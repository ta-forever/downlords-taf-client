package com.faforever.client.mod;

import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.faforever.client.task.CompletableTask.Priority.HIGH;
import static com.faforever.client.util.LinkOrCopy.hardLinkOrCopy;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InstallFeaturedModTask extends CompletableTask<Path> {

  static String TA_EXECUTABLE_FILE = "TotalA.exe";

  static String[] REQUIRED_BASE_FILES = {
      "TotalA.exe",
      "smackw32.dll",
      "totala1.hpi",
      "totala2.hpi"
  };

  static String[] OPTIONAL_BASE_FILES = {
      "totala3.hpi",
      "totala4.hpi",
      "ccdata.ccx",
      "ccmaps.ccx",
      "btdata.ccx",
      "btmaps.ccx",
      "cdmaps.ccx"
  };

  /*
   * Does the base installation with the expectation that versions can be switched afterwards using GameUpdater
   */

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final PlatformService platformService;
  private final PreferencesService preferencesService;
  private final NotificationService notificationService;
  private final DownloadService downloadService;
  private final I18n i18n;
  private final List<String> installPackagePathOrUrls;

  private Boolean okOverwriteTarget;
  private FeaturedMod featuredMod;
  private FeaturedModInstallSpecs featuredModInstallSpecs;
  private Path referenceTaPath;     // where to get OTA game data from
  private Path targetPath;          // where to install to

  public void setOkOverwriteTarget(Boolean okOverwriteTarget) {
    this.okOverwriteTarget = okOverwriteTarget;
  }
  public void setFeaturedMod(FeaturedMod featuredMod) {
    this.featuredMod = featuredMod;
  }
  public void setFeaturedModInstallSpecs(FeaturedModInstallSpecs featuredModInstallSpecs) {
    this.featuredModInstallSpecs = featuredModInstallSpecs;
  }
  public void setReferenceTaPath(Path referenceTaPath) {
    this.referenceTaPath = referenceTaPath;
  }
  public void setTargetPath(Path targetPath) {
    this.targetPath = targetPath;
  }
  public void addInstallPackagePathOrUrl(String installPackagePathOrUrl) {
    this.installPackagePathOrUrls.add(installPackagePathOrUrl);
  }


  @Inject
  public InstallFeaturedModTask(PlatformService platformService, PreferencesService preferencesService,
                                NotificationService notificationService, DownloadService downloadService, I18n i18n) {
    super(HIGH);
    this.platformService = platformService;
    this.preferencesService = preferencesService;
    this.notificationService = notificationService;
    this.downloadService = downloadService;
    this.i18n = i18n;
    this.okOverwriteTarget = false;
    this.installPackagePathOrUrls = new ArrayList<>();
  }

  @Override
  protected Path call() throws Exception {
    validate();

    targetPath.toFile().mkdirs();
    this.updateTitle("Copying base files ...");
    copyOriginalTaFiles();

    int n = 0;
    for (String url: installPackagePathOrUrls) {
      installIteration(url, ++n, installPackagePathOrUrls.size());
    }

    if (Files.exists(targetPath.resolve(TA_EXECUTABLE_FILE))) {
      try {
        Files.setPosixFilePermissions(targetPath.resolve(TA_EXECUTABLE_FILE), PosixFilePermissions.fromString("rwxr-xr-x"));
      }
      catch (UnsupportedOperationException ignored)
      { }
    }

    return targetPath;
  }

  protected void installIteration(String installPackagePathOrUrl, int iterationNumber, int iterationsTotal) throws Exception {

    Path downloadTarget = null;
    long downloadSize = 0;
    try {
      if (Files.exists(Path.of(installPackagePathOrUrl))) {
        downloadTarget = Path.of(installPackagePathOrUrl);
        downloadSize = Files.size(downloadTarget);
      }
    }
    catch (InvalidPathException ignored) { }

    if (downloadTarget == null) {
      URL downloadUrl = new URL(installPackagePathOrUrl);
      URLConnection conn = downloadUrl.openConnection();
      String downloadFilename = getDownloadFilename(conn, downloadUrl);
      downloadTarget = preferencesService.getFeaturedModCachePath().resolve(downloadFilename);
      downloadSize = conn.getContentLengthLong();
    }
    logger.info("downloadTarget={}, downloadSize={}", downloadTarget, downloadSize);

    downloadTarget.getParent().toFile().mkdirs();
    if (!Files.exists(downloadTarget) || downloadSize>0 && Files.size(downloadTarget) != downloadSize) {
      logger.info("Downloading {} installer to {}", featuredMod.getDisplayName(), downloadTarget);
      this.updateTitle(String.format("Downloading %s install package (%d/%d) ...",
          featuredMod.getDisplayName(), iterationNumber, iterationsTotal));
      downloadService.downloadFile(new URL(installPackagePathOrUrl), downloadTarget, this::updateProgress);
    }

    this.updateTitle(String.format("Extracting %s install package (%d/%d) ...",
        featuredMod.getDisplayName(), iterationNumber, iterationsTotal));
    if (featuredModInstallSpecs.getFolders() == null) {
      extractFeaturedModFiles(downloadTarget, "(.*)");
    }
    else {
      for (FeaturedModInstallFolders folders : featuredModInstallSpecs.getFolders()) {
        if (folders.getPlatforms() == null || folders.getPlatforms().isEmpty() ||
            (folders.getPlatforms().contains("win7") && org.bridj.Platform.isWindows7()) ||
            (folders.getPlatforms().contains("win8") && org.bridj.Platform.isWindows()) ||
            (folders.getPlatforms().contains("win10") && org.bridj.Platform.isWindows()) ||
            (folders.getPlatforms().contains("linux") && org.bridj.Platform.isLinux()) ||
            (folders.getPlatforms().contains("mac") && org.bridj.Platform.isMacOSX())) {
          extractFeaturedModFiles(downloadTarget, folders.getRegex());
        }
      }
    }
  }

  private String getDownloadFilename(URLConnection conn, URL url) throws IOException {
    String filename = Path.of(url.getPath()).getFileName().toString();
    if (url.getQuery() != null && !url.getQuery().isEmpty()) {
      String disposition = conn.getHeaderField("Content-disposition");
      if (disposition != null) {
        for (String arg : disposition.split(";")) {
          if (arg.trim().startsWith("filename=")) {
            filename = arg.split("=")[1].replace("\"", "");
            break;
          }
        }
      }
    }
    return filename;
  }

  private void validate() {
    if (this.installPackagePathOrUrls.isEmpty() || this.installPackagePathOrUrls.get(0).isEmpty()) {
      throw new FeaturedModInstallException(i18n.get("installFeaturedMod.error.emptyInstallPackageUrl"));
    }

    if (referenceTaPath.toString().isEmpty()) {
      throw new FeaturedModInstallException(i18n.get("installFeaturedMod.error.emptyReferencePath"));
    }

    if (targetPath.toString().isEmpty()) {
      throw new FeaturedModInstallException(i18n.get("installFeaturedMod.error.emptyTargetPath"));
    }

    if (!Files.exists(referenceTaPath)) {
      throw new FeaturedModInstallException(i18n.get("installFeaturedMod.error.referenceTaNotExists", referenceTaPath));
    }

    if (!okOverwriteTarget && Files.exists(targetPath) &&
        (!Files.isDirectory(targetPath) || targetPath.toFile().list().length > 0)) {
      throw new FeaturedModInstallException(i18n.get("installFeaturedMod.error.notEmptyFolder", targetPath));
    }

    for (String fileName: REQUIRED_BASE_FILES) {
      Path from = referenceTaPath.resolve(fileName);
      if (!Files.exists(from)) {
        throw new FeaturedModInstallException(i18n.get("installFeaturedMod.error.requiredFileMissing", from));
      }
    }
  }

  private void copyOriginalTaFiles() throws IOException {
    for (String [] base_files: new String[][]{REQUIRED_BASE_FILES, OPTIONAL_BASE_FILES}) {
      for (String fileName : base_files) {
        Path from = referenceTaPath.resolve(fileName);
        Path to = targetPath.resolve(fileName);
        if (Files.exists(from)) {
          hardLinkOrCopy(from, to);
        }
      }
    }
  }

  private void extractFeaturedModFiles(Path archive, String filesRegex) throws IOException {
    RandomAccessFile randomAccessFile = new RandomAccessFile(archive.toFile(), "r");
    RandomAccessFileInStream randomAccessFileStream = new RandomAccessFileInStream(randomAccessFile);
    IInArchive inArchive = SevenZip.openInArchive(null, randomAccessFileStream);
    logger.info("[extractFeaturedModFiles] with regex={}", filesRegex);
    Pattern pattern = Pattern.compile(filesRegex);
    for (ISimpleInArchiveItem item : inArchive.getSimpleInterface().getArchiveItems()) {
      if (!item.isFolder()) {
        Matcher matcher = pattern.matcher(item.getPath().replace('\\', '/'));
        if (matcher.find()) {
          Path dest = targetPath.resolve(matcher.group(1));
          logger.info("[extractFeaturedModFiles] extracting {}:{} to {}", archive, item.getPath(), dest);
          if (dest.getParent() != null && !Files.exists(dest.getParent())) {
            dest.getParent().toFile().mkdirs();
          }
          Files.deleteIfExists(dest);
          FileOutputStream destStream = new FileOutputStream(dest.toFile());
          ExtractOperationResult result = item.extractSlow(data -> {
            try {
              new BufferedInputStream(new ByteArrayInputStream(data)).transferTo(destStream);
            } catch (IOException e) {
              logger.error("[extractFeaturedModFiles] error writing file {}: {}", dest, e.getMessage());
              return 0;
            }
            return data.length;
          });

          destStream.close();
          if (result != ExtractOperationResult.OK) {
            throw new RuntimeException(String.format("Error extracting archive. Extracting error: %s", result));
          }
        }
      }
    }
  }
}
