package com.faforever.client.chat;

import com.faforever.client.chat.avatar.AvatarService;
import com.faforever.client.clan.Clan;
import com.faforever.client.clan.ClanService;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.galacticwar.GalacticWarService;
import com.faforever.client.game.Game;
import com.faforever.client.game.GameService;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.i18n.I18n;
import com.faforever.client.map.MapService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.player.Player;
import com.faforever.client.preferences.ChatPrefs;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import java.util.Objects;
import java.util.Optional;

import static com.faforever.client.chat.ChatColorMode.DEFAULT;
import static com.faforever.client.chat.ChatColorMode.RANDOM;
import static com.faforever.client.chat.ChatUserCategory.MODERATOR;
import static java.util.Locale.US;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUserService implements InitializingBean {

  static public final long MIN_AFK_TIMEOUT_SECONDS = 10*60;

  private final UiService uiService;
  private final MapService mapService;
  private final AvatarService avatarService;
  private final ClanService clanService;
  private final CountryFlagService countryFlagService;
  private final PreferencesService preferencesService;
  private final I18n i18n;
  private final EventBus eventBus;
  private final ObjectProvider<GameService> gameServiceProvider;
  private final ObjectProvider<GalacticWarService> galacticWarServiceProvider;
  private final com.faforever.client.ladder.LadderPointsService ladderPointsService;

  // Identity-based: ChatChannelUser.equals is username-only, but one instance exists
  // per (username, channel). Username equality would collapse a user in N channels into
  // a single tracked instance, leaving cells in the other channels' user lists with
  // stale icons after an icon-mode change.
  private final Set<ChatChannelUser> trackedUsers =
      java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

  /** Icon mode: "avatar" for normal avatars, "none" for no icons, or a galaxy technical name for GW rank icons. */
  public static final String ICON_MODE_AVATAR = "avatar";
  public static final String ICON_MODE_NONE = "none";
  private final StringProperty iconMode = new SimpleStringProperty(ICON_MODE_AVATAR);
  private boolean inGwOverride = false;
  private boolean gameListenerRegistered = false;

  public StringProperty iconModeProperty() { return iconMode; }
  public String getIconMode() { return iconMode.get(); }

  /** Called by the combobox when the user manually selects a mode. Always persists. */
  public void setIconModeManual(String mode) {
    iconMode.set(mode);
    preferencesService.getPreferences().getChat().setIconMode(mode);
    preferencesService.storeInBackground();
  }

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
    // Load persisted setting
    String persisted = preferencesService.getPreferences().getChat().getIconMode();
    if (persisted != null) {
      iconMode.set(persisted);
    }
    iconMode.addListener((obs, oldVal, newVal) -> repopulateAllAvatars());
  }

  private void ensureGameListener() {
    if (!gameListenerRegistered) {
      gameListenerRegistered = true;
      JavaFxUtil.addListener(gameServiceProvider.getObject().getCurrentGameProperty(), (obs, oldGame, newGame) -> {
        String newGwGalaxy = extractGalaxy(newGame);
        String oldGwGalaxy = extractGalaxy(oldGame);
        if (newGwGalaxy != null && !newGwGalaxy.equals(oldGwGalaxy)) {
          // Entering a GW game: temporarily override without persisting
          inGwOverride = true;
          JavaFxUtil.runLater(() -> iconMode.set(newGwGalaxy));
        } else if (newGwGalaxy == null && oldGwGalaxy != null) {
          // Leaving a GW game: restore persisted setting
          inGwOverride = false;
          String persisted = preferencesService.getPreferences().getChat().getIconMode();
          JavaFxUtil.runLater(() -> iconMode.set(persisted != null ? persisted : ICON_MODE_AVATAR));
        }
      });
    }
  }

  private static String extractGalaxy(Game game) {
    if (game == null) return null;
    String planetName = game.getGalacticWarPlanetName();
    if (planetName == null || planetName.isBlank()) return null;
    int slashIdx = planetName.indexOf('/');
    return slashIdx >= 0 ? planetName.substring(0, slashIdx) : null;
  }

  private void repopulateAllAvatars() {
    synchronized (trackedUsers) {
      trackedUsers.forEach(this::populateAvatar);
    }
  }

  private void populateClan(ChatChannelUser chatChannelUser) {
    if (chatChannelUser.isDisplayed()) {
      chatChannelUser.getPlayer().ifPresent(player -> {
        if (player.getClan() != null) {
          clanService.getClanByTag(player.getClan())
              .thenAccept(optionalClan -> JavaFxUtil.runLater(() -> {
                    Clan clan = optionalClan.orElse(null);
                    chatChannelUser.setClan(clan);
                  })
              );
        } else {
          chatChannelUser.setClan(null);
        }
      });
    } else {
      chatChannelUser.setClan(null);
    }
  }

  private void populateAvatar(ChatChannelUser chatChannelUser) {
    if (!chatChannelUser.isDisplayed()) {
      chatChannelUser.setAvatar(null);
      return;
    }
    chatChannelUser.getPlayer().ifPresent(player -> {
      // In normal avatar mode a chosen medal takes the avatar slot (over the regular avatar); GW /
      // none modes keep their own icons. Resolving the medal here (not in the cell) means a medal
      // change refreshes the bound cell immediately on repopulate.
      if (ICON_MODE_AVATAR.equals(getIconMode()) && player.getId() > 0) {
        ladderPointsService.getFeaturedMedalCached(player.getId())
            .thenAccept(medal -> JavaFxUtil.runLater(() -> {
              if (!chatChannelUser.isDisplayed() || !ICON_MODE_AVATAR.equals(getIconMode())) {
                return;
              }
              if (medal.isPresent()) {
                chatChannelUser.setAvatar(uiService.getThemeImage(
                    com.faforever.client.ladder.LadderUiUtil.medalIconPath(medal.get().getCode())));
                chatChannelUser.setAvatarTooltipText(com.faforever.client.ladder.LadderUiUtil
                    .medalAvatarTooltip(i18n, medal.get().getCode(), medal.get().getCount()));
              } else {
                chatChannelUser.setAvatar(getIconForMode(player));
                chatChannelUser.setAvatarTooltipText(player.getAvatarTooltip());
              }
            }));
      } else {
        Image icon = getIconForMode(player);
        JavaFxUtil.runLater(() -> {
          chatChannelUser.setAvatar(icon);
          chatChannelUser.setAvatarTooltipText(player.getAvatarTooltip());
        });
      }
    });
  }

  /** Refresh the avatar slot (incl. medal-as-avatar) for one player across all channels — called
   * when their featured medal changes so the chat list updates without waiting for a rebind. */
  @com.google.common.eventbus.Subscribe
  public void onFeaturedMedalChanged(com.faforever.client.ladder.FeaturedMedalChangedEvent event) {
    synchronized (trackedUsers) {
      trackedUsers.stream()
          .filter(u -> u.getPlayer().map(p -> p.getId() == event.getPlayerId()).orElse(false))
          .forEach(this::populateAvatar);
    }
  }

  private Image getIconForMode(Player player) {
    String mode = getIconMode();
    if (ICON_MODE_NONE.equals(mode)) {
      return null;
    }
    if (ICON_MODE_AVATAR.equals(mode)) {
      if (!Strings.isNullOrEmpty(player.getAvatarUrl())) {
        return avatarService.loadAvatar(player.getAvatarUrl());
      }
      return null;
    }
    // Galaxy technical name: show GW rank icon
    return galacticWarServiceProvider.getObject().getMedalImageForGalaxy(player.getId(), mode);
  }

  private void populateCountry(ChatChannelUser chatChannelUser) {
    if (chatChannelUser.isDisplayed()) {
      chatChannelUser.getPlayer()
          .ifPresent(player -> {
            Optional<Image> countryFlag = countryFlagService.loadCountryFlag(player.getCountry());
            JavaFxUtil.runLater(() -> {
              chatChannelUser.setCountryFlag(countryFlag.orElse(null));
              chatChannelUser.setCountryName(i18n.getCountryNameLocalized(player.getCountry()));
            });
          });
    } else {
      chatChannelUser.setCountryFlag(null);
      chatChannelUser.setCountryName(null);
    }
  }

  private void populateGameImages(ChatChannelUser chatChannelUser) {
    if (chatChannelUser.isDisplayed()) {
      chatChannelUser.getPlayer()
          .ifPresent(player -> setGameImages(chatChannelUser, player));
    } else {
      chatChannelUser.setStatusTooltipText(null);
      chatChannelUser.setGameStatusImage(null);
      chatChannelUser.setMapImage(null);
      chatChannelUser.setAfkImage(null);
    }
  }

  private void populateColor(ChatChannelUser chatChannelUser) {
    ChatPrefs chatPrefs = preferencesService.getPreferences().getChat();
    Optional<Player> optionalPlayer = chatChannelUser.getPlayer();
    String lowercaseUsername = chatChannelUser.getUsername().toLowerCase(US);

    Color color = null;
    if (chatPrefs.getChatColorMode() == null) {
      chatPrefs.setChatColorMode(DEFAULT);
    }

    if (chatPrefs.getChatColorMode() == DEFAULT && chatPrefs.getUserToColor().containsKey(lowercaseUsername)) {
      color = chatPrefs.getUserToColor().get(lowercaseUsername);
    } else if (chatPrefs.getChatColorMode() == DEFAULT && chatChannelUser.isModerator() && chatPrefs.getGroupToColor().containsKey(MODERATOR)) {
      color = chatPrefs.getGroupToColor().get(MODERATOR);
    } else if (chatPrefs.getChatColorMode() == DEFAULT && optionalPlayer.isPresent()) {
      ChatUserCategory chatUserCategory = optionalPlayer.map(player -> switch (player.getSocialStatus()) {
        case FRIEND -> ChatUserCategory.FRIEND;
        case FOE -> ChatUserCategory.FOE;
        default -> ChatUserCategory.OTHER;
      }).orElse(ChatUserCategory.CHAT_ONLY);
      color = chatPrefs.getGroupToColor().get(chatUserCategory);
    } else if (chatPrefs.getChatColorMode() == RANDOM) {
      color = ColorGeneratorUtil.generateRandomColor(lowercaseUsername.hashCode());
    }
    chatChannelUser.setColor(color);
  }

  private void setGameImages(ChatChannelUser chatChannelUser, Player player) {
    PlayerStatus status = player.getStatus();
    Image playerStatusImage = switch (status) {
      case HOSTING -> uiService.getThemeImage(UiService.CHAT_LIST_STATUS_HOSTING);
      case JOINING -> uiService.getThemeImage(UiService.CHAT_LIST_STATUS_JOINING);
      case HOSTED -> uiService.getThemeImage(UiService.CHAT_LIST_STATUS_HOSTED);
      case JOINED -> uiService.getThemeImage(UiService.CHAT_LIST_STATUS_JOINED);
      case PLAYING -> uiService.getThemeImage(UiService.CHAT_LIST_STATUS_PLAYING);
      default -> null;
    };

    Image mapImage;
    if (status != PlayerStatus.IDLE && player.getGame() != null) {
      String modTechnical = player.getGame().getFeaturedMod();
      String mapName = player.getGame().getMapName();
      mapImage = mapService.loadPreview(modTechnical, mapName, PreviewType.MINI, 10);
    } else {
      mapImage = null;
    }

    Image afkImage;
    if ((status == PlayerStatus.IDLE || status == PlayerStatus.HOSTING || status == PlayerStatus.JOINING) &&
        player.getAfkSeconds() >= MIN_AFK_TIMEOUT_SECONDS) {
      afkImage = uiService.getThemeImage(UiService.CHAT_LIST_STATUS_AFK);
    } else {
      afkImage = null;
    }

    JavaFxUtil.runLater(() -> {
      chatChannelUser.setStatusTooltipText(i18n.get(status.getI18nKey()));
      chatChannelUser.setGameStatusImage(playerStatusImage);
      if (player.getGame() != null && player.getGame().getReplayDelaySeconds() >= 0) {
        chatChannelUser.setMapImage(mapImage);
      }
      chatChannelUser.setAfkImage(afkImage);
    });
  }

  public void associatePlayerToChatUser(ChatChannelUser chatChannelUser, Player player) {
    if (player != null && chatChannelUser.getPlayer().filter(userPlayer -> userPlayer.getUsername().equals(player.getUsername())).isEmpty()) {
      ensureGameListener();
      chatChannelUser.setPlayer(player);
      synchronized (trackedUsers) { trackedUsers.add(chatChannelUser); }
      populateGameImages(chatChannelUser);
      populateClan(chatChannelUser);
      populateCountry(chatChannelUser);
      populateAvatar(chatChannelUser);
      populateColor(chatChannelUser);
      addListeners(chatChannelUser);
    } else if (player == null) {
      synchronized (trackedUsers) { trackedUsers.remove(chatChannelUser); }
      chatChannelUser.removeListeners();
      chatChannelUser.setPlayer(null);
      chatChannelUser.setStatusTooltipText(null);
      chatChannelUser.setGameStatusImage(null);
      chatChannelUser.setMapImage(null);
      chatChannelUser.setAfkImage(null);
      chatChannelUser.setCountryFlag(null);
      chatChannelUser.setCountryName(null);
      chatChannelUser.setClan(null);
      chatChannelUser.setAvatar(null);
      populateColor(chatChannelUser);
    }
  }

  private void addListeners(ChatChannelUser chatChannelUser) {
    chatChannelUser.setAvatarChangeListener((observable, oldValue, newValue) -> {
      if (!Objects.equals(oldValue, newValue)) {
        populateAvatar(chatChannelUser);
      }
    });
    chatChannelUser.setClanTagChangeListener((observable, oldValue, newValue) -> {
      if (!Objects.equals(oldValue, newValue)) {
        populateClan(chatChannelUser);
      }
    });
    chatChannelUser.setCountryChangeListener((observable, oldValue, newValue) -> {
      if (!Objects.equals(oldValue, newValue)) {
        populateCountry(chatChannelUser);
      }
    });
    chatChannelUser.setSocialStatusChangeListener((observable, oldValue, newValue) -> {
      if (!Objects.equals(oldValue, newValue)) {
        populateColor(chatChannelUser);
      }
    });
    chatChannelUser.setGameStatusChangeListener((observable, oldValue, newValue) -> {
      if (!Objects.equals(oldValue, newValue)) {
        populateGameImages(chatChannelUser);
      }
    });
    chatChannelUser.setDisplayedChangeListener((observable, oldValue, newValue) -> {
      if (oldValue != newValue) {
        if (newValue) {
          populateGameImages(chatChannelUser);
          populateClan(chatChannelUser);
          populateCountry(chatChannelUser);
          populateAvatar(chatChannelUser);
          populateColor(chatChannelUser);
        } else {
          chatChannelUser.setStatusTooltipText(null);
          chatChannelUser.setGameStatusImage(null);
          chatChannelUser.setMapImage(null);
          chatChannelUser.setAfkImage(null);
          chatChannelUser.setCountryFlag(null);
          chatChannelUser.setCountryName(null);
          chatChannelUser.setClan(null);
          chatChannelUser.setAvatar(null);
        }
      }
    });
    chatChannelUser.setAfkSecondsChangeListener((observable, oldValue, newValue) -> {
      boolean wasAfk = oldValue.longValue() >= MIN_AFK_TIMEOUT_SECONDS;
      boolean isAfk = newValue.longValue() >= MIN_AFK_TIMEOUT_SECONDS;
      if (wasAfk != isAfk) {
        populateGameImages(chatChannelUser);
      }
    });
  }
}


