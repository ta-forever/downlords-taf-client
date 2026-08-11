package com.faforever.client.replay;

import com.faforever.client.api.FafApiAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;

/**
 * Decrypts a local demo that the recorder left sealed because TotalA.exe died before it could
 * decrypt at game end.
 * <p>
 * Normal demos never come through here: a cleanly-finished game is decrypted in-process by the
 * recorder, so {@link #isEncrypted} is false and this is a no-op. Only the crash case needs the
 * server.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocalDemoSealService {

  /** Preamble v3. Must match TDemoEncPreamble in tadr.git src/dplayx/log2.pas. */
  private static final byte[] MAGIC = {'T', 'A', 'F', 'D', 'E', 'M', 'O', 1};
  private static final int SUPPORTED_VERSION = 3;
  private static final int PREAMBLE_SIZE = 326;
  private static final int OFF_VERSION = 8;
  private static final int OFF_NONCE = 12;
  private static final int OFF_DPID = 24;
  private static final int OFF_SEAL_LEN = 68;
  private static final int OFF_SEAL = 70;
  private static final int SEAL_SIZE = 256;
  private static final int NONCE_SIZE = 12;

  private final FafApiAccessor fafApiAccessor;

  /** Thrown when a sealed demo cannot be recovered; the message is already user-facing. */
  public static class DemoStillSealedException extends RuntimeException {
    public DemoStillSealedException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public boolean isEncrypted(Path demo) {
    try {
      if (Files.size(demo) < PREAMBLE_SIZE) {
        return false;
      }
      byte[] head = new byte[MAGIC.length];
      try (InputStream in = Files.newInputStream(demo)) {
        if (in.read(head) != head.length) {
          return false;
        }
      }
      return Arrays.equals(head, MAGIC);
    } catch (IOException e) {
      log.warn("could not inspect demo {}", demo, e);
      return false;
    }
  }

  /**
   * Returns the demo path, decrypting first if it is sealed. Safe to call on any file: a demo that
   * is not sealed is returned untouched.
   *
   * @throws DemoStillSealedException if the key could not be obtained or applied. The demo is left
   * exactly as it was, so a later retry (e.g. once the game has ended) still works.
   */
  public Path ensureDecrypted(Path demo) {
    if (!isEncrypted(demo)) {
      return demo;
    }

    log.info("demo {} is sealed; asking the server for the key", demo);
    byte[] preamble = new byte[PREAMBLE_SIZE];
    try (InputStream in = Files.newInputStream(demo)) {
      if (in.readNBytes(preamble, 0, PREAMBLE_SIZE) != PREAMBLE_SIZE) {
        throw new IOException("truncated preamble");
      }
    } catch (IOException e) {
      throw new DemoStillSealedException("could not read the demo header", e);
    }

    ByteBuffer buf = ByteBuffer.wrap(preamble).order(ByteOrder.LITTLE_ENDIAN);
    int version = Short.toUnsignedInt(buf.getShort(OFF_VERSION));
    if (version != SUPPORTED_VERSION) {
      throw new DemoStillSealedException(
        "this demo uses encrypted-demo format version " + version + ", which this client cannot read",
        null);
    }
    int sealLen = Short.toUnsignedInt(buf.getShort(OFF_SEAL_LEN));
    if (sealLen != SEAL_SIZE) {
      throw new DemoStillSealedException("the demo carries no recoverable key", null);
    }

    byte[] nonce = Arrays.copyOfRange(preamble, OFF_NONCE, OFF_NONCE + NONCE_SIZE);
    int dpid = buf.getInt(OFF_DPID);
    String dpidHex = String.format("%08x", dpid);
    byte[] seal = Arrays.copyOfRange(preamble, OFF_SEAL, OFF_SEAL + SEAL_SIZE);

    byte[] key;
    try {
      String keyBase64 = fafApiAccessor.unsealDemo(Base64.getEncoder().encodeToString(seal), dpidHex);
      key = Base64.getDecoder().decode(keyBase64);
    } catch (Exception e) {
      // Includes the server's "still in progress" / "too soon" responses, which are the expected
      // answers rather than faults - the demo stays sealed and can be retried later.
      throw new DemoStillSealedException(e.getMessage(), e);
    }

    Path temp = demo.resolveSibling(demo.getFileName() + ".decrypting");
    try {
      decryptBody(demo, temp, key, nonce);
      // Replace only once the whole file decrypted, so an interrupted attempt cannot destroy the
      // only copy of the demo.
      Files.move(temp, demo, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
        // best effort
      }
      throw new DemoStillSealedException("the demo could not be decrypted", e);
    }

    log.info("decrypted demo {} (dpid {})", demo, dpidHex);
    return demo;
  }

  /**
   * Copies the body out from {@link #PREAMBLE_SIZE} onwards, decrypting as it goes, so the result
   * is the ordinary TA demo it would have been. Keystream position is BODY-relative, matching
   * TLog2.DecryptDemoFile.
   */
  private void decryptBody(Path source, Path target, byte[] key, byte[] nonce) throws IOException {
    byte[] chunk = new byte[64 * 1024];
    long bodyOffset = 0;
    try (InputStream in = Files.newInputStream(source);
         OutputStream out = Files.newOutputStream(target)) {
      if (in.skip(PREAMBLE_SIZE) != PREAMBLE_SIZE) {
        throw new IOException("could not skip the demo header");
      }
      int read;
      while ((read = in.read(chunk)) > 0) {
        chaCha20Apply(key, nonce, bodyOffset, chunk, read);
        out.write(chunk, 0, read);
        bodyOffset += read;
      }
    }
  }

  // ---------------------------------------------------------------- ChaCha20 (RFC 8439)

  private static int rotl(int v, int n) {
    return (v << n) | (v >>> (32 - n));
  }

  private static void quarterRound(int[] x, int a, int b, int c, int d) {
    x[a] += x[b]; x[d] = rotl(x[d] ^ x[a], 16);
    x[c] += x[d]; x[b] = rotl(x[b] ^ x[c], 12);
    x[a] += x[b]; x[d] = rotl(x[d] ^ x[a], 8);
    x[c] += x[d]; x[b] = rotl(x[b] ^ x[c], 7);
  }

  private static void block(byte[] key, byte[] nonce, int counter, byte[] out) {
    int[] state = new int[16];
    state[0] = 0x61707865; state[1] = 0x3320646e; state[2] = 0x79622d32; state[3] = 0x6b206574;
    ByteBuffer k = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 8; i++) {
      state[4 + i] = k.getInt(i * 4);
    }
    state[12] = counter;
    ByteBuffer n = ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 3; i++) {
      state[13 + i] = n.getInt(i * 4);
    }

    int[] x = state.clone();
    for (int i = 0; i < 10; i++) {
      quarterRound(x, 0, 4, 8, 12);  quarterRound(x, 1, 5, 9, 13);
      quarterRound(x, 2, 6, 10, 14); quarterRound(x, 3, 7, 11, 15);
      quarterRound(x, 0, 5, 10, 15); quarterRound(x, 1, 6, 11, 12);
      quarterRound(x, 2, 7, 8, 13);  quarterRound(x, 3, 4, 9, 14);
    }
    ByteBuffer o = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 16; i++) {
      o.putInt(i * 4, x[i] + state[i]);
    }
  }

  /** XORs {@code length} bytes of {@code data} with the keystream starting at {@code streamOffset}. */
  static void chaCha20Apply(byte[] key, byte[] nonce, long streamOffset, byte[] data, int length) {
    byte[] ks = new byte[64];
    int counter = (int) (streamOffset / 64);
    int idx = (int) (streamOffset % 64);
    int pos = 0;
    while (pos < length) {
      block(key, nonce, counter, ks);
      int n = Math.min(64 - idx, length - pos);
      for (int i = 0; i < n; i++) {
        data[pos + i] ^= ks[idx + i];
      }
      pos += n;
      idx = 0;
      counter++;
    }
  }
}
