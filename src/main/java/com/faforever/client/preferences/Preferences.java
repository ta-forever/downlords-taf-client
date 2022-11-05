package com.faforever.client.preferences;

import com.faforever.client.chat.RatingMetric;
import com.faforever.client.game.GamesTilesContainerController.TilesSortingOrder;
import com.faforever.client.game.KnownFeaturedMod;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.control.TableColumn.SortType;
import javafx.util.Pair;
import lombok.Getter;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;

import static javafx.collections.FXCollections.observableArrayList;

public class Preferences {

  public static final String DEFAULT_THEME_NAME = "default";

  private final WindowPrefs mainWindow;
  private final GeneratorPrefs generator;
  private final ListProperty<TotalAnnihilationPrefs> totalAnnihilation;
  private final LoginPrefs login;
  private final ChatPrefs chat;
  private final NotificationsPrefs notification;
  private final LocalizationPrefs localization;
  private final LastGamePrefs lastGame;
  private final MatchmakerPrefs matchmaker;
  private final NewsPrefs news;
  private final DeveloperPrefs developer;
  private final VaultPrefs vault;
  private final StringProperty themeName;
  private final BooleanProperty preReleaseCheckEnabled;
  private final BooleanProperty showPasswordProtectedGames;
  private final BooleanProperty showModdedGames;
  // TA options @todo they need to be in TotalAnnilationPreferences()
  private final BooleanProperty forceRelayEnabled;
  private final BooleanProperty proactiveResendEnabled;
  private final ObjectProperty<MaxPacketSizeOption> maxPacketSizeOption;
  private final BooleanProperty suppressReplayChatEnabled;
  private final BooleanProperty ircIntegrationEnabled;
  private final BooleanProperty autoLaunchOnHostEnabled;
  private final BooleanProperty autoLaunchOnJoinEnabled;
  private final BooleanProperty autoRehostEnabled;
  private final BooleanProperty autoJoinEnabled;
  private final BooleanProperty requireUacEnabled;
  // end TA options
  private final ListProperty<String> ignoredNotifications;
  private final StringProperty gamesViewMode;
  private final StringProperty lastPlayTab; // matchmaker or custom
  private final ListProperty<Pair<String, SortType>> gameListSorting;
  private final ObjectProperty<TilesSortingOrder> gameTileSortingOrder;
  private final ObjectProperty<UnitDataBaseType> unitDataBaseType;
  private final MapProperty<URI, ArrayList<HttpCookie>> storedCookies;
  private final BooleanProperty disallowJoinsViaDiscord;
  private final BooleanProperty showGameDetailsSidePane;
  private final BooleanProperty advancedIceLogEnabled;
  private final IntegerProperty cacheLifeTimeInDays;
  private final BooleanProperty gameDataCacheActivated;
  private final BooleanProperty gameDataMapManagementEnabled;
  private final BooleanProperty gameDataPromptDownloadActivated;
  private final BooleanProperty gameDataMapDownloadKeepVersionTag;
  private final BooleanProperty debugLogEnabled;
  private final ObjectProperty<TadaIntegrationOption> tadaIntegrationOption;
  private final ObjectProperty<AskAlwaysOrNever> featuredModRevertOption;
  private final ObjectProperty<RatingMetric> userInfoRatingMetric;
  private final ObjectProperty<AutoUploadLogsOption> autoUploadLogsOption;
  private final StringProperty lastLeaderboardSelection;
  private final BooleanProperty lastLeaderboardFriendsOnlySelection;
  private final ListProperty<String> LeaderBoardsSelectionFilter;
  private final BooleanProperty gameRoomPopout;

  public Preferences() {
    gameTileSortingOrder = new SimpleObjectProperty<>(TilesSortingOrder.PLAYER_DES);
    chat = new ChatPrefs();
    login = new LoginPrefs();
    generator = new GeneratorPrefs();

    localization = new LocalizationPrefs();
    lastGame = new LastGamePrefs();
    mainWindow = new WindowPrefs();
    totalAnnihilation = new SimpleListProperty<>(FXCollections.observableArrayList());
    themeName = new SimpleStringProperty(DEFAULT_THEME_NAME);
    ignoredNotifications = new SimpleListProperty<>(observableArrayList());
    notification = new NotificationsPrefs();
    matchmaker = new MatchmakerPrefs();
    gamesViewMode = new SimpleStringProperty();
    lastPlayTab = new SimpleStringProperty();
    news = new NewsPrefs();
    developer = new DeveloperPrefs();
    gameListSorting = new SimpleListProperty<>(observableArrayList());
    vault = new VaultPrefs();
    unitDataBaseType = new SimpleObjectProperty<>(UnitDataBaseType.SPOOKY);
    storedCookies = new SimpleMapProperty<>(FXCollections.observableHashMap());
    showPasswordProtectedGames = new SimpleBooleanProperty(true);
    showModdedGames = new SimpleBooleanProperty(true);
    forceRelayEnabled = new SimpleBooleanProperty(false);
    proactiveResendEnabled = new SimpleBooleanProperty(false);
    maxPacketSizeOption = new SimpleObjectProperty<>(MaxPacketSizeOption.NORMAL);
    suppressReplayChatEnabled = new SimpleBooleanProperty(false);
    ircIntegrationEnabled = new SimpleBooleanProperty(true);
    autoLaunchOnHostEnabled = new SimpleBooleanProperty(false);
    autoLaunchOnJoinEnabled = new SimpleBooleanProperty(true);
    autoRehostEnabled = new SimpleBooleanProperty(false);
    autoJoinEnabled = new SimpleBooleanProperty(false);
    requireUacEnabled = new SimpleBooleanProperty(true);
    disallowJoinsViaDiscord = new SimpleBooleanProperty();
    showGameDetailsSidePane = new SimpleBooleanProperty(false);
    advancedIceLogEnabled = new SimpleBooleanProperty(false);
    preReleaseCheckEnabled = new SimpleBooleanProperty(false);
    cacheLifeTimeInDays = new SimpleIntegerProperty(30);
    gameDataCacheActivated = new SimpleBooleanProperty(false);
    gameDataMapManagementEnabled = new SimpleBooleanProperty(true);
    gameDataPromptDownloadActivated = new SimpleBooleanProperty(true);
    gameDataMapDownloadKeepVersionTag = new SimpleBooleanProperty(false);
    debugLogEnabled = new SimpleBooleanProperty(false);
    autoUploadLogsOption = new SimpleObjectProperty<>(AutoUploadLogsOption.ASK);
    tadaIntegrationOption = new SimpleObjectProperty<>(TadaIntegrationOption.BROWSER);
    featuredModRevertOption = new SimpleObjectProperty<>(AskAlwaysOrNever.ASK);
    userInfoRatingMetric = new SimpleObjectProperty<>(RatingMetric.TRUESKILL);
    lastLeaderboardSelection = new SimpleStringProperty("global");
    lastLeaderboardFriendsOnlySelection = new SimpleBooleanProperty(false);
    LeaderBoardsSelectionFilter = new SimpleListProperty<>(observableArrayList());
    gameRoomPopout = new SimpleBooleanProperty(true);
  }

  public VaultPrefs getVault() {
    return vault;
  }

  public TilesSortingOrder getGameTileSortingOrder() {
    return gameTileSortingOrder.get();
  }

  public void setGameTileSortingOrder(TilesSortingOrder gameTileTilesSortingOrder) {
    this.gameTileSortingOrder.set(gameTileTilesSortingOrder);
  }

  public ObjectProperty<TilesSortingOrder> gameTileSortingOrderProperty() {
    return gameTileSortingOrder;
  }

  public BooleanProperty showPasswordProtectedGamesProperty() {
    return showPasswordProtectedGames;
  }

  public BooleanProperty showModdedGamesProperty() {
    return showModdedGames;
  }

  public String getGamesViewMode() {
    return gamesViewMode.get();
  }

  public void setGamesViewMode(String gamesViewMode) {
    this.gamesViewMode.set(gamesViewMode);
  }

  public StringProperty gamesViewModeProperty() {
    return gamesViewMode;
  }

  public String getLastPlayTab() {
    return lastPlayTab.get();
  }

  public void setLastPlayTab(String lastPlayTab) {
    this.lastPlayTab.set(lastPlayTab);
  }

  public StringProperty lastPlayTabProperty() {
    return lastPlayTab;
  }

  public WindowPrefs getMainWindow() {
    return mainWindow;
  }

  public LocalizationPrefs getLocalization() {
    return localization;
  }

  public ObservableList<TotalAnnihilationPrefs> getTotalAnnihilationAllMods() { return totalAnnihilation; }

  public BooleanProperty getForceRelayEnabledProperty() {
    return forceRelayEnabled;
  }

  public BooleanProperty getProactiveResendEnabledProperty() {
    return proactiveResendEnabled;
  }

  public MaxPacketSizeOption getMaxPacketSizeOption() {
    return maxPacketSizeOption.get();
  }
  public void setMaxPacketSizeOption(MaxPacketSizeOption option) { this.maxPacketSizeOption.set(option); }
  public ObjectProperty<MaxPacketSizeOption> maxPacketSizeOptionProperty() {
    return maxPacketSizeOption;
  }

  public BooleanProperty getSuppressReplayChatEnabledProperty() {
    return suppressReplayChatEnabled;
  }

  public BooleanProperty getIrcIntegrationEnabledProperty() { return ircIntegrationEnabled; }

  public BooleanProperty getAutoLaunchOnHostEnabledProperty() { return autoLaunchOnHostEnabled; }

  public BooleanProperty getAutoLaunchOnJoinEnabledProperty() {  return autoLaunchOnJoinEnabled; }

  public BooleanProperty getAutoRehostEnabledProperty() {
    return autoRehostEnabled;
  }

  public BooleanProperty getAutoJoinEnabledProperty() {
    return autoJoinEnabled;
  }

  public BooleanProperty getRequireUacEnabledProperty() {
    return requireUacEnabled;
  }

  public boolean getForceRelayEnabled() {
    return forceRelayEnabled.get();
  }

  public boolean getProactiveResendEnabled() {
    return proactiveResendEnabled.get();
  }

  public boolean getSuppressReplayChatEnabled() {
    return suppressReplayChatEnabled.get();
  }

  public boolean getIrcIntegrationEnabled() {
    return false; //return ircIntegrationEnabled.get();
  }

  public boolean getAutoLaunchOnHostEnabled() {
    return autoLaunchOnHostEnabled.get();
  }

  public boolean getAutoLaunchOnJoinEnabled() {
    return autoLaunchOnJoinEnabled.get();
  }

  public boolean getAutoRehostEnabled() {
    return autoRehostEnabled.get();
  }

  public boolean getAutoJoinEnabled() {
    return autoJoinEnabled.get();
  }

  public boolean getRequireUacEnabled() {
    return requireUacEnabled.get();
  }

  public TotalAnnihilationPrefs getTotalAnnihilation(String modTechnical) {
    return setTotalAnnihilation(modTechnical, null, null, AskAlwaysOrNever.ASK);
  }

  public TotalAnnihilationPrefs setTotalAnnihilation(String modTechnical, Path installedExePath,
                                                     String commandLineOptions, AskAlwaysOrNever autoUpdateEnable) {
    String baseGameName = modTechnical;
    KnownFeaturedMod kfm = KnownFeaturedMod.fromString(modTechnical);
    if (kfm != null) {
      baseGameName = kfm.getBaseGameName();
    }

    for (TotalAnnihilationPrefs pref: totalAnnihilation) {
      if (pref.getBaseGameName().equals(baseGameName)) {
        if (installedExePath != null && commandLineOptions != null) {
          pref.setInstalledExePath(installedExePath);
          pref.setCommandLineOptions(commandLineOptions);
          pref.setAutoUpdateEnable(autoUpdateEnable);
        }
        return pref;
      }
    }
    TotalAnnihilationPrefs pref = new TotalAnnihilationPrefs(baseGameName, installedExePath, commandLineOptions,
        AskAlwaysOrNever.NEVER);
    totalAnnihilation.add(pref);
    return pref;
  }

  public LoginPrefs getLogin() {
    return login;
  }

  public ChatPrefs getChat() {
    return chat;
  }

  public NotificationsPrefs getNotification() {
    return notification;
  }

  public String getThemeName() {
    return themeName.get();
  }

  public void setThemeName(String themeName) {
    this.themeName.set(themeName);
  }

  public StringProperty themeNameProperty() {
    return themeName;
  }

  public ObservableList<String> getIgnoredNotifications() {
    return ignoredNotifications.get();
  }

  public void setIgnoredNotifications(ObservableList<String> ignoredNotifications) {
    this.ignoredNotifications.set(ignoredNotifications);
  }

  public ListProperty<String> ignoredNotificationsProperty() {
    return ignoredNotifications;
  }

  public MatchmakerPrefs getMatchmaker() {
    return matchmaker;
  }

  public NewsPrefs getNews() {
    return news;
  }

  public DeveloperPrefs getDeveloper() {
    return developer;
  }

  public ObservableList<Pair<String, SortType>> getGameListSorting() {
    return gameListSorting.get();
  }

  public UnitDataBaseType getUnitDataBaseType() {
    return unitDataBaseType.get();
  }

  public void setUnitDataBaseType(UnitDataBaseType unitDataBaseType) {
    this.unitDataBaseType.set(unitDataBaseType);
  }

  public ObjectProperty<UnitDataBaseType> unitDataBaseTypeProperty() {
    return unitDataBaseType;
  }

  public ObservableMap<URI, ArrayList<HttpCookie>> getStoredCookies() {
    return storedCookies.get();
  }

  public boolean isDisallowJoinsViaDiscord() {
    return disallowJoinsViaDiscord.get();
  }

  public void setDisallowJoinsViaDiscord(boolean disallowJoinsViaDiscord) {
    this.disallowJoinsViaDiscord.set(disallowJoinsViaDiscord);
  }

  public BooleanProperty disallowJoinsViaDiscordProperty() {
    return disallowJoinsViaDiscord;
  }

  public boolean isShowGameDetailsSidePane() {
    return showGameDetailsSidePane.get();
  }

  public void setShowGameDetailsSidePane(boolean showGameDetailsSidePane) {
    this.showGameDetailsSidePane.set(showGameDetailsSidePane);
  }

  public boolean isAdvancedIceLogEnabled() {
    return advancedIceLogEnabled.get();
  }

  public void setAdvancedIceLogEnabled(boolean advancedIceLogEnabled) {
    this.advancedIceLogEnabled.set(advancedIceLogEnabled);
  }

  public BooleanProperty advancedIceLogEnabledProperty() {
    return advancedIceLogEnabled;
  }

  public BooleanProperty showGameDetailsSidePaneProperty() {
    return showGameDetailsSidePane;
  }

  public boolean getPreReleaseCheckEnabled() {
    return preReleaseCheckEnabled.get();
  }

  public void setPreReleaseCheckEnabled(boolean preReleaseCheckEnabled) {
    this.preReleaseCheckEnabled.set(preReleaseCheckEnabled);
  }

  public LastGamePrefs getLastGame() {
    return lastGame;
  }

  public BooleanProperty preReleaseCheckEnabledProperty() {
    return preReleaseCheckEnabled;
  }

  public boolean isShowPasswordProtectedGames() {
    return showPasswordProtectedGames.get();
  }

  public void setShowPasswordProtectedGames(boolean showPasswordProtectedGames) {
    this.showPasswordProtectedGames.set(showPasswordProtectedGames);
  }

  public boolean isShowModdedGames() {
    return showModdedGames.get();
  }

  public void setShowModdedGames(boolean showModdedGames) {
    this.showModdedGames.set(showModdedGames);
  }

  public GeneratorPrefs getGenerator() {
    return generator;
  }

  public enum UnitDataBaseType {
    SPOOKY("unitDatabase.spooky"),
    RACKOVER("unitDatabase.rackover");

    @Getter
    private final String i18nKey;

    UnitDataBaseType(String i18nKey) {
      this.i18nKey = i18nKey;
    }
  }

  public int getCacheLifeTimeInDays() {
    return cacheLifeTimeInDays.get();
  }

  public void setCacheLifeTimeInDays(int cacheLifeTimeInDays) {
    this.cacheLifeTimeInDays.set(cacheLifeTimeInDays);
  }

  public IntegerProperty cacheLifeTimeInDaysProperty() {
    return cacheLifeTimeInDays;
  }

  public boolean isGameDataCacheActivated() {
    return gameDataCacheActivated.get();
  }

  public void setGameDataCacheActivated(boolean gameDataCacheActivated) {
    this.gameDataCacheActivated.set(gameDataCacheActivated);
  }

  public BooleanProperty gameDataCacheActivatedProperty() {
    return gameDataCacheActivated;
  }

  public boolean isGameDataMapManagementEnabled() {
    return gameDataMapManagementEnabled.get();
  }

  public void setGameDataMapManagementEnabled(boolean gameDataMapManagementEnabled) {
    this.gameDataMapManagementEnabled.set(gameDataMapManagementEnabled);
  }

  public BooleanProperty gameDataMapManagementEnabledProperty() {
    return gameDataMapManagementEnabled;
  }

  public boolean isGameDataPromptDownloadActivated() {
    return gameDataPromptDownloadActivated.get();
  }

  public void setGameDataPromptDownloadActivated(boolean gameDataPromptDownloadActivated) {
    this.gameDataPromptDownloadActivated.set(gameDataPromptDownloadActivated);
  }

  public BooleanProperty gameDataPromptDownloadActivatedProperty() {
    return gameDataPromptDownloadActivated;
  }

  public boolean isGameDataMapDownloadKeepVersionTag() {
    return gameDataMapDownloadKeepVersionTag.get();
  }

  public void setGameDataMapDownloadKeepVersionTag(boolean value) {
    this.gameDataMapDownloadKeepVersionTag.set(value);
  }

  public BooleanProperty gameDataMapDownloadKeepVersionTagProperty() {
    return gameDataMapDownloadKeepVersionTag;
  }

  public boolean isDebugLogEnabled() {
    return debugLogEnabled.get();
  }

  public void setDebugLogEnabled(boolean debugLogEnabled) {
    this.debugLogEnabled.set(debugLogEnabled);
  }

  public BooleanProperty debugLogEnabledProperty() {
    return debugLogEnabled;
  }

  public TadaIntegrationOption getTadaIntegrationOption() {
    return tadaIntegrationOption.get();
  }
  public void setTadaIntegrationOption(TadaIntegrationOption option) { this.tadaIntegrationOption.set(option); }
  public ObjectProperty<TadaIntegrationOption> tadaIntegrationOptionProperty() {
    return tadaIntegrationOption;
  }

  public AskAlwaysOrNever getFeaturedModRevertOption() {
    return featuredModRevertOption.get();
  }
  public void setFeaturedModRevertOption(AskAlwaysOrNever option) { this.featuredModRevertOption.set(option); }
  public ObjectProperty<AskAlwaysOrNever> featuredModRevertOptionProperty() {
    return featuredModRevertOption;
  }

  public RatingMetric getUserInfoRatingMetric() {
    return userInfoRatingMetric.get();
  }

  public void setUserInfoRatingMetric(RatingMetric metric) {
    this.userInfoRatingMetric.set(metric);
  }

  public ObjectProperty<RatingMetric> userInfoRatingMetricProperty() {
    return userInfoRatingMetric;
  }

  public AutoUploadLogsOption getAutoUploadLogsOption() {
    return autoUploadLogsOption.get();
  }

  public void setAutoUploadLogsOption(AutoUploadLogsOption option) { this.autoUploadLogsOption.set(option); }

  public ObjectProperty<AutoUploadLogsOption> autoUploadLogsOptionProperty() {
    return autoUploadLogsOption;
  }

  public String getLastLeaderboardSelection() {
    return lastLeaderboardSelection.get();
  }

  public void setLastLeaderboardSelection(String option) { this.lastLeaderboardSelection.set(option); }

  public StringProperty lastLeaderboardSelectionProperty() {
    return lastLeaderboardSelection;
  }

  public Boolean getLastLeaderboardFriendsOnlySelection() {
    return lastLeaderboardFriendsOnlySelection.get();
  }

  public void setLastLeaderboardFriendsOnlySelection(boolean selected) { this.lastLeaderboardFriendsOnlySelection.set(selected); }

  public BooleanProperty lastLeaderboardFriendsOnlySelection() { return lastLeaderboardFriendsOnlySelection; }

  public boolean getGameRoomPopout() {
    return gameRoomPopout.get();
  }

  public void setGameRoomPopout(boolean option) { this.gameRoomPopout.set(option); }

  public BooleanProperty gameRoomPopoutProperty() {
    return gameRoomPopout;
  }

  public ObservableList<String> getLeaderBoardsSelectionFilter() {
    return LeaderBoardsSelectionFilter.get();
  }

  public void setLeaderBoardsSelectionFilter(ObservableList<String> technicalNames) {
    this.LeaderBoardsSelectionFilter.set(technicalNames);
  }

  public ListProperty<String> leaderBoardsSelectionFilterProperty() {
    return LeaderBoardsSelectionFilter;
  }
}
