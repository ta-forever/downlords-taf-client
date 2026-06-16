package com.faforever.client.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mutates the static pinned-key list, so force same-thread to avoid racing parallel suites.
 */
@Execution(ExecutionMode.SAME_THREAD)
class UpdateSignatureVerifierTest {

  private final List<String> originalKeys = UpdateSignatureVerifier.pinnedPublicKeysBase64;

  @AfterEach
  void restoreKeys() {
    UpdateSignatureVerifier.pinnedPublicKeysBase64 = originalKeys;
  }

  @Test
  void validSignaturePasses(@TempDir Path dir) throws Exception {
    KeyPair kp = generateKeyPair();
    pin(kp);
    Path artifact = write(dir, "installer.exe", "the real installer bytes");
    Path sig = writeSig(artifact, sign(kp, artifact));

    assertDoesNotThrow(() -> UpdateSignatureVerifier.verify(artifact, sig));
  }

  @Test
  void rotationKeyAlsoAccepted(@TempDir Path dir) throws Exception {
    KeyPair current = generateKeyPair();
    KeyPair next = generateKeyPair();
    UpdateSignatureVerifier.pinnedPublicKeysBase64 = List.of(spki(current), spki(next));

    Path artifact = write(dir, "installer.exe", "bytes");
    Path sig = writeSig(artifact, sign(next, artifact)); // signed by the not-yet-primary key

    assertDoesNotThrow(() -> UpdateSignatureVerifier.verify(artifact, sig));
  }

  @Test
  void tamperedArtifactFails(@TempDir Path dir) throws Exception {
    KeyPair kp = generateKeyPair();
    pin(kp);
    Path artifact = write(dir, "installer.exe", "the real installer bytes");
    Path sig = writeSig(artifact, sign(kp, artifact));

    Files.write(artifact, "MALICIOUS payload swapped in".getBytes()); // swap bytes after signing

    assertThrows(SecurityException.class, () -> UpdateSignatureVerifier.verify(artifact, sig));
  }

  @Test
  void wrongKeyFails(@TempDir Path dir) throws Exception {
    KeyPair legitimate = generateKeyPair();
    KeyPair attacker = generateKeyPair();
    pin(legitimate); // client pins the real key...
    Path artifact = write(dir, "installer.exe", "bytes");
    Path sig = writeSig(artifact, sign(attacker, artifact)); // ...but artifact signed by attacker

    assertThrows(SecurityException.class, () -> UpdateSignatureVerifier.verify(artifact, sig));
  }

  @Test
  void missingSignatureFails(@TempDir Path dir) throws Exception {
    Path artifact = write(dir, "installer.exe", "bytes");
    assertThrows(SecurityException.class,
        () -> UpdateSignatureVerifier.verify(artifact, dir.resolve("absent.sig")));
  }

  @Test
  void malformedSignatureFails(@TempDir Path dir) throws Exception {
    pin(generateKeyPair());
    Path artifact = write(dir, "installer.exe", "bytes");
    Path sig = dir.resolve("installer.exe.sig");
    Files.writeString(sig, "not valid base64 ***");

    assertThrows(SecurityException.class, () -> UpdateSignatureVerifier.verify(artifact, sig));
  }

  private static KeyPair generateKeyPair() throws Exception {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  private static void pin(KeyPair kp) {
    UpdateSignatureVerifier.pinnedPublicKeysBase64 = List.of(spki(kp));
  }

  private static String spki(KeyPair kp) {
    return Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
  }

  private static byte[] sign(KeyPair kp, Path file) throws Exception {
    Signature s = Signature.getInstance("Ed25519");
    s.initSign(kp.getPrivate());
    s.update(Files.readAllBytes(file));
    return s.sign();
  }

  private static Path write(Path dir, String name, String content) throws Exception {
    Path p = dir.resolve(name);
    Files.write(p, content.getBytes());
    return p;
  }

  private static Path writeSig(Path artifact, byte[] signature) throws Exception {
    Path sig = artifact.resolveSibling(artifact.getFileName() + ".sig");
    Files.writeString(sig, Base64.getEncoder().encodeToString(signature));
    return sig;
  }
}
