package com.faforever.client.game;


import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.leaderboard.LeaderboardService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.theme.UiService;
import com.google.common.base.Joiner;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
@RequiredArgsConstructor
public class GameTooltipController implements Controller<Node> {

  private final UiService uiService;
  private final PlayerService playerService;
  private final LeaderboardService leaderboardService;

  public TitledPane modsPane;
  public TilePane teamsPane;
  public Label modsLabel;
  public VBox gameTooltipRoot;
  private ObservableMap<String, List<String>> lastTeams;
  private ObservableMap<String, String> lastSimMods;
  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener teamChangedListener;
  @SuppressWarnings("FieldCanBeLocal")
  private InvalidationListener simModsChangedListener;
  private WeakInvalidationListener weakTeamChangeListener;
  private WeakInvalidationListener weakModChangeListener;
  private int maxPrefColumns;
  private Game game;
  private boolean showMods;

  public void initialize() {
    modsPane.managedProperty().bind(modsPane.visibleProperty());
    maxPrefColumns = teamsPane.getPrefColumns();
    showMods = true;
  }

  public void setGame(Game game) {
    if (lastTeams != null && weakTeamChangeListener != null) {
      lastTeams.removeListener(weakTeamChangeListener);
    }
    if (showMods && lastSimMods != null && weakModChangeListener != null) {
      lastSimMods.removeListener(weakModChangeListener);
    }
    this.game = game;
  }

  public void displayGame() {
    if (game == null) {
      return;
    }
    teamChangedListener = change -> createTeams(game.getTeams(), game.getRatingType());
    lastTeams = game.getTeams();
    createTeams(game.getTeams(), game.getRatingType());
    weakTeamChangeListener = new WeakInvalidationListener(teamChangedListener);
    JavaFxUtil.addListener(game.getTeams(), weakTeamChangeListener);
    if (showMods) {
      simModsChangedListener = change -> createModsList(game.getSimMods());
      lastSimMods = game.getSimMods();
      createModsList(game.getSimMods());
      weakModChangeListener = new WeakInvalidationListener(simModsChangedListener);
      JavaFxUtil.addListener(game.getSimMods(), weakModChangeListener);
    } else {
      modsPane.setVisible(false);
    }
  }

  private void createTeams(ObservableMap<? extends String, ? extends List<String>> teamsList, String ratingType) {
    leaderboardService.getLeaderboards()
      .thenAccept(leaderboards -> JavaFxUtil.runLater(() -> {
        boolean hidePlayerRatings = leaderboards.stream().noneMatch(lb -> lb.getTechnicalName().equals(ratingType));
        synchronized (teamsList) {
          teamsPane.getChildren().clear();
          TeamCardController.createAndAdd(teamsList, ratingType, playerService, uiService, teamsPane, hidePlayerRatings);
          teamsPane.setPrefColumns(Math.min(teamsList.size(), maxPrefColumns));
        }
      }));
  }

  private void createModsList(ObservableMap<? extends String, ? extends String> simMods) {
    String stringSimMods = Joiner.on(System.getProperty("line.separator")).join(simMods.values());
    JavaFxUtil.runLater(() -> {
      if (simMods.isEmpty()) {
        modsPane.setVisible(false);
        return;
      }

      modsLabel.setText(stringSimMods);
      modsPane.setVisible(true);
    });
  }

  public void setShowMods(boolean showMods) {
    this.showMods = showMods;
  }

  public Node getRoot() {
    return gameTooltipRoot;
  }
}
