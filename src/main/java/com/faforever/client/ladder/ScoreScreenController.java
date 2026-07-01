package com.faforever.client.ladder;

import ch.micheljung.fxwindow.FxStage;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.ShowReplayEvent;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The post-game "Battle Report" score screen (LADDER_POINTS_DESIGN §13.4) - the primary immediate
 * reward moment. Opens as a standalone modal window; renders instantly, then fills its reward
 * bundle (Combat Score -> LP chain + medals, via {@link GameRewardsView}) asynchronously when the
 * combat service has finished compiling the game. Never blocks, never fabricates numbers: while the
 * result is pending it shows a "Compiling battle report..." state, and if no result exists it shows
 * an "unavailable" state rather than a fake LP value.
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ScoreScreenController implements Controller<Node> {

  private final LadderPointsService ladderPointsService;
  private final UiService uiService;
  private final EventBus eventBus;
  private final I18n i18n;
  private final PlayerService playerService;
  private final PreferencesService preferencesService;

  public VBox scoreScreenRoot;
  public Label titleLabel;
  public Label stateLabel;
  public VBox heroBox;
  public Label heroLpLabel;
  public Label heroSubLabel;
  public VBox rewardsContainer;

  private int gameId;
  private Window ownerWindow;
  private Stage stage;

  @Override
  public void initialize() {
    stateLabel.managedProperty().bind(stateLabel.visibleProperty());
    heroBox.managedProperty().bind(heroBox.visibleProperty());
    heroBox.setVisible(false);
  }

  @Override
  public Node getRoot() {
    return scoreScreenRoot;
  }

  public void setGameId(int gameId) {
    this.gameId = gameId;
    loadResult();
  }

  public void setOwnerWindow(Window ownerWindow) {
    this.ownerWindow = ownerWindow;
  }

  private void loadResult() {
    stateLabel.setText(i18n.get("scorescreen.state.pending"));
    stateLabel.setVisible(true);
    ladderPointsService.getGameResult(gameId)
        .thenAccept(result -> JavaFxUtil.runLater(() -> {
          rewardsContainer.getChildren().clear();
          if (result == null || result.isEmpty()) {
            stateLabel.setText(i18n.get("scorescreen.unavailable"));
            stateLabel.setVisible(true);
            heroBox.setVisible(false);
            return;
          }
          stateLabel.setVisible(false);
          showHero(result);
          rewardsContainer.getChildren().add(GameRewardsView.render(i18n, uiService, result, true));
        }))
        .exceptionally(throwable -> {
          log.warn("Could not load score screen for game {}", gameId, throwable);
          JavaFxUtil.runLater(() -> {
            stateLabel.setText(i18n.get("scorescreen.unavailable"));
            stateLabel.setVisible(true);
          });
          return null;
        });
  }

  /** The hero: the viewer's own +LP and Combat Score for this game — "what you did" front and
   * centre (§13.8). Hidden if the viewer wasn't a rated participant. */
  private void showHero(GameLadderResult result) {
    Optional<Integer> myId = playerService.getCurrentPlayer().map(Player::getId);
    Optional<LpGameBreakdown> mine = myId.flatMap(id -> result.getBreakdowns().stream()
        .filter(b -> b.getPlayerId() == id).findFirst());
    if (mine.isEmpty()) {
      heroBox.setVisible(false);
      return;
    }
    LpGameBreakdown b = mine.get();
    heroLpLabel.setText(i18n.get("lp.perGame.change", b.getLpAwarded()));
    heroSubLabel.setText(b.getDestroyedValue() != null
        ? i18n.get("scorescreen.reward.subtitle", i18n.number(b.getDestroyedValue()))
        : "");
    heroBox.setVisible(true);
  }

  /** Stop auto-opening the Battle Report (re-enable in Settings ▸ Notifications), then close. */
  public void onDontShowAgain() {
    preferencesService.getPreferences().getNotification().setBattleReportEnabled(false);
    preferencesService.storeInBackground();
    close();
  }

  /** Jump to the durable home for this result - the online replay vault detail. */
  public void onViewReplay() {
    eventBus.post(new ShowReplayEvent(gameId));
    close();
  }

  public void onClose() {
    close();
  }

  private void close() {
    if (stage != null) {
      stage.close();
    }
  }

  public void show() {
    FxStage fxStage = FxStage.create(scoreScreenRoot)
        .initOwner(ownerWindow)
        .initModality(Modality.WINDOW_MODAL)
        .withSceneFactory(uiService::createScene)
        .allowMinimize(false)
        .apply();

    stage = fxStage.getStage();
    stage.setTitle(i18n.get("scorescreen.title"));
    stage.show();
  }
}
