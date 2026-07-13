package com.faforever.client.fa;

import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.map.MapService.PreviewOverlayType;

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
      keyvals.put("name", valueAt(values, MAP_DETAIL_COLUMN_NAME));
      keyvals.put("archive", valueAt(values, MAP_DETAIL_COLUMN_ARCHIVE));
      keyvals.put("crc", valueAt(values, MAP_DETAIL_COLUMN_CRC));
      keyvals.put("description", valueAt(values, MAP_DETAIL_COLUMN_DESCRIPTION));
      keyvals.put("size", valueAt(values, MAP_DETAIL_COLUMN_SIZE));
      keyvals.put("players", valueAt(values, MAP_DETAIL_COLUMN_NUM_PLAYERS));
      keyvals.put("wind", valueAt(values, MAP_DETAIL_COLUMN_WIND));
      keyvals.put("tidal", valueAt(values, MAP_DETAIL_COLUMN_TIDAL));
      keyvals.put("gravity", valueAt(values, MAP_DETAIL_COLUMN_GRAVITY));
      mapDetails.add(keyvals);
    }
    return mapDetails;
  }

  private static String valueAt(String[] values, int index) {
    return values.length > index ? values[index] : "";
  }

  static public String toJson(List<String[]> mapDetails) {
    return new Gson().toJson(toListOfDict(mapDetails));
  }

  static public List<String[]> listMapsInstalled(Path gamePath, Path previewCacheDirectory, boolean doCrc) throws IOException {
    // and build feature cache in the previewCacheDirectory if not null
    return run(gamePath, null, null, doCrc, null, null, 0, previewCacheDirectory);
  }

  static public List<String[]> listMap(Path gamePath, String mapName) throws IOException {
    return run(gamePath, null, mapName+"$", true, null, null, 0, null);
  }

  static public List<String[]> listMapsInArchive(Path hpiFile, Path previewCacheDirectory, boolean doCrc) throws IOException {
    // and generate minimap images in previewCacheDirectory if not null
    return run(hpiFile.getParent(), hpiFile.getFileName().toString(), null, doCrc, previewCacheDirectory, PreviewType.MINI.getToolName(), 0, null);
  }

  public static void generatePreview(Path gamePath, String modGp3FileName, String mapName, Path previewCacheDirectory, PreviewType previewType, int maxPositions) throws IOException {
    generatePreview(gamePath, modGp3FileName, mapName, previewCacheDirectory, previewType.getToolName(), maxPositions);
  }

  public static void generateOverlayPreview(Path gamePath, String modGp3FileName, String mapName, Path previewCacheDirectory, PreviewOverlayType previewOverlayType, int maxPositions) throws IOException {
    generatePreview(gamePath, modGp3FileName, mapName, previewCacheDirectory, previewOverlayType.getToolName(), maxPositions);
  }

  /** Generate the start-position coordinate side-car (a text file the client reads to draw its
   *  own position markers). Written to {@code <previewCacheDirectory>/positions-coords_<max>/<map>.txt}. */
  public static void generateStartPositions(Path gamePath, String modGp3FileName, String mapName, Path previewCacheDirectory, int maxPositions) throws IOException {
    generatePreview(gamePath, modGp3FileName, mapName, previewCacheDirectory, "positions-coords", maxPositions);
  }

  private static void generatePreview(Path gamePath, String modGp3FileName, String mapName, Path previewCacheDirectory, String previewToolName, int maxPositions) throws IOException {
    String hpiSpecs = "*.hpi;*.gpf;*.ccx;rev31.gp3;*.ufo";
    if (modGp3FileName != null) {
      hpiSpecs = "*.hpi;*.gpf;*.ccx;" + modGp3FileName + ";*.ufo";
    }
    run(gamePath, hpiSpecs, mapName + "$", false, previewCacheDirectory, previewToolName, maxPositions, previewCacheDirectory);
  }

  static private List<String[]> run(Path gamePath, String hpiSpecs, String mapName, boolean doCrc, Path previewCacheDirectory, String previewToolName, int maxPositions, Path featuresCacheDirectory) throws IOException {
    String nativeDir = System.getProperty("nativeDir", "lib");
    Path exe = Paths.get(nativeDir).resolve("bin").resolve(
        org.bridj.Platform.isLinux() ? "maptool" : "maptool.exe"
    );
    Path workingDirectory = exe.getParent();

    List<String> command = new ArrayList<>();
    command.add(exe.toAbsolutePath().toString());
    command.add("--gamepath");
    command.add(gamePath.toString());

    if (hpiSpecs != null) {
      command.add("--hpispecs");
      // maptool uses Qt's QDir, which uses wildcardToRegularExpression for globbing: https://doc.qt.io/qt-5/qregularexpression.html#wildcardToRegularExpression
      // so we need to replace "[" with "[[]" and "]" with "[]]"
      hpiSpecs = hpiSpecs.replace("[", "\\[").replace("]", "\\]");
      hpiSpecs = hpiSpecs.replace("\\[", "[[]").replace("\\]", "[]]");
      command.add(hpiSpecs);
    }
    if (mapName != null) {
      command.add("--mapname");
      command.add(mapName);
    }
    if (doCrc) {
      command.add("--hash");
    }
    if (previewCacheDirectory != null) {
      command.add("--thumb");
      command.add(previewCacheDirectory.toString());
    }
    if (previewToolName != null) {
      command.add("--thumbtypes");
      command.add(previewToolName);
    }
    if (maxPositions > 0) {
      command.add("--maxpositions");
      command.add(String.valueOf(maxPositions));
    }
    if (featuresCacheDirectory != null) {
      command.add("--featurescachedir");
      command.add(featuresCacheDirectory.toString());
    }

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.directory(workingDirectory.toFile());
    processBuilder.command(command);
    logger.info("{}", processBuilder.command());

    List<String[]> mapList = new ArrayList<>();

    boolean logEnable = false;
    String maptoolDataReceived = new String();

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

    BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    while ((line = err.readLine()) != null) {
      logger.error(line);
    }
    err.close();

    try {
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException(String.format("Map tool exited with error code %d", exitCode));
      }
    } catch (InterruptedException e) {
      logger.error("maptool process interrupted: {}", e.getMessage());
    }

    return mapList;
  }
}
