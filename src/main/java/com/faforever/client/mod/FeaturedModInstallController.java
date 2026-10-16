package com.faforever.client.mod;

import com.faforever.client.fa.oa.OaInstallationService;
import com.faforever.client.fa.oa.OaPolicy;
import com.faforever.client.fa.oa.OaVersion;
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
import com.faforever.client.preferences.GameEngine;
import com.faforever.client.preferences.OaInstallationMode;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.StageHolder;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
  final OaInstallationService oaInstallationService;

  public CheckBox useExistingCheckBox;
  public ComboBox<AskAlwaysOrNever> autoUpdateComboBox;
  public Pane featuredModInstallControllerRoot;
  public Label titleLabel;
  public Label descriptionLabel;
  public Label installPathCopyToLabel;
  public Label installPathExistingLabel;
  public GridPane settingsGridPane;
  public TextField originalTaPathTextField;
  public TextField installPackageUrlTextField;
  public TextField installPathTextField;
  public ButtonBar buttonBar;
  public GridPane newInstallParamsGridPane;
  public VBox containerLayout;
  public TextField commandLineTextField;
  public VBox engineBox;
  public VBox oaOptionsBox;
  public ToggleGroup engineToggleGroup;
  public ToggleGroup oaModeToggleGroup;
  public RadioButton cavedogRadioButton;
  public RadioButton oaRadioButton;
  public RadioButton oaManagedRadioButton;
  public RadioButton oaExistingRadioButton;
  public TextField oaExecutableTextField;
  public Button oaExecutableButton;
  public Label oaStatusLabel;
  public Label oaNoWineLabel;

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

    oaOptionsBox.visibleProperty().bind(oaRadioButton.selectedProperty());
    oaOptionsBox.managedProperty().bind(oaRadioButton.selectedProperty());
    oaExecutableTextField.disableProperty().bind(oaExistingRadioButton.selectedProperty().not());
    oaExecutableButton.disableProperty().bind(oaExistingRadioButton.selectedProperty().not());
    oaNoWineLabel.setVisible(org.bridj.Platform.isLinux());
    oaNoWineLabel.setManaged(org.bridj.Platform.isLinux());
    oaExecutableTextField.textProperty().addListener((obs, oldValue, newValue) -> updateOaStatus());
    oaExistingRadioButton.selectedProperty().addListener((obs, oldValue, newValue) -> updateOaStatus());

    newInstallParamsGridPane.disableProperty().bind(useExistingCheckBox.selectedProperty());
    installPathCopyToLabel.visibleProperty().bind(useExistingCheckBox.selectedProperty().not());
    installPathExistingLabel.visibleProperty().bind(useExistingCheckBox.selectedProperty());

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
    descriptionLabel.setText(i18n.get("installFeaturedMod.description", fm.getDisplayName()));
    useExistingCheckBox.setText(i18n.get("installFeaturedMod.useExistingCheckBox", fm.getDisplayName()));
    installPathExistingLabel.setText(i18n.get("installFeaturedMod.installPathExistingLabel", fm.getDisplayName()));
    installPathTextField.promptTextProperty().bind(Bindings.createStringBinding(() -> useExistingCheckBox.isSelected()
        ? i18n.get("installFeaturedMod.installPathTextFieldExisting", fm.getDisplayName())
        : i18n.get("installFeaturedMod.installPathTextField", fm.getDisplayName()),
        useExistingCheckBox.selectedProperty()));

    FeaturedModInstallSpecs specs = new Gson().fromJson(fm.getInstallPackage(), FeaturedModInstallSpecs.class);
    installPackageUrlTextField.setText(specs.getUrl());

    TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(fm.getTechnicalName());
    configureAutoUpdateOption(taPrefs);
    configureEngineSection(taPrefs);

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

  /** The engine section is only offered for mods the server allows Open Annihilation for. */
  private void configureEngineSection(TotalAnnihilationPrefs taPrefs) {
    boolean supported = OaPolicy.isOaSupported(preferencesService.getClientRemoteConfiguration(),
        featuredMod.getTechnicalName());
    engineBox.setVisible(supported);
    engineBox.setManaged(supported);

    boolean oa = supported && taPrefs.isOpenAnnihilation();
    (oa ? oaRadioButton : cavedogRadioButton).setSelected(true);
    (taPrefs.getOaInstallation() == OaInstallationMode.EXISTING ? oaExistingRadioButton : oaManagedRadioButton)
        .setSelected(true);
    if (taPrefs.getOaExecutablePath() != null) {
      oaExecutableTextField.setText(taPrefs.getOaExecutablePath().toString());
    }
    updateOaStatus();
  }

  /** Shows whether the chosen existing Open Annihilation is a version the server allows. */
  private void updateOaStatus() {
    if (!oaExistingRadioButton.isSelected() || oaExecutableTextField.getText().isBlank()) {
      oaStatusLabel.setText("");
      return;
    }
    Path exe;
    try {
      exe = Path.of(oaExecutableTextField.getText().trim());
    } catch (InvalidPathException e) {
      oaStatusLabel.setText(i18n.get("installFeaturedMod.oa.versionUnknown"));
      return;
    }
    Optional<OaVersion> version = oaInstallationService.detectVersion(exe);
    if (version.isEmpty() || !Files.isRegularFile(exe)) {
      oaStatusLabel.setText(i18n.get("installFeaturedMod.oa.versionUnknown"));
    } else if (OaPolicy.isVersionAllowed(
        OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(), featuredMod.getTechnicalName()).orElse(null),
        version.get())) {
      oaStatusLabel.setText(i18n.get("installFeaturedMod.oa.versionAllowed", version.get().toString()));
    } else {
      oaStatusLabel.setText(i18n.get("installFeaturedMod.oa.versionNotAllowed", version.get().toString()));
    }
  }

  public void setInstalledPathFuture(Optional<CompletableFuture<Path>> future) {
    installedPathFuture = future;
  }

  public void show(Window owner) {
    boolean alreadyInstalled = Path.of(installPathTextField.getText()).resolve("TotalA.exe").toFile().exists()
        || (oaRadioButton.isSelected() && Files.isDirectory(Path.of(installPathTextField.getText()))
        && Files.exists(Path.of(installPathTextField.getText()).resolve("oamod.yaml")));
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

  private boolean validateTotalAExe(Path installExe) {
    return Files.exists(installExe);
  }

  private final String[] requiredFilesForExistingInstallation = { ".playx\\.dll", ".draw\\.dll", ".musi\\.dll" };
  private boolean validateRequiredForExistingInstallation(Path installFolder) {
    final File[] files = installFolder.toFile().listFiles();
    for (String requiredFile:  requiredFilesForExistingInstallation) {
      if (Stream.of(files)
          .filter(file -> Pattern.compile(requiredFile, Pattern.CASE_INSENSITIVE).matcher(file.getName()).find())
          .findAny().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public void onConfirmButton(ActionEvent actionEvent) {
    boolean openAnnihilation = engineBox.isVisible() && oaRadioButton.isSelected();
    Path installFolder = Path.of(installPathTextField.getText());
    Path installExe = installFolder.resolve("TotalA.exe");
    if (openAnnihilation && oaExistingRadioButton.isSelected() && !isExistingOaAllowed()) {
      notificationService.addImmediateWarnNotification("installFeaturedMod.oa.exeNotAllowed",
          oaExecutableTextField.getText());
    }
    else if (useExistingCheckBox.isSelected() && openAnnihilation && !Files.isDirectory(installFolder)) {
      // Open Annihilation reads the HPIs and needs no TotalA.exe; the folder only has to exist
      notificationService.addImmediateWarnNotification("gameChosen.noValidExe", installFolder);
    }
    else if (useExistingCheckBox.isSelected() && !openAnnihilation && !validateTotalAExe(installExe)) {
      notificationService.addImmediateWarnNotification("gameChosen.noValidExe", installFolder);
    }
    else if (useExistingCheckBox.isSelected() && !openAnnihilation && !validateRequiredForExistingInstallation(installFolder)) {
      notificationService.addImmediateWarnNotification("gameChosen.missingDlls", installFolder, featuredMod.getDisplayName());
    }
    else if (useExistingCheckBox.isSelected()) {
      preferencesService.setTotalAnnihilation(
          featuredMod.getTechnicalName(), installExe, commandLineTextField.getText(), autoUpdateComboBox.getValue());
      applyEnginePreferences(openAnnihilation);
      stage.hide();
      preferencesService.storeInBackground();
      prepareEngine(openAnnihilation).thenAccept(ok -> {
        if (ok) {
          installedPathFuture.ifPresent(future -> future.complete(installExe));
        }
      });
    }
    else {
      // we'll only set installedExePath once we're sure installation was successful
      TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(featuredMod.getTechnicalName());
      taPrefs.setCommandLineOptions(commandLineTextField.getText());
      taPrefs.setAutoUpdateEnable(autoUpdateComboBox.getValue());
      applyEnginePreferences(openAnnihilation);

      stage.hide();
      modService.downloadAndInstallFeaturedMod(featuredMod, Path.of(originalTaPathTextField.getText()),
          installPackageUrlTextField.getText(), Path.of(installPathTextField.getText()))
          .thenCompose(version -> prepareEngine(openAnnihilation))
          .thenAccept((ok) -> Platform.runLater(() -> {
            preferencesService.storeInBackground();
            if (ok) {
              installedPathFuture.ifPresent(future -> future.complete(installExe));
            }
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

  private boolean isExistingOaAllowed() {
    Path exe;
    try {
      exe = Path.of(oaExecutableTextField.getText().trim());
    } catch (InvalidPathException e) {
      return false;
    }
    return Files.isRegularFile(exe) && oaInstallationService.detectVersion(exe)
        .map(version -> OaPolicy.isVersionAllowed(OaPolicy.forMod(preferencesService.getClientRemoteConfiguration(),
            featuredMod.getTechnicalName()).orElse(null), version))
        .orElse(false);
  }

  private void applyEnginePreferences(boolean openAnnihilation) {
    TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(featuredMod.getTechnicalName());
    taPrefs.setEngine(openAnnihilation ? GameEngine.OPEN_ANNIHILATION : GameEngine.CAVEDOG);
    if (openAnnihilation) {
      boolean existing = oaExistingRadioButton.isSelected();
      taPrefs.setOaInstallation(existing ? OaInstallationMode.EXISTING : OaInstallationMode.TAF_MANAGED);
      taPrefs.setOaExecutablePath(existing ? Path.of(oaExecutableTextField.getText().trim()) : null);
    }
  }

  /**
   * Gets Open Annihilation ready (the pinned download for TAF-managed installs) before the dialog
   * reports success, so the first launch does not stall on it. Cavedog needs nothing.
   *
   * @return completes with false if the engine could not be prepared; the user has been told
   */
  private CompletableFuture<Boolean> prepareEngine(boolean openAnnihilation) {
    if (!openAnnihilation) {
      return CompletableFuture.completedFuture(true);
    }
    notificationService.addNotification(new com.faforever.client.notification.TransientNotification(
        featuredMod.getDisplayName(), i18n.get("installFeaturedMod.oa.downloading")));
    return oaInstallationService.ensureExecutable(featuredMod.getTechnicalName())
        .handle((exe, throwable) -> {
          if (throwable == null) {
            return true;
          }
          Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
          log.warn("Could not prepare Open Annihilation", cause);
          Platform.runLater(() -> {
            notificationService.addImmediateWarnNotification("installFeaturedMod.oa.error", cause.getMessage());
            stage.show();
          });
          return false;
        });
  }

  public void onOaExecutableButton(ActionEvent actionEvent) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(i18n.get("installFeaturedMod.oa.exePrompt"));
    try {
      Path current = Path.of(oaExecutableTextField.getText().trim());
      if (current.getParent() != null && Files.isDirectory(current.getParent())) {
        chooser.setInitialDirectory(current.getParent().toFile());
      }
    }
    catch (InvalidPathException ignored) { }

    File result = chooser.showOpenDialog(getRoot().getScene().getWindow());
    if (result != null) {
      oaExecutableTextField.setText(result.toString());
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
