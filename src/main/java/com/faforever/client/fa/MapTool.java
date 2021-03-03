package com.faforever.client.fa;

import com.faforever.client.map.MapService.PreviewType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MapTool {

  public static final Integer MAP_DETAIL_COLUMN_NAME = 0;
  public static final Integer MAP_DETAIL_COLUMN_ARCHIVE = 1;
  public static final Integer MAP_DETAIL_COLUMN_CRC = 2;
  public static final Integer MAP_DETAIL_COLUMN_DESCRIPTION = 3;
  public static final Integer MAP_DETAIL_COLUMN_SIZE = 4;
  public static final Integer MAP_DETAIL_COLUMN_NUM_PLAYERS = 5;
  public static final Integer MAP_DETAIL_COLUMN_WIND = 6;
  public static final Integer MAP_DETAIL_COLUMN_TIDAL = 7;
  public static final Integer MAP_DETAIL_COLUMN_GRAVITY = 8;

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  static public List<String[]> listMaps(Path gamePath, String hpiArchiveSpec, String mapNameSpec, Path mapCacheDirectory, boolean doCrc) {
    String nativeDir = System.getProperty("nativeDir", "lib");
    Path exe = Paths.get(nativeDir).resolve("gpgnet4ta").resolve("maptool.exe");
    Path workingDirectory = exe.getParent();

    String QUOTED = "\"%s\"";
    List<String> command = new ArrayList<>();
    command.add(String.format(QUOTED, exe.toAbsolutePath()));
    command.add("--gamepath");
    command.add(String.format(QUOTED, gamePath));
    if (hpiArchiveSpec != null) {
      command.add("--hpispecs");
      command.add(String.format(QUOTED, hpiArchiveSpec));
    }
    if (mapCacheDirectory != null) {
      command.add("--featurescachedir");
      command.add(String.format(QUOTED, mapCacheDirectory));
    }
    if (mapNameSpec != null) {
      command.add("--mapname");
      command.add(String.format(QUOTED, mapNameSpec));
    }
    if (doCrc) {
      command.add("--hash");
    }

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.directory(workingDirectory.toFile());
    processBuilder.command(command);
    logger.info("Enumerating maps: {}", String.join(" ", processBuilder.command()));

    List<String[]> mapList = new ArrayList<>();
    try {
      final String UNIT_SEPARATOR = Character.toString((char)0x1f);
      Process process = processBuilder.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        mapList.add(line.split(UNIT_SEPARATOR));
      }
      input.close();
    }
    catch (IOException e)
    {
      logger.error("unable to enumerate maps: {}", e.getMessage());
    }

    return mapList;
  }

  public static void generatePreview(Path gamePath, String mapName, Path mapCacheDirectory, PreviewType previewType, int maxPositions) {
    String nativeDir = System.getProperty("nativeDir", "lib");
    Path exe = Paths.get(nativeDir).resolve("gpgnet4ta").resolve("maptool.exe");
    Path workingDirectory = exe.getParent();

    String QUOTED = "\"%s\"";
    List<String> command = new ArrayList<>();
    command.add(String.format(QUOTED, exe.toAbsolutePath()));
    command.add("--gamepath");
    command.add(String.format(QUOTED, gamePath));
    command.add("--mapname");
    command.add(String.format(QUOTED, mapName + "$"));
    command.add("--thumb");
    command.add(String.format(QUOTED, mapCacheDirectory));
    command.add("--featurescachedir");
    command.add(String.format(QUOTED, mapCacheDirectory));
    command.add("--thumbtypes");
    command.add(previewType.toString().toLowerCase());
    command.add("--maxpositions");
    command.add(String.valueOf(maxPositions));

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.directory(workingDirectory.toFile());
    processBuilder.command(command);
    logger.info("Generating map preview: {}", String.join(" ", processBuilder.command()));
    try {
      Process process = processBuilder.start();
      process.waitFor();
    }
    catch (IOException e)
    {
      logger.error("unable to generate preview: {}", e.getMessage());
    }
    catch (InterruptedException e)
    {
      logger.error("unable to generate preview: {}", e.getMessage());
    }
  }

}
