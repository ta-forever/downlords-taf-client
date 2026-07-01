package com.faforever.client.chat.avatar;

import com.faforever.client.remote.domain.Avatar;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

import static com.github.nocatch.NoCatch.noCatch;

public class AvatarBean {
  private final ObjectProperty<URL> url;
  private final StringProperty description;
  /** When non-null this item is one of the player's medals offered as an avatar (not a server
   * avatar); selecting it sets the featured medal instead of calling the avatar service. */
  @Nullable
  private final String medalCode;

  public AvatarBean(@Nullable URL url, @Nullable String description) {
    this(url, description, null);
  }

  public AvatarBean(@Nullable URL url, @Nullable String description, @Nullable String medalCode) {
    this.url = new SimpleObjectProperty<>(url);
    this.description = new SimpleStringProperty(description);
    this.medalCode = medalCode;
  }

  public static AvatarBean fromAvatar(Avatar avatar) {
    return new AvatarBean(noCatch(() -> new URL(avatar.getUrl())), avatar.getTooltip());
  }

  @Nullable
  public String getMedalCode() {
    return medalCode;
  }

  @Nullable
  public URL getUrl() {
    return url.get();
  }

  public void setUrl(URL url) {
    this.url.set(url);
  }

  public ObjectProperty<URL> urlProperty() {
    return url;
  }

  @Nullable
  public String getDescription() {
    return description.get();
  }

  public void setDescription(String description) {
    this.description.set(description);
  }

  public StringProperty descriptionProperty() {
    return description;
  }
}
