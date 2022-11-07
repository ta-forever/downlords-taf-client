package com.faforever.client.replay;

import com.faforever.client.fa.DemoFileInfo;
import com.faforever.client.fx.DefaultImageView;
import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapBean;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.rating.RatingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.user.UserService;
import com.faforever.client.util.RatingUtil;
import com.faforever.client.util.TimeService;
import com.faforever.client.vault.review.Review;
import com.faforever.client.vault.review.StarsController;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ReplayCardController implements Controller<Node> {

  private final ReplayService replayService;
  private final TimeService timeService;
  private final MapService mapService;
  private final RatingService ratingService;
  private final UiService uiService;
  private final UserService userService;
  private final I18n i18n;
  public Label dateLabel;
  public DefaultImageView mapThumbnailImageView;
  public Label gameTitleLabel;
  public Node replayTileRoot;
  public Label timeLabel;
  public Label modLabel;
  public Label durationLabel;
  public Label ratingLabel;
  public Label ratingTypeLabel;
  public Label qualityLabel;
  public Label numberOfReviewsLabel;
  public HBox teamsContainer;
  public Label onMapLabel;
  public Button tadaUploadButton;
  public Button watchButton;
  public StarsController starsController;
  public Button unhideButton;
  public Label visibilityLabel;
  private Replay replay;
  private final InvalidationListener reviewsChangedListener = observable -> populateReviews();
  private Consumer<Replay> onOpenDetailListener;

  public void setReplay(Replay replay) {
    this.replay = replay;

    mapThumbnailImageView.setDefaultImage(uiService.getThemeImage(UiService.UNKNOWN_MAP_IMAGE));
    Optional<MapBean> optionalMap = Optional.ofNullable(replay.getMap());
    Optional<DemoFileInfo> optionalDemoFileInfo = Optional.ofNullable(replay.getDemoFileInfo());
    String mapName = null;
    if (optionalMap.isPresent()) {
      mapName = optionalMap.get().getMapName();
    } else if (optionalDemoFileInfo.isPresent()) {
      mapName = optionalDemoFileInfo.get().getMapName();
    }
    if (mapName != null) {
      Image image = mapService.loadPreview(KnownFeaturedMod.DEFAULT.getTechnicalName(), mapName, PreviewType.MINI, 10);
      mapThumbnailImageView.setBackgroundLoadingImage(image);
      onMapLabel.setText(i18n.get("game.onMapFormat", mapName));
    }
    else {
      onMapLabel.setText(i18n.get("game.onUnknownMap"));
    }

    visibilityLabel.visibleProperty().bind(replay.replayHiddenProperty());
    visibilityLabel.managedProperty().bind(visibilityLabel.visibleProperty());

    unhideButton.visibleProperty().bind(replay.replayHiddenProperty()
        .and(replay.hostIdProperty().isEqualTo(userService.getUserId())));
    unhideButton.managedProperty().bind(unhideButton.visibleProperty());

    watchButton.disableProperty().bind(replay.replayAvailableProperty().not());

    tadaUploadButton.disableProperty().bind(replay.replayAvailableProperty().not());
    tadaUploadButton.visibleProperty().bind(Bindings.createBooleanBinding(
        () -> replayService.uploadReplayToTadaPermitted(replay) && !replay.getReplayHidden(),
        replay.replayHiddenProperty()));
    tadaUploadButton.managedProperty().bind(tadaUploadButton.visibleProperty());

    gameTitleLabel.setText(replay.getTitle());
    dateLabel.setText(timeService.asDate(replay.getStartTime()));
    timeLabel.setText(timeService.asShortTime(replay.getStartTime()));
    modLabel.setText(replay.getFeaturedMod().getDisplayName());
    double gameQuality = ratingService.calculateQuality(replay);
    if (!Double.isNaN(gameQuality)) {
      qualityLabel.setText(i18n.get("percentage", Math.round(gameQuality * 100)));
    } else {
      qualityLabel.setText(i18n.get("gameQuality.undefined"));
    }

    replay.getTeamPlayerStats().values().stream()
        .flatMapToInt(playerStats -> playerStats.stream().filter(stats -> stats.getBeforeMean() != null && stats.getBeforeDeviation() != null)
            .mapToInt(stats -> RatingUtil.getRating(stats.getBeforeMean(), stats.getBeforeDeviation())))
        .average()
        .ifPresentOrElse(averageRating -> ratingLabel.setText(i18n.number((int) averageRating)),
            () -> ratingLabel.setText("-"));

    ratingTypeLabel.setVisible(false);
    ratingTypeLabel.managedProperty().bind(ratingTypeLabel.visibleProperty());
    modLabel.managedProperty().bind(modLabel.visibleProperty());
    modLabel.visibleProperty().bind(ratingTypeLabel.visibleProperty().not());
    replay.getTeamPlayerStats().values().stream()
        .findAny()
        .flatMap(playerStatsList -> playerStatsList.stream().findAny())
        .flatMap(playerStats -> Optional.ofNullable(playerStats.getLeaderboard()))
        .ifPresent(leaderboard -> {
          ratingTypeLabel.setText(i18n.get(leaderboard.getNameKey()));
          ratingTypeLabel.setVisible(!"global".equals(leaderboard.getTechnicalName()));
        });

    Integer replayTicks = replay.getReplayTicks();
    if (replayTicks != null) {
      durationLabel.setText(timeService.shortDuration(Duration.ofMillis(replayTicks * 100)));
      // FIXME which icon was added in https://github.com/FAForever/downlords-faf-client/commit/58357c603eafead218ef7cceb8907e86c5d864b6#r40460680
//      durationLabel.getGraphic().getStyleClass().remove("duration-icon");
//      durationLabel.getGraphic().getStyleClass().remove("time-icon");
    } else {
      durationLabel.setText(Optional.ofNullable(replay.getEndTime())
          .map(endTime -> timeService.shortDuration(Duration.between(replay.getStartTime(), endTime)))
          .orElse(i18n.get("notAvailable")));
    }

    replay.getTeams()
        .forEach((id, team) -> {
          VBox teamBox = new VBox();

          String teamLabelText = id.equals("1") ? i18n.get("replay.noTeam") : i18n.get("replay.team", Integer.parseInt(id) - 1);
          Label teamLabel = new Label(teamLabelText);
          teamLabel.getStyleClass().add("replay-card-team-label");
          teamLabel.setPadding(new Insets(0, 0, 5, 0));
          teamBox.getChildren().add(teamLabel);
          team.forEach(player -> teamBox.getChildren().add(new Label(player)));

          teamsContainer.getChildren().add(teamBox);
        });

    ObservableList<Review> reviews = replay.getReviews();
    JavaFxUtil.addListener(reviews, new WeakInvalidationListener(reviewsChangedListener));
    reviewsChangedListener.invalidated(reviews);
  }

  private void populateReviews() {
    ObservableList<Review> reviews = replay.getReviews();
    JavaFxUtil.runLater(() -> {
      if (reviews.size() > 0) {
        numberOfReviewsLabel.setText(i18n.number(reviews.size()));
      } else {
        numberOfReviewsLabel.setText("");
      }

      starsController.setValue((float) reviews.stream().mapToInt(Review::getScore).average().orElse(0d));
    });
  }

  public Node getRoot() {
    return replayTileRoot;
  }

  public void setOnOpenDetailListener(Consumer<Replay> onOpenDetailListener) {
    this.onOpenDetailListener = onOpenDetailListener;
  }

  public void onShowReplayDetail() {
    onOpenDetailListener.accept(replay);
  }

  public void onWatchButtonClicked() {
    replayService.runDownloadReplay(replay);
  }

  public void onTadaUploadButtonClicked() { replayService.uploadReplayToTada(replay.getId()); }

  public void onUnhideButtonClicked(ActionEvent actionEvent) {
    replayService.unhideReplay(this.replay.getId());
    this.replay.setReplayHidden(false);
  }
}
