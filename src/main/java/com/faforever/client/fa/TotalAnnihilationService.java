package com.faforever.client.fa;

import com.faforever.client.game.Faction;
import com.faforever.client.player.Player;
import com.faforever.client.preferences.PreferencesService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Knows how to starts/stop Total Annihilation with proper parameters. Downloading maps, mods and updates as well as
 * notifying the server about whether the preferences is running or not is <strong>not</strong> this service's
 * responsibility.
 */
@Lazy
@Service
@RequiredArgsConstructor
public class TotalAnnihilationService {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PreferencesService preferencesService;

  public Process startGameOffline(List<String> args) throws IOException {
    Path executable = getExecutable();
    List<String> launchCommand = defaultLaunchCommand().build();
    return launch(executable, launchCommand);
  }

  public Process startGame(int uid, @Nullable Faction faction, @Nullable List<String> additionalArgs,
                           RatingMode ratingMode, int gpgPort, int localReplayPort, boolean rehost, Player currentPlayer) throws IOException {

    Path executable = getExecutable();
    List<String> launchCommand = defaultLaunchCommand()
        .executable(executable)
        .uid(uid)
        .faction(faction)
        .clan(currentPlayer.getClan())
        .country(currentPlayer.getCountry())
        .deviation(100.0f)
        .mean(1500.0f)
        .username(currentPlayer.getUsername())
        .additionalArgs(additionalArgs)
        .logFile(preferencesService.getNewGameLogFile(uid))
        .localGpgPort(gpgPort)
        .localReplayPort(localReplayPort)
        .rehost(rehost)
        .build();

    return launch(executable, launchCommand);
  }


  public Process startReplay(Path path, @Nullable Integer replayId) throws IOException {
    Path executable = getExecutable();
    List<String> launchCommand = defaultLaunchCommand().build();
    return launch(executable, launchCommand);
  }


  public Process startReplay(URI replayUri, Integer replayId, Player currentPlayer) throws IOException {
    Path executable = getExecutable();
    List<String> launchCommand = defaultLaunchCommand().build();
    return launch(executable, launchCommand);
  }

  private Path getExecutable() {
    if (false) {
      return Paths.get("D:\\games\\pause.bat");
    }
    else {
      String nativeDir = System.getProperty("nativeDir", "lib");
      Path jdplay = Paths.get(nativeDir).resolve("gpgnet4ta").resolve("gpgnet4ta.exe");
      return jdplay;
    }
  }

  private LaunchCommandBuilder defaultLaunchCommand() {
    return LaunchCommandBuilder.create()
        .executableDecorator(preferencesService.getPreferences().getForgedAlliance().getExecutableDecorator());
  }

  @NotNull
  //private Process launch(Path executablePath) throws IOException {
  private Process launch(Path executablePath, List<String> launchCommand) throws IOException {
    Path executeDirectory = executablePath.getParent();
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.inheritIO();
    processBuilder.directory(executeDirectory.toFile());
    processBuilder.command(launchCommand);

    logger.info("Starting Total Annihilation with command: {}", String.join(" ", processBuilder.command()));
    processBuilder.command(launchCommand);
    Process process = processBuilder.start();

    return process;
  }
}
