package com.faforever.client.update;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Verifies Ed25519 detached signatures over downloaded executables/archives before they are run
 * or extracted. The trust root is the set of {@link #pinnedPublicKeysBase64} compiled into the
 * client binary, so verification is independent of the server, CDN and TLS path that delivered
 * the artifact. A compromise of {@code content.taforever.com} (or its CDN, or the TLS path) can
 * then at worst <em>deny</em> updates — it cannot make the client run code that was not signed by
 * the offline release key.
 *
 * <p>Signature wire format: the detached {@code ".sig"} sidecar contains base64 of the raw 64-byte
 * Ed25519 signature — the output of {@code openssl pkeyutl -sign -rawin ... | base64 -w0}, which is
 * byte-identical to what {@code Signature.getInstance("Ed25519").sign()} produces. Surrounding
 * whitespace/newlines are tolerated.
 *
 * <p>Public-key wire format: base64 of the X.509 SubjectPublicKeyInfo (SPKI) DER, i.e. the output
 * of {@code openssl pkey -pubout -outform DER | base64 -w0}.
 */
@Slf4j
public final class UpdateSignatureVerifier {

  private static final String ED25519 = "Ed25519";

  /**
   * Pinned Ed25519 public keys (SPKI DER, base64). More than one entry enables key rotation: ship
   * the next key alongside the current one for a release or two before retiring the old one, so
   * in-field clients accept artifacts signed by either. NEVER remove a key until no supported
   * client still pins only it.
   *
   * <p>Mutable + package-private only so tests can swap in a throwaway key; production code never
   * reassigns it.
   */
  @VisibleForTesting
  static List<String> pinnedPublicKeysBase64 = List.of(
      // TODO replace with the real release public key:
      //   openssl pkey -in update-signing.key -pubout -outform DER | base64 -w0
      "MCowBQYDK2VwAyEAmWYqfprf2+Nb4sj+/ILicayzfKKr9chS/QZKpC/mYKA="
  );

  private UpdateSignatureVerifier() {
  }

  /**
   * Reads the base64 {@code ".sig"} sidecar and verifies it over {@code file}. Fails closed: throws
   * if the sidecar is missing, malformed, or the signature matches none of the pinned keys.
   */
  public static void verify(Path file, Path signatureFile) throws IOException, SecurityException {
    if (!Files.isRegularFile(signatureFile)) {
      throw new SecurityException("missing update signature: " + signatureFile);
    }
    byte[] signature;
    try {
      signature = Base64.getDecoder().decode(Files.readString(signatureFile, StandardCharsets.UTF_8).trim());
    } catch (IllegalArgumentException e) {
      throw new SecurityException("malformed update signature: " + signatureFile, e);
    }
    verify(file, signature);
  }

  /** Verifies a raw Ed25519 signature over {@code file} against the pinned keys. Fails closed. */
  public static void verify(Path file, byte[] signature) throws IOException, SecurityException {
    if (!verify(file, signature, pinnedPublicKeysBase64)) {
      throw new SecurityException("update signature does not match any pinned key: " + file);
    }
  }

  @VisibleForTesting
  static boolean verify(Path file, byte[] signature, List<String> pinnedKeys) throws IOException {
    for (String pinned : pinnedKeys) {
      if (verifyWithKey(file, signature, pinned)) {
        return true;
      }
    }
    return false;
  }

  private static boolean verifyWithKey(Path file, byte[] signature, String pinnedBase64) throws IOException {
    try {
      PublicKey publicKey = KeyFactory.getInstance(ED25519)
          .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pinnedBase64.trim())));
      Signature verifier = Signature.getInstance(ED25519);
      verifier.initVerify(publicKey);
      byte[] buffer = new byte[64 * 1024];
      try (InputStream in = Files.newInputStream(file)) {
        int n;
        while ((n = in.read(buffer)) > 0) {
          verifier.update(buffer, 0, n);
        }
      }
      return verifier.verify(signature);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      log.warn("Update signature could not be checked against a pinned key", e);
      return false;
    }
  }
}
