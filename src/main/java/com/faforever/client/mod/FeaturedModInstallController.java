package com.faforever.client.mod;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.Action.Type;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.AskAlwaysOrNever;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.StageHolder;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class FeaturedModInstallController implements Controller<Node> {

  final PreferencesService preferencesService;
  final UiService uiService;
  final ModService modService;
  final NotificationService notificationService;
  final I18n i18n;
  final PlatformService platformService;

  public CheckBox useExistingCheckBox;
  public ComboBox<AskAlwaysOrNever> autoUpdateComboBox;
  public Pane featuredModInstallControllerRoot;
  public Label titleLabel;
  public GridPane settingsGridPane;
  public TextField originalTaPathTextField;
  public TextField installPackageUrlTextField;
  public TextField installPathTextField;
  public ButtonBar buttonBar;
  public GridPane newInstallParamsGridPane;
  public VBox containerLayout;
  public TextField commandLineTextField;

  private FeaturedMod featuredMod;
  private Optional<CompletableFuture<Path>> installedPathFuture;
  private Stage stage;

  public Parent getRoot() {
    return featuredModInstallControllerRoot;
  }

  public void initialize() {
    ObjectProperty<Path> otaExeProperty = preferencesService
        .getTotalAnnihilation(KnownFeaturedMod.DEFAULT.getTechnicalName()).getInstalledExePathProperty();

    otaExeProperty.addListener((obs, oldValue, newValue) -> {
        if (newValue != null) {
          originalTaPathTextField.setText(newValue.getParent().toString());
        }
    });
    if (otaExeProperty.get() != null) {
      originalTaPathTextField.setText(otaExeProperty.get().getParent().toString());
    }

    newInstallParamsGridPane.disableProperty().bind(useExistingCheckBox.selectedProperty());

    stage = new Stage(StageStyle.TRANSPARENT);
    stage.initModality(Modality.NONE);
    stage.initOwner(StageHolder.getStage());

    Scene scene = uiService.createScene(getRoot());
    stage.setScene(scene);
    stage.show();
  }

  public void setFeaturedMod(FeaturedMod fm) {
    this.featuredMod = fm;

    titleLabel.setText(i18n.get("installFeaturedMod.title", fm.getDisplayName()));
    FeaturedModInstallSpecs specs = new Gson().fromJson(fm.getInstallPackage(), FeaturedModInstallSpecs.class);
    installPackageUrlTextField.setText(specs.getUrl());

    TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(fm.getTechnicalName());
    configureAutoUpdateOption(taPrefs);

    if (taPrefs.getInstalledPath() != null) {
      installPathTextField.setText(taPrefs.getInstalledPath().toString());
    }
    else {
      preferencesService.getTotalAnnihilationAllMods().stream()
          .map(TotalAnnihilationPrefs::getInstalledPath)
          .filter(path -> path != null && Files.exists(path) &&
              !path.toString().contains("Program Files") &&
              !path.toString().contains("SteamApps"))
          .findAny()
          .ifPresentOrElse(
              path -> installPathTextField.setText(path.getParent().resolve("TAF-" + fm.getDisplayName()).toString()),
              () -> installPathTextField.setText(Paths.get(System.getProperty("user.home"),
                  "games", String.format("TAF-%s", fm.getDisplayName())).toString())
          );

    }
  }

  public void setInstalledPathFuture(Optional<CompletableFuture<Path>> future) {
    installedPathFuture = future;
  }

  public void show(Window owner) {
    boolean alreadyInstalled = Path.of(installPathTextField.getText()).resolve("TotalA.exe").toFile().exists();
    boolean validOriginalTaPath = Path.of(originalTaPathTextField.getText()).resolve("TotalA.exe").toFile().exists();
    useExistingCheckBox.setSelected(alreadyInstalled);
    if (!validOriginalTaPath && !alreadyInstalled) {
      originalTaPathTextField.requestFocus();
    }
    else if (!alreadyInstalled) {
      installPathTextField.requestFocus();
    }

    stage.show();
  }

  private void configureAutoUpdateOption(TotalAnnihilationPrefs preferences) {
    if (autoUpdateComboBox.getItems().isEmpty()) {
      autoUpdateComboBox.setItems(FXCollections.observableArrayList(AskAlwaysOrNever.values()));
      autoUpdateComboBox.setConverter(new StringConverter<>() {
        @Override
        public String toString(AskAlwaysOrNever option) {
          return i18n.get(option.getI18nKey());
        }

        @Override
        public AskAlwaysOrNever fromString(String s) {
          throw new UnsupportedOperationException("Not needed");
        }
      });
    }
    autoUpdateComboBox.setValue(preferences.getAutoUpdateEnable());
  }

  public void onCancelButton(ActionEvent actionEvent)  {
    getRoot().getScene().getWindow().hide();
    installedPathFuture.ifPresent(pathCompletableFuture -> pathCompletableFuture.completeExceptionally(
        new CancellationException("User cancelled")));
  }

  public void onConfirmButton(ActionEvent actionEvent) {
    Path installFolder = Path.of(installPathTextField.getText());
    Path installExe = installFolder.resolve("TotalA.exe");
    if (useExistingCheckBox.isSelected() && installExe.toFile().exists()) {
      preferencesService.setTotalAnnihilation(
          featuredMod.getTechnicalName(), installExe, commandLineTextField.getText(), autoUpdateComboBox.getValue());
      stage.hide();
      preferencesService.storeInBackground();
      installedPathFuture.ifPresent(future -> future.complete(installExe));
    }
    else if (useExistingCheckBox.isSelected()) {
      notificationService.addImmediateWarnNotification("gameChosen.noValidExe");
    }
    else {
      // we'll only set installedExePath once we're sure installation was successful
      TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(featuredMod.getTechnicalName());
      taPrefs.setCommandLineOptions(commandLineTextField.getText());
      taPrefs.setAutoUpdateEnable(autoUpdateComboBox.getValue());

      stage.hide();
      modService.downloadAndInstallFeaturedMod(featuredMod, Path.of(originalTaPathTextField.getText()),
          installPackageUrlTextField.getText(), Path.of(installPathTextField.getText()))
          .thenAccept((version) -> Platform.runLater(() -> {
            preferencesService.storeInBackground();
            installedPathFuture.ifPresent(future -> future.complete(installExe));
          }))
          .exceptionally(e -> {
            log.error("exception installing mod: {}", e.getMessage());
            notificationService.addNotification(new ImmediateNotification(
                i18n.get("installFeaturedMod.error", featuredMod.getDisplayName()),
                e.getCause().getMessage(),
                Severity.ERROR, List.of(new Action(
                    i18n.get("dismiss"), Type.OK_DONE, (a) -> Platform.runLater(
                        () -> stage.show())))));
            return null;
          });
    }
  }

  public void onInstallPathButton(ActionEvent actionEvent) {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle(i18n.get("installFeaturedMod.installPathTextField"));
    try {
      if (!installPathTextField.getText().isEmpty() && Files.exists(Path.of(installPathTextField.getText()))) {
        chooser.setInitialDirectory(Path.of(installPathTextField.getText()).toFile());
      }
    }
    catch (InvalidPathException ignored) { }
    File result = chooser.showDialog(getRoot().getScene().getWindow());

    if (result != null) {
      installPathTextField.setText(result.toString());
    }
  }

  public void onInstallPackageUrlButton(ActionEvent actionEvent) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(i18n.get("installFeaturedMod.installPackageUrlTextField"));
    try {
      if (!installPackageUrlTextField.getText().isEmpty() && Files.exists(Path.of(installPackageUrlTextField.getText()))) {
        chooser.setInitialDirectory(Path.of(installPackageUrlTextField.getText()).toFile());
      }
    }
    catch (InvalidPathException ignored) { }

    File result = chooser.showOpenDialog(getRoot().getScene().getWindow());

    if (result != null) {
      installPackageUrlTextField.setText(result.toString());
    }
  }

  public void onOriginalTaPathButton(ActionEvent actionEvent) {
    DirectoryChooser chooser = new DirectoryChooser();
    if (Path.of(originalTaPathTextField.getText()).toFile().exists()) {
      chooser.setInitialDirectory(Path.of(originalTaPathTextField.getText()).toFile());
    }
    chooser.setTitle(i18n.get("installFeaturedMod.originalTaPathTextField"));
    File result = chooser.showDialog(getRoot().getScene().getWindow());

    if (result != null) {
      originalTaPathTextField.setText(result.toString());
    }
  }

  public void openContextMenu(MouseEvent event) {
    ContextMenu contextMenu = new ContextMenu();
    contextMenu.getItems().add(new MenuItem(i18n.get("installFeaturedMod.visitModWebsite")));
    contextMenu.getItems().get(0).setOnAction(e -> visitModWebpage());

    contextMenu.getItems().add(new MenuItem(i18n.get("installFeaturedMod.visitTaOnGog")));
    contextMenu.getItems().get(1).setOnAction(e -> visitTaOnGog());

    contextMenu.getItems().add(new MenuItem(i18n.get("installFeaturedMod.visitTaOnSteam")));
    contextMenu.getItems().get(2).setOnAction(e -> visitTaOnSteam());

    if (!installPathTextField.getText().isEmpty() && Files.isDirectory(Path.of(installPathTextField.getText()))) {
      contextMenu.getItems().add(new MenuItem(i18n.get("installFeaturedMod.openInstallPath")));
      contextMenu.getItems().get(3).setOnAction(e -> openInstallPath());
    }

    contextMenu.show(this.getRoot().getScene().getWindow(), event.getScreenX(), event.getScreenY());
  }

  public void visitTaOnGog() {
    platformService.showDocument("https://www.gog.com/game/total_anihilation_commander_pack");
  }

  public void visitTaOnSteam() {
    platformService.showDocument("https://store.steampowered.com/app/298030/Total_Annihilation");
  }

  public void visitModWebpage() {
    if (featuredMod.getWebsite() != null && !featuredMod.getWebsite().isEmpty()) {
      platformService.showDocument(featuredMod.getWebsite());
    }
  }

  public void openInstallPath() {
    platformService.reveal(Path.of(installPathTextField.getText()));
  }
}
