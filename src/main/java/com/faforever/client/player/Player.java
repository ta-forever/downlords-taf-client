package com.faforever.client.player;

import com.faforever.client.chat.ChatChannelUser;
import com.faforever.client.chat.avatar.AvatarBean;
import com.faforever.client.game.Game;
import com.faforever.client.game.PlayerStatus;
import com.faforever.client.leaderboard.LeaderboardRating;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.faforever.client.player.SocialStatus.OTHER;

/**
 * Represents a player with username, clan, country, friend/foe flag and so on.
 */
public class Player {

  private final IntegerProperty id;
  private final StringProperty username;
  private final StringProperty alias;
  private final StringProperty clan;
  private final StringProperty country;
  private final StringProperty avatarUrl;
  private final StringProperty avatarTooltip;
  private final ObjectProperty<SocialStatus> socialStatus;
  private final MapProperty<String, LeaderboardRating> leaderboardRatings;
  private final ObjectProperty<Game> game;
  private final ObjectProperty<PlayerStatus> status;
  private final ObservableSet<ChatChannelUser> chatChannelUsers;
  private final IntegerProperty numberOfGames;
  private final LongProperty afkSeconds;
  private final ObjectProperty<Instant> idleSince;
  private final ObservableList<NameRecord> names;
  private final IntegerProperty currentGameUid;

  public Player(com.faforever.client.remote.domain.Player player) {
    this();

    username.set(player.getLogin());
    alias.set(player.getLogin());
    clan.set(player.getClan());
    country.set(player.getCountry());

    if (player.getAvatar() != null) {
      avatarTooltip.set(player.getAvatar().getTooltip());
      avatarUrl.set(player.getAvatar().getUrl());
    }
  }

  private Player() {
    id = new SimpleIntegerProperty();
    username = new SimpleStringProperty();
    alias = new SimpleStringProperty();
    clan = new SimpleStringProperty();
    country = new SimpleStringProperty();
    avatarUrl = new SimpleStringProperty();
    avatarTooltip = new SimpleStringProperty();
    leaderboardRatings = new SimpleMapProperty<>(FXCollections.emptyObservableMap());
    status = new SimpleObjectProperty<>(PlayerStatus.IDLE);
    chatChannelUsers = FXCollections.observableSet();
    game = new SimpleObjectProperty<>();
    numberOfGames = new SimpleIntegerProperty();
    socialStatus = new SimpleObjectProperty<>(OTHER);
    afkSeconds = new SimpleLongProperty(0);       // as indicated by server
    idleSince = new SimpleObjectProperty<>(Instant.now());  // at time when afkSeconds was set to 0
    names = FXCollections.observableArrayList();
    currentGameUid = new SimpleIntegerProperty(-1);

    afkSeconds.addListener((obs, oldValue, newValue) -> {
      if (oldValue.longValue() != 0 && newValue.longValue() == 0) {
        idleSince.set(Instant.now());
      }
    });
  }

  public Player(String username) {
    this();
    this.username.set(username);
    this.alias.set(username);
  }

  public static Player fromDto(com.faforever.client.api.dto.Player dto) {
    // com.faforever.client.remote.domain.Player is populated by server
    // com.faforever.client.api.dto.Player is populated by API
    // com.faforever.client.player.Player is us
    Player player = new Player(dto.getLogin());
    player.setId(Integer.parseInt(dto.getId()));
    player.setUsername(dto.getLogin());
    player.setAlias(dto.getLogin());
    if (dto.getNames() != null) {
      player.getNames().addAll(dto.getNames().stream().map(NameRecord::fromDto).collect(Collectors.toList()));
    }
    return player;
  }

  public ObservableList<NameRecord> getNames() {
    return names;
  }

  public SocialStatus getSocialStatus() {
    return socialStatus.get();
  }

  public void setSocialStatus(SocialStatus socialStatus) {
    this.socialStatus.set(socialStatus);
  }

  public ObjectProperty<SocialStatus> socialStatusProperty() {
    return socialStatus;
  }

  public int getId() {
    return id.get();
  }

  public void setId(int id) {
    this.id.set(id);
  }

  public IntegerProperty idProperty() {
    return id;
  }

  public int getNumberOfGames() {
    return numberOfGames.get();
  }

  public void setNumberOfGames(int numberOfGames) {
    this.numberOfGames.set(numberOfGames);
  }

  public IntegerProperty numberOfGamesProperty() {
    return numberOfGames;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id.get(), username.get());
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null
        && (obj.getClass() == Player.class)
        && (getId() == ((Player) obj).getId() && getId() != 0 ||
        getUsername().equalsIgnoreCase(((Player) obj).getUsername()));
  }

  public String getUsername() {
    return username.get();
  }

  public void setUsername(String username) {
    this.username.set(username);
  }

  public StringProperty usernameProperty() {
    return username;
  }

  public String getAlias() {
    return alias.get();
  }

  public void setAlias(String alias) {
    this.alias.set(alias);
  }

  public StringProperty aliasProperty() {
    return alias;
  }

  public String getClan() {
    return clan.get();
  }

  public void setClan(String clan) {
    this.clan.set(clan);
  }

  public StringProperty clanProperty() {
    return clan;
  }

  public String getCountry() {
    return country.get();
  }

  public void setCountry(String country) {
    this.country.set(country);
  }

  public StringProperty countryProperty() {
    return country;
  }

  @SneakyThrows
  public void setAvatar(AvatarBean avatar) {
    if (avatar != null) {
      if (avatar.getUrl() != null) {
        setAvatarUrl(avatar.getUrl().toExternalForm());
      } else {
        setAvatarUrl(null);
      }
      setAvatarTooltip(avatar.getDescription());
    } else {
      setAvatarUrl(null);
      setAvatarTooltip(null);
    }
  }

  public String getAvatarUrl() {
    return avatarUrl.get();
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl.set(avatarUrl);
  }

  public StringProperty avatarUrlProperty() {
    return avatarUrl;
  }

  public String getAvatarTooltip() {
    return avatarTooltip.get();
  }

  public void setAvatarTooltip(String avatarTooltip) {
    this.avatarTooltip.set(avatarTooltip);
  }

  public StringProperty avatarTooltipProperty() {
    return avatarTooltip;
  }

  @NotNull
  public Map<String, LeaderboardRating> getLeaderboardRatings() {
    return leaderboardRatings.get();
  }

  public void setLeaderboardRatings(Map<String, LeaderboardRating> leaderboardRatings) {
    this.leaderboardRatings.set(FXCollections.observableMap(leaderboardRatings));
  }

  public MapProperty<String, LeaderboardRating> leaderboardRatingMapProperty() {
    return leaderboardRatings;
  }

  public PlayerStatus getStatus() {
    return status.get();
  }

  public void setStatus(PlayerStatus status) {
    this.status.set(status);
  }

  public ObjectProperty<PlayerStatus> statusProperty() {
    return status;
  }

  public Game getGame() {
    return game.get();
  }

  public void setGame(Game game) {
    this.game.set(game);
  }

  public ObjectProperty<Game> gameProperty() {
    return game;
  }

  public long getAfkSeconds() {
    return afkSeconds.get();
  }

  public void setAfkSeconds(long seconds) { afkSeconds.set(seconds); }

  public LongProperty afkSecondsProperty() {
    return afkSeconds;
  }

  public Instant getIdleSince() {
    return idleSince.get();
  }

  public ReadOnlyObjectProperty<Instant> idleSinceProperty() {
    return idleSince;
  }

  public ObservableSet<ChatChannelUser> getChatChannelUsers() {
    return chatChannelUsers;
  }

  public void updateFromDto(com.faforever.client.remote.domain.Player player) {
    // com.faforever.client.remote.domain.Player is populated by server
    // com.faforever.client.api.dto.Player is populated by API
    // com.faforever.client.player.Player is us
    setId(player.getId());
    setClan(player.getClan());
    setCountry(player.getCountry());
    setStatus(PlayerStatus.fromDto(player.getState()));
    setCurrentGameUid(player.getCurrentGameUid());
    setAlias(player.getAlias()==null ? player.getLogin() : player.getAlias());

    if (player.getAfkSeconds() != null) {
      this.afkSeconds.set(player.getAfkSeconds());
    }

    if (player.getRatings() != null) {
      Map<String, LeaderboardRating> ratingMap = new HashMap<>();
      player.getRatings().forEach((key, value) -> ratingMap.put(key, LeaderboardRating.fromDto(value)));
      setLeaderboardRatings(ratingMap);
    }

    setNumberOfGames(player.getNumberOfGames());
    if (player.getAvatar() != null) {
      setAvatarUrl(player.getAvatar().getUrl());
      setAvatarTooltip(player.getAvatar().getTooltip());
    }
  }

  public int getCurrentGameUid() {
    return currentGameUid.get();
  }

  public void setCurrentGameUid(int uid) {
    this.currentGameUid.set(uid);
  }

  public IntegerProperty currentGameUidProperty() {
    return currentGameUid;
  }
}
