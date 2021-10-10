package com.faforever.client.fa;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DemoFile {

  @SneakyThrows
  static public DemoFileInfo sneakyGetInfo(String replayPath) {
    return getInfo(replayPath);
  }

  static public DemoFileInfo getInfo(String replayPath) throws IOException {
    Path nativeDir = Path.of(System.getProperty("nativeDir", "lib"));
    Path exePath = nativeDir.resolve("bin").resolve("replayer.exe");

    List<String> command = new ArrayList<>();
    if (org.bridj.Platform.isLinux()) {
      command.add("wine");
    }

    command.addAll(List.of(
        exePath.toAbsolutePath().toString(),
        "--demourl", replayPath,
        "--info"));

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.directory(nativeDir.toFile());
    processBuilder.command(command);
    log.info("{}", processBuilder.command());

    Process process = processBuilder.start();
    BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));

    String allLines = new String();
    String line;
    while ((line = input.readLine()) != null) {
      allLines += line + "\n";
    }
    input.close();

    try {
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(String.format("replayer exited with error code %d", exitCode));
      }
    } catch (InterruptedException e) {
      log.error("replayer process interrupted: {}", e.getMessage());
    }

    return new Gson().fromJson(allLines, DemoFileInfo.class);
  }
}
