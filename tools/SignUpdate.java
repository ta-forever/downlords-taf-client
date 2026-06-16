import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Offline Ed25519 signer for client/hotfix update artifacts. Produces the detached "&lt;file&gt;.sig"
 * sidecar that {@link com.faforever.client.update.UpdateSignatureVerifier} expects: base64 of the
 * raw 64-byte Ed25519 signature over the file's bytes.
 *
 * Use this instead of {@code openssl pkeyutl -sign -rawin} when the local openssl predates 1.1.1
 * (no {@code -rawin} support). Output is byte-identical to the openssl path.
 *
 * Usage (JDK 11+ single-file source launch):
 *   java tools/SignUpdate.java update-signing.key tdraw-full.zip
 * Writes tdraw-full.zip.sig next to the input and echoes the base64 signature.
 *
 * The key file is the Ed25519 private key in PKCS#8 PEM (the default output of
 * {@code openssl genpkey -algorithm ed25519 -out update-signing.key}); raw PKCS#8 DER also works.
 */
public class SignUpdate {

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("usage: java SignUpdate.java <ed25519-private-key.(pem|der)> <file-to-sign>");
      System.exit(2);
    }
    Path keyPath = Path.of(args[0]);
    Path file = Path.of(args[1]);

    byte[] der;
    String maybePem = Files.readString(keyPath);
    if (maybePem.contains("-----BEGIN")) {
      String body = maybePem.replaceAll("-----BEGIN [^-]+-----", "")
          .replaceAll("-----END [^-]+-----", "")
          .replaceAll("\\s", "");
      der = Base64.getDecoder().decode(body);
    } else {
      der = Files.readAllBytes(keyPath); // already DER
    }

    PrivateKey key = KeyFactory.getInstance("Ed25519")
        .generatePrivate(new PKCS8EncodedKeySpec(der));

    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(key);
    signer.update(Files.readAllBytes(file));
    String base64 = Base64.getEncoder().encodeToString(signer.sign());

    Path out = file.resolveSibling(file.getFileName() + ".sig");
    Files.writeString(out, base64);
    System.out.println("wrote " + out);
    System.out.println(base64);
  }
}
