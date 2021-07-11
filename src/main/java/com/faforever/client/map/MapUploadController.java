package com.faforever.client.map;

import com.faforever.client.api.dto.ApiException;
import com.faforever.client.config.ClientProperties;
import com.faforever.client.fa.MapTool;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.event.MapUploadedEvent;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.CopyErrorAction;
import com.faforever.client.notification.DismissAction;
import com.faforever.client.notification.GetHelpAction;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.task.CompletableTask;
import com.google.common.eventbus.EventBus;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.faforever.client.notification.Severity.ERROR;
import static java.util.Arrays.asList;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MapUploadController implements Controller<Node> {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final MapService mapService;
  private final ExecutorService executorService;
  private final NotificationService notificationService;
  private final ReportingService reportingService;
  private final PlatformService platformService;
  private final I18n i18n;
  private final EventBus eventBus;
  private final ClientProperties clientProperties;
  public Label rankedLabel;
  public Label uploadTaskMessageLabel;
  public Label uploadTaskTitleLabel;
  public Label sizeLabel;
  public Pane parseProgressPane;
  public Pane uploadProgressPane;
  public Pane uploadCompletePane;
  public ProgressBar uploadProgressBar;
  public Pane mapInfoPane;
  public Label mapNameLabel;
  public ImageView thumbnailImageView;
  public Region mapUploadRoot;
  public CheckBox rankedCheckbox;
  public Label rulesLabel;
  private Path tempDirectory; // should be just parent of stagingDirectory, but we'll remember it explicitely since its the directory we want to clean up when we're finished
  private Path stagingDirectory;
  private String archiveFileName;
  private List<String[]> mapDetails;
  private CompletableTask<Void> uploadMapTask;
  private Runnable cancelButtonClickedListener;

  public MapUploadController(MapService mapService, ExecutorService executorService, NotificationService notificationService,
                             ReportingService reportingService, PlatformService platformService, I18n i18n, EventBus eventBus,
                             ClientProperties clientProperties) {
    this.mapService = mapService;
    this.executorService = executorService;
    this.notificationService = notificationService;
    this.reportingService = reportingService;
    this.platformService = platformService;
    this.i18n = i18n;
    this.eventBus = eventBus;
    this.clientProperties = clientProperties;
  }

  public void initialize() {
    mapInfoPane.managedProperty().bind(mapInfoPane.visibleProperty());
    uploadProgressPane.managedProperty().bind(uploadProgressPane.visibleProperty());
    parseProgressPane.managedProperty().bind(parseProgressPane.visibleProperty());
    uploadCompletePane.managedProperty().bind(uploadCompletePane.visibleProperty());

    mapInfoPane.setVisible(false);
    uploadProgressPane.setVisible(false);
    parseProgressPane.setVisible(false);
    uploadCompletePane.setVisible(false);

    rankedLabel.setLabelFor(rankedCheckbox);
  }

  public void prepareUpload(Path archivePath) {
    archiveFileName = archivePath.getFileName().toString();
    CompletableFuture.runAsync(() -> {
      enterParsingState();
      stageUpload(archivePath);
      enterMapInfoState();
      Platform.runLater(() -> updateDisplay());
    });
  }

  private void stageUpload(Path archivePath) {
    try {
      tempDirectory = Files.createTempDirectory("mapupload"); // NB tempDirectory is recursively deleted on clean up
      stagingDirectory = tempDirectory.resolve(archiveFileName);
      Files.createDirectories(stagingDirectory);
      logger.info("Staging archive upload at {}", stagingDirectory);
      mapDetails = MapTool.listMapsInArchive(archivePath, stagingDirectory, true);
      Files.copy(archivePath, stagingDirectory.resolve(archiveFileName));

      String jsonMapDetails = MapTool.toJson(mapDetails);
      FileOutputStream jsonFos = new FileOutputStream(stagingDirectory.resolve(archiveFileName+".json").toFile());
      jsonFos.write(jsonMapDetails.getBytes());
      jsonFos.close();
    } catch (IOException e) {
      logger.warn("unable to prep archive for upload:", e);
      notificationService.addImmediateErrorNotification(e, "maptool.error");
    }
  }

  private void updateDisplay()  {
    mapNameLabel.textProperty().setValue(archiveFileName);
    sizeLabel.textProperty().setValue(mapDetails.size() + " maps");

    try {
      Files.walkFileTree(stagingDirectory.resolve("mini"), new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          FileInputStream ifs = new FileInputStream(file.toFile());
          thumbnailImageView.setImage(new Image(ifs));
          ifs.close();
          return FileVisitResult.TERMINATE;
        }
      });
    } catch (IOException e) {
      logger.warn("Unable to load image from map archive");
    }
  }

  private void enterParsingState() {
    mapInfoPane.setVisible(false);
    uploadProgressPane.setVisible(false);
    parseProgressPane.setVisible(true);
    uploadCompletePane.setVisible(false);
  }

  private void enterMapInfoState() {
    mapInfoPane.setVisible(true);
    uploadProgressPane.setVisible(false);
    parseProgressPane.setVisible(false);
    uploadCompletePane.setVisible(false);
  }

  public void onCancelUploadClicked() {
    uploadMapTask.cancel(true);
    enterMapInfoState();
  }

  private void onUploadFailed(Throwable throwable) {
    enterMapInfoState();
    if (throwable instanceof ApiException) {
      notificationService.addServerNotification(new ImmediateNotification(
          i18n.get("errorTitle"), i18n.get("mapVault.upload.failed", throwable.getLocalizedMessage()), ERROR,
          asList(
              new Action(i18n.get("mapVault.upload.retry"), event -> onUploadClicked()),
              new DismissAction(i18n)
          )
      ));
    } else {
      notificationService.addServerNotification(new ImmediateNotification(
          i18n.get("errorTitle"), i18n.get("mapVault.upload.failed", throwable.getLocalizedMessage()), ERROR, throwable,
          asList(
              new Action(i18n.get("mapVault.upload.retry"), event -> onUploadClicked()),
              new CopyErrorAction(i18n, reportingService, throwable),
              new GetHelpAction(i18n, reportingService),
              new DismissAction(i18n)
          )
      ));
    }
  }

  public void onUploadClicked() {

    if (mapDetails == null) {
      notificationService.addImmediateWarnNotification("mapVault.upload.nullDetails");
      return;
    }
    if (mapDetails.isEmpty()) {
      notificationService.addImmediateWarnNotification("mapVault.upload.noDetails");
      return;
    }

    enterUploadingState();

    uploadProgressPane.setVisible(true);
    uploadMapTask = mapService.uploadMap(stagingDirectory, archiveFileName, rankedCheckbox.isSelected(), MapTool.toListOfDict(mapDetails));
    uploadTaskTitleLabel.textProperty().bind(uploadMapTask.titleProperty());
    uploadTaskMessageLabel.textProperty().bind(uploadMapTask.messageProperty());
    uploadProgressBar.progressProperty().bind(uploadMapTask.progressProperty());

    uploadMapTask.getFuture()
        .thenAccept(v -> eventBus.post(new MapUploadedEvent(stagingDirectory)))
        .thenAccept(aVoid -> enterUploadCompleteState())
        .thenAccept(aVoid -> deleteDirectoryRecursion(tempDirectory))
        .exceptionally(throwable -> {
          if (!(throwable instanceof CancellationException)) {
            onUploadFailed(throwable.getCause());
          }
          return null;
        });
  }

  private void deleteDirectoryRecursion(Path path) {
    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
        for (Path entry : entries) {
          deleteDirectoryRecursion(entry);
        }
      } catch (IOException e) {
      }
    }
    try {
      Files.delete(path);
    }
    catch (IOException e) {
    }
  }

  private void enterUploadingState() {
    mapInfoPane.setVisible(false);
    uploadProgressPane.setVisible(true);
    parseProgressPane.setVisible(false);
    uploadCompletePane.setVisible(false);
  }

  private void enterUploadCompleteState() {
    mapInfoPane.setVisible(false);
    uploadProgressPane.setVisible(false);
    parseProgressPane.setVisible(false);
    uploadCompletePane.setVisible(true);
  }

  public void onCancelClicked() {
    cancelButtonClickedListener.run();
    deleteDirectoryRecursion(tempDirectory);
  }

  public Region getRoot() {
    return mapUploadRoot;
  }

  public void setOnCancelButtonClickedListener(Runnable cancelButtonClickedListener) {
    this.cancelButtonClickedListener = cancelButtonClickedListener;
  }
}
