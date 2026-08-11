package com.faforever.client.replay;

import com.faforever.client.api.FafApiAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Decrypts a demo actually produced by the Delphi recorder.
 * <p>
 * {@code src/test/resources/demoseal/sealed_demo.bin} was written by tadr.git's
 * {@code src/dplayx/tests/sealtest.dpr}, which deliberately leaks its writer so the file is left
 * sealed exactly as a TotalA.exe crash would leave it. The expected key was recovered separately
 * with the private key. So this checks the Java implementation against a real artifact of a
 * different implementation, rather than against itself — which is the only way the ChaCha20
 * port, the 326-byte preamble layout and the body-relative keystream offset all get verified.
 */
@ExtendWith(MockitoExtension.class)
public class LocalDemoSealServiceTest {

  /** dpid the fixture is sealed to, and the key the server would return for it. */
  private static final String EXPECTED_DPID = "03bf990e";
  private static final String KEY_BASE64 = "HfO2JD5Sslvkx+B/mEoKdlXVLw5zPJGEHa/iVSV1blE=";

  @Mock
  private FafApiAccessor fafApiAccessor;

  private LocalDemoSealService service;
  private Path sealed;
  private byte[] expectedPlain;

  @BeforeEach
  void setUp() throws Exception {
    service = new LocalDemoSealService(fafApiAccessor);

    Path fixture = Path.of(getClass().getResource("/demoseal/sealed_demo.bin").toURI());
    expectedPlain = Files.readAllBytes(
      Path.of(getClass().getResource("/demoseal/expected_plain.bin").toURI()));

    // Work on a copy: ensureDecrypted replaces the file in place.
    sealed = Files.createTempFile("sealed", ".tad");
    Files.copy(fixture, sealed, StandardCopyOption.REPLACE_EXISTING);
  }

  @Test
  void detectsASealedDemo() {
    assertTrue(service.isEncrypted(sealed));
  }

  @Test
  void aPlainDemoIsNotDetectedAsSealed() throws Exception {
    Path plain = Files.createTempFile("plain", ".tad");
    Files.write(plain, expectedPlain);
    assertFalse(service.isEncrypted(plain));
  }

  @Test
  void decryptsADemoWrittenByTheRecorder() throws Exception {
    when(fafApiAccessor.unsealDemo(any(), eq(EXPECTED_DPID))).thenReturn(KEY_BASE64);

    Path result = service.ensureDecrypted(sealed);

    assertEquals(sealed, result);
    byte[] decrypted = Files.readAllBytes(sealed);
    assertArrayEquals(expectedPlain, decrypted,
      "decrypted demo must match what the recorder wrote, byte for byte");
    assertFalse(service.isEncrypted(sealed), "the preamble must be stripped");
  }

  @Test
  void aDemoThatIsNotSealedPassesStraightThrough() throws Exception {
    Path plain = Files.createTempFile("plain", ".tad");
    Files.write(plain, expectedPlain);

    assertEquals(plain, service.ensureDecrypted(plain));
    assertArrayEquals(expectedPlain, Files.readAllBytes(plain));
  }

  /** A refusal must leave the demo intact so it can be retried once the game has ended. */
  @Test
  void aRefusedUnsealLeavesTheDemoUntouched() throws Exception {
    byte[] before = Files.readAllBytes(sealed);
    when(fafApiAccessor.unsealDemo(any(), any()))
      .thenThrow(new RuntimeException("a game using that DirectPlay id is still in progress"));

    assertThrows(LocalDemoSealService.DemoStillSealedException.class,
      () -> service.ensureDecrypted(sealed));

    assertArrayEquals(before, Files.readAllBytes(sealed));
    assertTrue(service.isEncrypted(sealed));
  }
}
