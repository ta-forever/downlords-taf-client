package com.faforever.client.preferences;

import com.faforever.client.chat.ChatColorMode;
import com.faforever.client.chat.ChatFormat;
import com.faforever.client.chat.ChatUserCategory;
import com.faforever.client.player.SocialStatus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.faforever.client.chat.ChatColorMode.DEFAULT;
import static com.faforever.client.preferences.LanguageChannel.FRENCH;
import static com.faforever.client.preferences.LanguageChannel.GERMAN;
import static com.faforever.client.preferences.LanguageChannel.RUSSIAN;

public class ChatPrefs {

  @VisibleForTesting
  public static final ImmutableMap<Locale, LanguageChannel> LOCALE_LANGUAGES_TO_CHANNELS = ImmutableMap.<Locale, LanguageChannel>builder()
      .put(Locale.FRENCH, FRENCH)
      .put(Locale.GERMAN, GERMAN)
      .put(new Locale("ru"), RUSSIAN)
      .put(new Locale("be"), RUSSIAN)
      .build();

  private final DoubleProperty zoom = new SimpleDoubleProperty(1);
  private final BooleanProperty learnedAutoComplete = new SimpleBooleanProperty(false);
  private final BooleanProperty previewImageUrls = new SimpleBooleanProperty(true);
  private final IntegerProperty maxMessages = new SimpleIntegerProperty(500);
  private final ObjectProperty<ChatColorMode> chatColorMode = new SimpleObjectProperty<>(DEFAULT);
  private final IntegerProperty channelTabScrollPaneWidth = new SimpleIntegerProperty(250);
  private final MapProperty<String, Color> userToColor = new SimpleMapProperty<>(FXCollections.observableHashMap());
  private final MapProperty<ChatUserCategory, Color> groupToColor = new SimpleMapProperty<>(FXCollections.observableHashMap());
  private final BooleanProperty hideFoeMessages = new SimpleBooleanProperty(true);
  private final BooleanProperty showToxicity = new SimpleBooleanProperty(false);
  private final BooleanProperty playerListShown = new SimpleBooleanProperty(true);
  private final ObjectProperty<TimeInfo> timeFormat = new SimpleObjectProperty<>(TimeInfo.AUTO);
  private final ObjectProperty<DateInfo> dateFormat = new SimpleObjectProperty<>(DateInfo.AUTO);
  private final ObjectProperty<ChatFormat> chatFormat = new SimpleObjectProperty<>(ChatFormat.COMPACT);
  private final IntegerProperty idleThreshold = new SimpleIntegerProperty(10);
  private final ListProperty<ToxicitySetting> toxicitySettings2 = new SimpleListProperty<>(FXCollections.observableArrayList());
  private ListProperty<String> autoJoinChannels2;

  private ChatPrefs() {
    // Only for Gson
  }

  public ChatPrefs(List<String> defaultAutoJoinChannels) {
    init(defaultAutoJoinChannels);
  }

  public void init(List<String> defaultAutoJoinChannels) {
    if (autoJoinChannels2 == null) {
      ObservableList<String> observableList = FXCollections.observableArrayList(defaultAutoJoinChannels);
      autoJoinChannels2 = new SimpleListProperty<>(observableList);
    }

    if (toxicitySettings2.isEmpty()) {
      toxicitySettings2.add(new ToxicitySetting(SocialStatus.FOE, 0.67, ToxicityAction.SUPPRESS));
      toxicitySettings2.add(new ToxicitySetting(SocialStatus.FRIEND, 0.95, ToxicityAction.MASK));
      toxicitySettings2.add(new ToxicitySetting(SocialStatus.OTHER, 0.9, ToxicityAction.SUPPRESS));
    }
  }

  public ChatColorMode getChatColorMode() {
    return chatColorMode.get();
  }

  public void setChatColorMode(ChatColorMode chatColorMode) {
    this.chatColorMode.set(chatColorMode);
  }

  public TimeInfo getTimeFormat() {
    return timeFormat.get();
  }

  public void setTimeFormat(TimeInfo time) {
    this.timeFormat.set(time);
  }

  public DateInfo getDateFormat() {
    return dateFormat.get();
  }

  public void setDateFormat(DateInfo date) {
    this.dateFormat.set(date);
  }

  public ChatFormat getChatFormat() {
    return this.chatFormat.get();
  }

  public void setChatFormat(ChatFormat chatFormat) {
    this.chatFormat.setValue(chatFormat);
  }

  public ObjectProperty<ChatFormat> chatFormatProperty() {
    return chatFormat;
  }

  public ObjectProperty<ChatColorMode> chatColorModeProperty() {
    return chatColorMode;
  }

  public ObservableMap<String, Color> getUserToColor() {
    return userToColor.get();
  }

  public void setUserToColor(ObservableMap<String, Color> userToColor) {
    this.userToColor.set(userToColor);
  }

  public MapProperty<String, Color> userToColorProperty() {
    return userToColor;
  }

  public ObservableMap<ChatUserCategory, Color> getGroupToColor() {
    return groupToColor.get();
  }

  public void setGroupToColor(ObservableMap<ChatUserCategory, Color> groupToColor) {
    this.groupToColor.set(groupToColor);
  }

  public MapProperty<ChatUserCategory, Color> groupToColorProperty() {
    return groupToColor;
  }

  public boolean getPreviewImageUrls() {
    return previewImageUrls.get();
  }

  public void setPreviewImageUrls(boolean previewImageUrls) {
    this.previewImageUrls.set(previewImageUrls);
  }

  public BooleanProperty previewImageUrlsProperty() {
    return previewImageUrls;
  }

  public Double getZoom() {
    return zoom.getValue();
  }

  public void setZoom(double zoom) {
    this.zoom.set(zoom);
  }

  public DoubleProperty zoomProperty() {
    return zoom;
  }

  public boolean getLearnedAutoComplete() {
    return learnedAutoComplete.get();
  }

  public void setLearnedAutoComplete(boolean learnedAutoComplete) {
    this.learnedAutoComplete.set(learnedAutoComplete);
  }

  public BooleanProperty learnedAutoCompleteProperty() {
    return learnedAutoComplete;
  }

  public int getMaxMessages() {
    return maxMessages.get();
  }

  public void setMaxMessages(int maxMessages) {
    this.maxMessages.set(maxMessages);
  }

  public IntegerProperty maxMessagesProperty() {
    return maxMessages;
  }

  public int getChannelTabScrollPaneWidth() {
    return channelTabScrollPaneWidth.get();
  }

  public void setChannelTabScrollPaneWidth(int channelTabScrollPaneWidth) {
    this.channelTabScrollPaneWidth.set(channelTabScrollPaneWidth);
  }

  public IntegerProperty channelTabScrollPaneWidthProperty() {
    return channelTabScrollPaneWidth;
  }


  public boolean getHideFoeMessages() {
    return hideFoeMessages.get();
  }

  public void setHideFoeMessages(boolean hideFoeMessages) {
    this.hideFoeMessages.set(hideFoeMessages);
  }

  public BooleanProperty hideFoeMessagesProperty() {
    return hideFoeMessages;
  }

  public boolean getShowToxicity() {
    return showToxicity.get();
  }

  public void setShowToxicity(boolean showToxicity) {
    this.showToxicity.set(showToxicity);
  }

  public BooleanProperty showToxicityProperty() {
    return showToxicity;
  }

  public int getIdleThreshold() {
    return idleThreshold.get();
  }

  public void setIdleThreshold(int idleThreshold) {
    this.idleThreshold.set(idleThreshold);
  }

  public IntegerProperty idleThresholdProperty() {
    return idleThreshold;
  }

  public ObservableList<String> getAutoJoinChannels2() { return autoJoinChannels2.get(); }
  public ListProperty<String> autoJoinChannels2Property() { return autoJoinChannels2; }

  public ObservableList<ToxicitySetting> getToxicitySettings2() { return toxicitySettings2.get(); }
  public ListProperty<ToxicitySetting> toxicitySettings2Property() { return toxicitySettings2; }

  public boolean isPlayerListShown() {
    return playerListShown.get();
  }

  public void setPlayerListShown(boolean playerListShown) {
    this.playerListShown.set(playerListShown);
  }

  public BooleanProperty playerListShownProperty() {
    return playerListShown;
  }

}
