package com.faforever.client.tournament;

import com.faforever.client.api.dto.MapPool;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.remote.FafService;
import com.faforever.client.remote.domain.TournamentCreateMessage;
import com.faforever.client.remote.domain.TournamentEditMessage;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Shared form controller for creating and editing player-created tournaments.
 * In CREATE mode all sections are visible. In EDIT mode only basics + schedule
 * are shown (format, maps, and mod are locked after creation).
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class TournamentFormController implements Controller<Node> {

  public enum Mode { CREATE, EDIT }

  private final I18n i18n;
  private final FafService fafService;
  private final MapService mapService;

  public VBox formRoot;
  public ScrollPane formScroll;

  // Basics
  public TextField nameField;
  public TextArea descField;

  // Format (create-only)
  public VBox formatSection;
  public ComboBox<String> formatCombo;
  public ComboBox<Integer> bestOfCombo;
  public ComboBox<Integer> ppsCombo;
  public Spinner<Integer> noshowSpinner;
  public Spinner<Integer> checkInMinutesSpinner;

  // Swiss-only rows (toggled by formatCombo's value)
  public HBox swissRoundsRow;
  public Spinner<Integer> swissRoundsSpinner;
  public HBox topCutRow;
  public ComboBox<Integer> topCutCombo;
  public HBox topCutFormatRow;
  public ComboBox<String> topCutFormatCombo;

  // Maps & Mod (create-only)
  public VBox mapsSection;
  public ComboBox<FeaturedMod> modCombo;
  public ComboBox<MapPool> poolCombo;
  public org.controlsfx.control.SearchableComboBox<MapBean> singleMapCombo;
  public ComboBox<com.faforever.client.leaderboard.Leaderboard> leaderboardCombo;
  public TextField minRatingField;
  public TextField maxRatingField;
  public ComboBox<String> visibilityCombo;

  // Schedule
  public HBox minPlayersRow;
  public Spinner<Integer> minPlayersSpinner;
  public DatePicker datePicker;
  public TextField timeField;
  public Label utcClockLabel;

  private javafx.animation.Timeline utcClockTimeline;
  private Mode mode = Mode.CREATE;
  private TournamentBean editTarget;
  private Runnable onDone;

  public TournamentFormController(I18n i18n, FafService fafService, MapService mapService) {
    this.i18n = i18n;
    this.fafService = fafService;
    this.mapService = mapService;
  }

  /** Deferred fixed-map selection for edit mode: the map_version id to select
   *  once the ranked-map list finishes loading. Null when nothing to preselect. */
  private Integer pendingSingleMapVersionId;

  @Override
  public Node getRoot() {
    return formRoot;
  }

  @FXML
  public void initialize() {
    // Format combos
    formatCombo.getItems().addAll("single_elimination", "double_elimination", "swiss", "king_of_the_hill");
    formatCombo.getSelectionModel().selectFirst();
    bestOfCombo.getItems().addAll(1, 3, 5);
    bestOfCombo.getSelectionModel().selectFirst();
    ppsCombo.getItems().addAll(1, 2);
    ppsCombo.getSelectionModel().selectFirst();
    noshowSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 60, 20, 5));
    // Check-in window: 0 disables (legacy behaviour), step 5 to discourage
    // odd values, max 60 to match the server-side cap.
    checkInMinutesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 60, 0, 5));
    minPlayersSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 64, 4));

    // Swiss-only controls. Defaults mirror the server's defaults in
    // store._parse_format_options (3 rounds, top cut of 4, single elim).
    swissRoundsSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 15, 3, 1));
    topCutCombo.getItems().addAll(0, 2, 4, 8, 16);
    topCutCombo.setConverter(new StringConverter<>() {
      @Override public String toString(Integer v) {
        if (v == null) return "";
        return v == 0 ? i18n.get("tournament.create.topCut.none") : v.toString();
      }
      @Override public Integer fromString(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
      }
    });
    topCutCombo.setValue(4);
    topCutFormatCombo.getItems().addAll("single_elimination", "double_elimination");
    topCutFormatCombo.getSelectionModel().selectFirst();

    // Wire swiss-row visibility to the format selection and to the
    // top-cut value (top_cut_format is only meaningful when top_cut > 0).
    formatCombo.valueProperty().addListener((obs, o, n) -> updateSwissVisibility());
    topCutCombo.valueProperty().addListener((obs, o, n) -> updateSwissVisibility());
    updateSwissVisibility();

    // Mod combo
    modCombo.setCellFactory(lv -> new ListCell<>() {
      @Override protected void updateItem(FeaturedMod item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? i18n.get("tournament.detail.modAny") : item.getDisplayName());
      }
    });
    modCombo.setButtonCell(modCombo.getCellFactory().call(null));
    fafService.getFeaturedMods().thenAccept(mods -> JavaFxUtil.runLater(() -> {
      modCombo.getItems().add(null);
      mods.stream()
          .sorted(Comparator.comparing(FeaturedMod::getDisplayName,
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
          .forEach(modCombo.getItems()::add);
      // Only auto-select "Any" in create mode. In edit mode the list-change
      // listener in setMode picks the tournament's current mod — selectFirst
      // here would overwrite that selection.
      if (mode == Mode.CREATE) {
        modCombo.getSelectionModel().selectFirst();
      }
    }));

    // Map pool combo
    poolCombo.setCellFactory(lv -> new ListCell<>() {
      @Override protected void updateItem(MapPool item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? i18n.get("tournament.create.noMapPool") : item.getName());
      }
    });
    poolCombo.setButtonCell(poolCombo.getCellFactory().call(null));
    try {
      List<MapPool> pools = fafService.getMapPools();
      poolCombo.getItems().add(null);
      pools.stream()
          .sorted(Comparator.comparing(MapPool::getName,
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
          .forEach(poolCombo.getItems()::add);
      poolCombo.getSelectionModel().selectFirst();
    } catch (Exception e) {
      log.warn("Failed to load map pools", e);
    }

    // Single fixed-map combo. A null entry means "no fixed map". Loaded
    // asynchronously from the ranked-map list. A fixed map and a map pool
    // are mutually exclusive (the server prefers the fixed map), so picking
    // one clears and disables the other.
    singleMapCombo.setCellFactory(lv -> new ListCell<>() {
      @Override protected void updateItem(MapBean item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? i18n.get("tournament.create.noSingleMap") : item.getMapName());
      }
    });
    singleMapCombo.setButtonCell(singleMapCombo.getCellFactory().call(null));
    // SearchableComboBox builds its type-to-filter text from the converter
    // (falling back to Object.toString()), so a converter is required for the
    // search box to match on map names rather than bean identity.
    singleMapCombo.setConverter(new StringConverter<>() {
      @Override public String toString(MapBean m) {
        return m == null ? i18n.get("tournament.create.noSingleMap") : m.getMapName();
      }
      @Override public MapBean fromString(String s) {
        if (s == null || s.isBlank()) {
          return null;
        }
        return singleMapCombo.getItems().stream()
            .filter(m -> m != null && s.equals(m.getMapName()))
            .findFirst().orElse(singleMapCombo.getValue());
      }
    });
    singleMapCombo.getItems().add(null);
    singleMapCombo.getSelectionModel().selectFirst();
    mapService.getAllRankedMaps().thenAccept(maps -> JavaFxUtil.runLater(() -> {
      maps.stream()
          .sorted(Comparator.comparing(MapBean::getMapName,
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
          .forEach(singleMapCombo.getItems()::add);
      // Edit mode may have asked for a specific map before the list loaded.
      if (pendingSingleMapVersionId != null) {
        selectSingleMapById(pendingSingleMapVersionId);
      }
    })).exceptionally(t -> {
      log.warn("Failed to load ranked maps for fixed-map picker", t);
      return null;
    });

    singleMapCombo.valueProperty().addListener((obs, o, n) -> {
      if (n != null && poolCombo.getValue() != null) {
        poolCombo.setValue(null);
      }
      poolCombo.setDisable(n != null);
    });
    poolCombo.valueProperty().addListener((obs, o, n) -> {
      if (n != null && singleMapCombo.getValue() != null) {
        singleMapCombo.setValue(null);
      }
      singleMapCombo.setDisable(n != null);
    });

    // Leaderboard combo (for seeding rating)
    // Display the (i18n'd) nameKey — production hijacks that field to
    // hold a human-readable string, and i18n.get() falls back to the
    // key verbatim when no translation exists (same behaviour as the
    // Leaderboards tab). technicalName is kept only as a last-resort
    // fallback for leaderboards with a missing nameKey.
    java.util.function.Function<com.faforever.client.leaderboard.Leaderboard, String> displayName =
        lb -> lb.getNameKey() != null && !lb.getNameKey().isBlank()
            ? i18n.get(lb.getNameKey())
            : lb.getTechnicalName();
    leaderboardCombo.setCellFactory(lv -> new ListCell<com.faforever.client.leaderboard.Leaderboard>() {
      @Override protected void updateItem(com.faforever.client.leaderboard.Leaderboard item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText("(Default)"); return; }
        setText(displayName.apply(item));
      }
    });
    leaderboardCombo.setButtonCell(leaderboardCombo.getCellFactory().call(null));
    fafService.getLeaderboards().thenAccept(lbs -> JavaFxUtil.runLater(() -> {
      leaderboardCombo.getItems().add(null);
      lbs.stream()
          .sorted(Comparator.comparing(displayName,
              Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
          .forEach(leaderboardCombo.getItems()::add);
      if (mode == Mode.CREATE) {
        leaderboardCombo.getSelectionModel().selectFirst();
      }
    }));

    // Map visibility combo
    visibilityCombo.getItems().addAll(
        "always_visible", "hidden_until_tournament_start", "hidden_until_round_start");
    visibilityCombo.setConverter(new StringConverter<>() {
      @Override public String toString(String value) {
        if (value == null) return i18n.get("tournament.mapVisibility.alwaysVisible");
        switch (value) {
          case "hidden_until_tournament_start":
            return i18n.get("tournament.mapVisibility.hiddenUntilTournamentStart");
          case "hidden_until_round_start":
            return i18n.get("tournament.mapVisibility.hiddenUntilRoundStart");
          default:
            return i18n.get("tournament.mapVisibility.alwaysVisible");
        }
      }
      @Override public String fromString(String s) { return s; }
    });
    visibilityCombo.getSelectionModel().selectFirst();

    // Live UTC clock so the user knows what "now" is when picking a start time
    updateUtcClock();
    utcClockTimeline = new javafx.animation.Timeline(
        new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> updateUtcClock()));
    utcClockTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
    utcClockTimeline.play();
    formRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
      if (newScene == null && utcClockTimeline != null) utcClockTimeline.stop();
    });
  }

  private void updateSwissVisibility() {
    boolean isSwiss = "swiss".equals(formatCombo.getValue());
    swissRoundsRow.setVisible(isSwiss);
    swissRoundsRow.setManaged(isSwiss);
    topCutRow.setVisible(isSwiss);
    topCutRow.setManaged(isSwiss);
    // top_cut_format is only relevant when there IS a top cut.
    Integer cut = topCutCombo.getValue();
    boolean showTopCutFormat = isSwiss && cut != null && cut > 0;
    topCutFormatRow.setVisible(showTopCutFormat);
    topCutFormatRow.setManaged(showTopCutFormat);
  }

  private void updateUtcClock() {
    utcClockLabel.setText("Current UTC: " +
        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
  }

  @FXML
  public void onClearSchedule() {
    datePicker.setValue(null);
    timeField.clear();
  }

  /** Configure the form for CREATE or EDIT mode. Call before showing the dialog. */
  public void setMode(Mode mode, TournamentBean editTarget) {
    this.mode = mode;
    this.editTarget = editTarget;

    // In edit mode, show everything except players-per-side (teams may
    // already be formed around it). All other settings are editable
    // while the tournament is still pending.
    boolean isEdit = mode == Mode.EDIT;
    if (isEdit) {
      // Lock players-per-side (within the format section)
      ppsCombo.setDisable(true);
    }

    if (isEdit && editTarget != null) {
      nameField.setText(editTarget.getName() != null ? editTarget.getName() : "");
      descField.setText(editTarget.getDescription() != null ? editTarget.getDescription() : "");

      // Format
      if (editTarget.getTournamentType() != null) {
        formatCombo.setValue(editTarget.getTournamentType());
      }
      if (editTarget.getBestOf() > 0) bestOfCombo.setValue(editTarget.getBestOf());
      if (editTarget.getPlayersPerSide() > 0) ppsCombo.setValue(editTarget.getPlayersPerSide());
      if (editTarget.getNoshowTimeoutMinutes() > 0) {
        noshowSpinner.getValueFactory().setValue(editTarget.getNoshowTimeoutMinutes());
      }
      // 0 is the legacy "no check-in" value, which is also the spinner's
      // default — set even when zero so the field accurately reflects state.
      checkInMinutesSpinner.getValueFactory().setValue(editTarget.getCheckInMinutes());

      // Swiss-specific fields on the edit target. TournamentBean parses
      // format_options JSON into these; zeros mean "not set" so we leave
      // the spinner/combo defaults.
      if (editTarget.getSwissRounds() > 0) {
        swissRoundsSpinner.getValueFactory().setValue(editTarget.getSwissRounds());
      }
      if (editTarget.getTopCut() >= 0 && topCutCombo.getItems().contains(editTarget.getTopCut())) {
        topCutCombo.setValue(editTarget.getTopCut());
      }
      if (editTarget.getTopCutFormat() != null) {
        topCutFormatCombo.setValue(editTarget.getTopCutFormat());
      }
      updateSwissVisibility();

      // Map pool — loaded synchronously, match by name
      if (editTarget.getMapPoolName() != null) {
        for (MapPool p : poolCombo.getItems()) {
          if (p != null && editTarget.getMapPoolName().equals(p.getName())) {
            poolCombo.setValue(p);
            break;
          }
        }
      }

      // Fixed single map — the ranked-map list loads async, so remember the
      // target id and let the loader select it; also try now in case it's
      // already populated.
      if (editTarget.getSingleMapVersionId() != null) {
        pendingSingleMapVersionId = editTarget.getSingleMapVersionId();
        selectSingleMapById(editTarget.getSingleMapVersionId());
      }

      // Mod — loaded async, so defer selection until the items arrive
      String targetModName = editTarget.getFeaturedModTechnicalName();
      if (targetModName != null) {
        // If items already loaded, select now; otherwise listen for changes
        Runnable selectMod = () -> {
          for (FeaturedMod fm : modCombo.getItems()) {
            if (fm != null && targetModName.equals(fm.getTechnicalName())) {
              modCombo.setValue(fm);
              break;
            }
          }
        };
        if (modCombo.getItems().size() > 1) {
          selectMod.run();
        } else {
          modCombo.getItems().addListener(
              (javafx.collections.ListChangeListener<FeaturedMod>) c -> selectMod.run());
        }
      }

      // Leaderboard — loaded async, defer like mod
      String targetLbName = editTarget.getLeaderboardTechnicalName();
      if (targetLbName != null) {
        Runnable selectLb = () -> {
          for (com.faforever.client.leaderboard.Leaderboard lb : leaderboardCombo.getItems()) {
            if (lb != null && targetLbName.equals(lb.getTechnicalName())) {
              leaderboardCombo.setValue(lb);
              break;
            }
          }
        };
        if (leaderboardCombo.getItems().size() > 1) {
          selectLb.run();
        } else {
          leaderboardCombo.getItems().addListener(
              (javafx.collections.ListChangeListener<com.faforever.client.leaderboard.Leaderboard>) c -> selectLb.run());
        }
      }

      // Rating range
      if (editTarget.getMinRating() != null) {
        minRatingField.setText(editTarget.getMinRating().toString());
      }
      if (editTarget.getMaxRating() != null) {
        maxRatingField.setText(editTarget.getMaxRating().toString());
      }

      // Map visibility
      if (editTarget.getMapVisibility() != null) {
        visibilityCombo.setValue(editTarget.getMapVisibility());
      }

      // Schedule
      if (editTarget.getStartingAt() != null) {
        OffsetDateTime utc = editTarget.getStartingAt().withOffsetSameInstant(ZoneOffset.UTC);
        datePicker.setValue(utc.toLocalDate());
        timeField.setText(utc.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
      }
    }
  }

  public void setOnDone(Runnable onDone) {
    this.onDone = onDone;
  }

  @FXML
  public void onOk() {
    if (mode == Mode.CREATE) {
      submitCreate();
    } else {
      submitEdit();
    }
    if (onDone != null) onDone.run();
  }

  @FXML
  public void onCancel() {
    if (onDone != null) onDone.run();
  }

  /** Select the fixed-map combo entry whose map_version id matches, if present. */
  private void selectSingleMapById(int mapVersionId) {
    for (MapBean m : singleMapCombo.getItems()) {
      if (m == null || m.getId() == null) {
        continue;
      }
      try {
        if (Integer.parseInt(m.getId()) == mapVersionId) {
          singleMapCombo.setValue(m);
          return;
        }
      } catch (NumberFormatException ignored) {}
    }
  }

  private void submitCreate() {
    String name = nameField.getText();
    if (name == null || name.isBlank()) return;

    TournamentCreateMessage msg = new TournamentCreateMessage();
    msg.setName(name.trim());
    msg.setDescription(descField.getText() != null ? descField.getText().trim() : "");
    msg.setFormat(formatCombo.getValue());
    msg.setBestOf(bestOfCombo.getValue());
    msg.setPlayersPerSide(ppsCombo.getValue());
    msg.setNoshowTimeoutMinutes(noshowSpinner.getValue());
    msg.setCheckInMinutes(checkInMinutesSpinner.getValue());
    msg.setMinPlayers(minPlayersSpinner.getValue());

    FeaturedMod selectedMod = modCombo.getValue();
    if (selectedMod != null) {
      try { msg.setFeaturedModId(Integer.parseInt(selectedMod.getId())); } catch (NumberFormatException ignored) {}
    }
    MapPool selectedPool = poolCombo.getValue();
    if (selectedPool != null) {
      try { msg.setMapPoolId(Integer.parseInt(selectedPool.getId())); } catch (NumberFormatException ignored) {}
    }
    MapBean selectedSingleMap = singleMapCombo.getValue();
    if (selectedSingleMap != null && selectedSingleMap.getId() != null) {
      try { msg.setSingleMapVersionId(Integer.parseInt(selectedSingleMap.getId())); } catch (NumberFormatException ignored) {}
    }
    com.faforever.client.leaderboard.Leaderboard selectedLb = leaderboardCombo.getValue();
    if (selectedLb != null) {
      msg.setLeaderboardId(selectedLb.getId());
    }
    try {
      String minR = minRatingField.getText();
      if (minR != null && !minR.isBlank()) msg.setMinRating(Integer.parseInt(minR.trim()));
    } catch (NumberFormatException ignored) {}
    try {
      String maxR = maxRatingField.getText();
      if (maxR != null && !maxR.isBlank()) msg.setMaxRating(Integer.parseInt(maxR.trim()));
    } catch (NumberFormatException ignored) {}

    if (datePicker.getValue() != null) {
      String time = timeField.getText() != null && !timeField.getText().isBlank()
          ? timeField.getText().trim() : "20:00";
      msg.setScheduledStartAt(datePicker.getValue() + " " + time + ":00");
    }

    msg.setMapVisibility(visibilityCombo.getValue());

    // Swiss knobs — only include when the chosen format is swiss so the
    // server doesn't store stale values for other formats.
    if ("swiss".equals(formatCombo.getValue())) {
      msg.setSwissRounds(swissRoundsSpinner.getValue());
      Integer cut = topCutCombo.getValue();
      msg.setTopCut(cut);
      if (cut != null && cut > 0) {
        msg.setTopCutFormat(topCutFormatCombo.getValue());
      }
    }

    fafService.tournamentCreate(msg);
  }

  private static Integer parseIntOrNull(String s) {
    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
  }

  private void submitEdit() {
    if (editTarget == null || editTarget.getId() == null) return;
    TournamentEditMessage msg = new TournamentEditMessage(Integer.parseInt(editTarget.getId()));
    msg.setName(nameField.getText() != null ? nameField.getText().trim() : null);
    msg.setDescription(descField.getText() != null ? descField.getText() : null);
    msg.setFormat(formatCombo.getValue());
    msg.setBestOf(bestOfCombo.getValue());
    msg.setNoshowTimeoutMinutes(noshowSpinner.getValue());
    msg.setCheckInMinutes(checkInMinutesSpinner.getValue());
    msg.setMinPlayers(minPlayersSpinner.getValue());
    msg.setMapVisibility(visibilityCombo.getValue());

    // Mod / pool / leaderboard: send 0 when the combo is null so the server
    // can clear a previously-set FK. JsonMessageSerializer drops null fields
    // (setSerializeNulls(false)), so without an explicit sentinel "(Any/None/
    // Default)" would be indistinguishable from "leave unchanged" on the wire.
    // The server treats 0/falsy as clear (tournament_manager:1670-1674).
    FeaturedMod selectedMod = modCombo.getValue();
    if (selectedMod != null) {
      try { msg.setFeaturedModId(Integer.parseInt(selectedMod.getId())); } catch (NumberFormatException ignored) {}
    } else {
      msg.setFeaturedModId(0);
    }
    MapPool selectedPool = poolCombo.getValue();
    if (selectedPool != null) {
      try { msg.setMapPoolId(Integer.parseInt(selectedPool.getId())); } catch (NumberFormatException ignored) {}
    } else {
      msg.setMapPoolId(0);
    }
    // Single fixed map: send 0 to clear when none selected (same sentinel as
    // map pool). A fixed map and a pool are mutually exclusive in the UI.
    MapBean selectedSingleMap = singleMapCombo.getValue();
    if (selectedSingleMap != null && selectedSingleMap.getId() != null) {
      try { msg.setSingleMapVersionId(Integer.parseInt(selectedSingleMap.getId())); } catch (NumberFormatException ignored) {}
    } else {
      msg.setSingleMapVersionId(0);
    }
    com.faforever.client.leaderboard.Leaderboard selectedLb = leaderboardCombo.getValue();
    if (selectedLb != null) {
      msg.setLeaderboardId(selectedLb.getId());
    } else {
      msg.setLeaderboardId(0);
    }
    // Send 0 when blank to explicitly clear the rating restriction.
    // (Gson skips null fields, so we use 0 as a "clear" sentinel;
    // the server treats 0 as NULL.)
    String minR = minRatingField.getText();
    msg.setMinRating(minR != null && !minR.isBlank() ? parseIntOrNull(minR.trim()) : 0);
    String maxR = maxRatingField.getText();
    msg.setMaxRating(maxR != null && !maxR.isBlank() ? parseIntOrNull(maxR.trim()) : 0);

    if (datePicker.getValue() != null) {
      String time = timeField.getText() != null && !timeField.getText().isBlank()
          ? timeField.getText().trim() : "20:00";
      msg.setScheduledStartAt(datePicker.getValue() + " " + time + ":00");
    } else {
      msg.setScheduledStartAt(TournamentEditMessage.CLEAR);
    }

    // Same guarding as create: only send swiss knobs if format is swiss.
    if ("swiss".equals(formatCombo.getValue())) {
      msg.setSwissRounds(swissRoundsSpinner.getValue());
      Integer cut = topCutCombo.getValue();
      msg.setTopCut(cut);
      if (cut != null && cut > 0) {
        msg.setTopCutFormat(topCutFormatCombo.getValue());
      }
    }

    fafService.tournamentEdit(msg);
  }
}

