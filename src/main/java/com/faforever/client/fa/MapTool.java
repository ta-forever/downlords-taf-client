package com.faforever.client.fa;

import com.faforever.client.map.MapService.PreviewType;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  static public List<Map<String,String>> toListOfDict(List<String[]> _mapDetails) {
    List<Map<String, String>> mapDetails = new ArrayList();
    for (String[] values : _mapDetails) {
      Map<String, String> keyvals = new HashMap();
      keyvals.put("name", values[MAP_DETAIL_COLUMN_NAME]);
      keyvals.put("archive", values[MAP_DETAIL_COLUMN_ARCHIVE]);
      keyvals.put("crc", values[MAP_DETAIL_COLUMN_CRC]);
      keyvals.put("description", values[MAP_DETAIL_COLUMN_DESCRIPTION]);
      keyvals.put("size", values[MAP_DETAIL_COLUMN_SIZE]);
      keyvals.put("players", values[MAP_DETAIL_COLUMN_NUM_PLAYERS]);
      keyvals.put("wind", values[MAP_DETAIL_COLUMN_WIND]);
      keyvals.put("tidal", values[MAP_DETAIL_COLUMN_TIDAL]);
      keyvals.put("gravity", values[MAP_DETAIL_COLUMN_GRAVITY]);
      mapDetails.add(keyvals);
    }
    return mapDetails;
  }

  static public String toJson(List<String[]> mapDetails) {
    return new Gson().toJson(toListOfDict(mapDetails));
  }

  static public List<String[]> listMapsInstalled(Path gamePath, Path previewCacheDirectory, boolean doCrc) {
    // and build feature cache in the previewCacheDirectory if not null
    return run(gamePath, null, null, doCrc, null, null, 0, previewCacheDirectory);
  }

  static public List<String[]> listMap(Path gamePath, String mapName) {
    return run(gamePath, null, mapName+"$", true, null, null, 0, null);
  }

  static public List<String[]> listMapsInArchive(Path hpiFile, Path previewCacheDirectory, boolean doCrc) {
    // and generate minimap images in previewCacheDirectory if not null
    return run(hpiFile.getParent(), hpiFile.getFileName().toString(), null, doCrc, previewCacheDirectory, PreviewType.MINI, 0, null);
  }

  public static void generatePreview(Path gamePath, String mapName, Path previewCacheDirectory, PreviewType previewType, int maxPositions) {
    run(gamePath, null, mapName + "$", false, previewCacheDirectory, previewType, maxPositions, previewCacheDirectory);
  }

  static private List<String[]> run(Path gamePath, String hpiSpecs, String mapName, boolean doCrc, Path previewCacheDirectory, PreviewType previewType, int maxPositions, Path featuresCacheDirectory) {
    String nativeDir = System.getProperty("nativeDir", "lib");
    Path exe = Paths.get(nativeDir).resolve("gpgnet4ta").resolve("maptool.exe");
    Path workingDirectory = exe.getParent();

    String QUOTED = "\"%s\"";
    List<String> command = new ArrayList<>();
    command.add(String.format(QUOTED, exe.toAbsolutePath()));
    command.add("--gamepath");
    command.add(String.format(QUOTED, gamePath));
    if (hpiSpecs != null) {
      command.add("--hpispecs");
      command.add(String.format(QUOTED, hpiSpecs));
    }
    if (mapName != null) {
      command.add("--mapname");
      command.add(String.format(QUOTED, mapName));
    }
    if (doCrc) {
      command.add("--hash");
    }
    if (previewCacheDirectory != null) {
      command.add("--thumb");
      command.add(String.format(QUOTED, previewCacheDirectory));
    }
    if (previewType != null) {
      command.add("--thumbtypes");
      command.add(previewType.toString().toLowerCase());
    }
    if (maxPositions > 0) {
      command.add("--maxpositions");
      command.add(String.valueOf(maxPositions));
    }
    if (featuresCacheDirectory != null) {
      command.add("--featurescachedir");
      command.add(String.format(QUOTED, featuresCacheDirectory));
    }

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.directory(workingDirectory.toFile());
    processBuilder.command(command);
    logger.info("Enumerating maps: {}", String.join(" ", processBuilder.command()));

    List<String[]> mapList = new ArrayList<>();

    boolean logEnable = false;
    String maptoolDataReceived = new String();

    try {
      final String UNIT_SEPARATOR = Character.toString((char)0x1f);
      Process process = processBuilder.start();
      BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = input.readLine()) != null) {
        maptoolDataReceived += line + "\n";
        String parts[] = line.split(UNIT_SEPARATOR);
        if (parts.length < 9) {
          logEnable = true;
        }
        mapList.add(parts);
      }
      if (logEnable) {
        logger.warn("Received too few fields from mapTool:\n{}\n{}", processBuilder.command(), maptoolDataReceived);
        while ((line = input.readLine()) != null) {
          logger.warn("but theres more:{}", line);
        }
      }
      input.close();
      process.waitFor();
    }
    catch (IOException | InterruptedException e )
    {
      logger.error("unable to process maps: {}", e.getMessage());
    }

    return mapList;
  }
}