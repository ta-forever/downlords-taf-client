package com.faforever.client.chat;

import com.faforever.client.fx.Controller;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.i18n.I18n;
import com.faforever.client.player.Player;
import com.faforever.client.util.RatingUtil;
import com.google.common.annotations.VisibleForTesting;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserFilterController implements Controller<Node> {

  private final I18n i18n;
  private final CountryFlagService flagService;
  public MenuButton gameStatusMenu;
  public GridPane filterUserRoot;
  public TextField clanFilterField;
  public ToggleGroup gameStatusToggleGroup;
  public TextField countryFilterField;


  private final BooleanProperty filterApplied;
  @VisibleForTesting
  ChannelTabController channelTabController;
  @VisibleForTesting
  PlayerStatus playerStatusFilter;

  List<String> currentSelectedCountries;

  public UserFilterController(I18n i18n, CountryFlagService flagService) {
    this.i18n = i18n;
    this.flagService = flagService;
    this.filterApplied = new SimpleBooleanProperty(false);
  }

  void setChannelController(ChannelTabController channelTabController) {
    this.channelTabController = channelTabController;
  }

  public void initialize() {
    clanFilterField.textProperty().addListener((observable, oldValue, newValue) -> filterUsers());
    countryFilterField.textProperty().addListener(((observable, oldValue, newValue) -> filterCountry()));
    currentSelectedCountries = flagService.getCountries(null);
  }

  public void filterUsers() {
    channelTabController.setUserFilter(this::filterUser);
    filterApplied.set(
        !clanFilterField.getText().isEmpty()
            || playerStatusFilter != null
            || !countryFilterField.getText().isEmpty()
    );
  }

  private boolean filterUser(CategoryOrChatUserListItem userListItem) {
    if (userListItem.getUser() == null) {
      // The categories should display in the list independently of a filter
      return true;
    }

    ChatChannelUser user = userListItem.getUser();
    return channelTabController.isUsernameMatch(user)
        && isInClan(user)
        && isGameStatusMatch(user)
        && isCountryMatch(user);
  }

  private void filterCountry() {
    currentSelectedCountries = flagService.getCountries(countryFilterField.textProperty().get());
    filterUsers();
  }

  public BooleanProperty filterAppliedProperty() {
    return filterApplied;
  }

  public boolean isFilterApplied() {
    return filterApplied.get();
  }

  @VisibleForTesting
  boolean isInClan(ChatChannelUser chatUser) {
    if (clanFilterField.getText().isEmpty()) {
      return true;
    }

    Optional<Player> playerOptional = chatUser.getPlayer();

    if (!playerOptional.isPresent()) {
      return false;
    }

    Player player = playerOptional.get();
    String clan = player.getClan();
    if (clan == null) {
      return false;
    }

    String lowerCaseSearchString = clan.toLowerCase();
    return lowerCaseSearchString.contains(clanFilterField.getText().toLowerCase());
  }

  @VisibleForTesting
  boolean isGameStatusMatch(ChatChannelUser chatUser) {
    if (playerStatusFilter == null) {
      return true;
    }

    Optional<Player> playerOptional = chatUser.getPlayer();

    if (!playerOptional.isPresent()) {
      return false;
    }

    PlayerStatus playerStatus = playerOptional.get().getStatus();
    if (playerStatusFilter == PlayerStatus.JOINING) {
      return PlayerStatus.JOINING == playerStatus || PlayerStatus.HOSTING == playerStatus || PlayerStatus.JOINED == playerStatus || PlayerStatus.HOSTED == playerStatus;
    } else {
      return playerStatusFilter == playerStatus;
    }
  }

  boolean isCountryMatch(ChatChannelUser chatUser) {
    // Users of  'chat only' group have no country so that need to check it for empty string
    if (countryFilterField.getText().isEmpty()) {
      return true;
    }

    Optional<Player> playerOptional = chatUser.getPlayer();
    if (playerOptional.isEmpty()) {
      return false;
    }

    String country = playerOptional.get().getCountry();
    return currentSelectedCountries.contains(country);
  }

  public void onGameStatusPlaying() {
    updateGameStatusMenuText(playerStatusFilter == PlayerStatus.PLAYING ? null : PlayerStatus.PLAYING);
    filterUsers();
  }

  public void onGameStatusLobby() {
    updateGameStatusMenuText(playerStatusFilter == PlayerStatus.JOINING ? null : PlayerStatus.JOINING);
    filterUsers();
  }

  public void onGameStatusNone() {
    updateGameStatusMenuText(playerStatusFilter == PlayerStatus.IDLE ? null : PlayerStatus.IDLE);
    filterUsers();
  }

  private void updateGameStatusMenuText(PlayerStatus status) {
    playerStatusFilter = status;
    if (status == null) {
      gameStatusMenu.setText(i18n.get("game.gameStatus"));
      gameStatusToggleGroup.selectToggle(null);
      return;
    }
    gameStatusMenu.setText(i18n.get(status.getI18nKey()));
  }

  public Node getRoot() {
    return filterUserRoot;
  }
}
