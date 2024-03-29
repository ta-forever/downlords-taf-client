package com.faforever.client.map;

import com.faforever.client.api.dto.MapVersion;
import com.faforever.client.vault.review.Review;
import com.faforever.client.vault.review.ReviewsSummary;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class MapBean implements Comparable<MapBean> {

  private final StringProperty hpiArchiveName;
  private final StringProperty mapName;
  private final IntegerProperty numberOfPlays;
  private final StringProperty description;
  private final IntegerProperty downloads;
  private final IntegerProperty players;
  private final ObjectProperty<MapSize> size;
  private final ObjectProperty<ComparableVersion> version;
  private CompletableFuture<String> crcFuture;
  private Function<Void,String> getInstalledMapCrc;
  private final StringProperty id;
  private final StringProperty author;
  private final BooleanProperty hidden;
  private final BooleanProperty ranked;
  private final ObjectProperty<URL> downloadUrl;
  private final ObjectProperty<URL> thumbnailUrl;
  private final ObjectProperty<LocalDateTime> createTime;
  private final ObjectProperty<Type> type;
  private final ObjectProperty<ReviewsSummary> reviewsSummary;
  private final ListProperty<Review> reviews;

  public MapBean() {
    id = new SimpleStringProperty();
    mapName = new SimpleStringProperty();
    hpiArchiveName = new SimpleStringProperty();
    crcFuture = null;
    getInstalledMapCrc = null;
    description = new SimpleStringProperty();
    numberOfPlays = new SimpleIntegerProperty(0);
    downloads = new SimpleIntegerProperty();
    players = new SimpleIntegerProperty();
    size = new SimpleObjectProperty<>();
    version = new SimpleObjectProperty<>();
    thumbnailUrl = new SimpleObjectProperty<>();
    downloadUrl = new SimpleObjectProperty<>();
    author = new SimpleStringProperty();
    createTime = new SimpleObjectProperty<>();
    type = new SimpleObjectProperty<>();
    reviewsSummary = new SimpleObjectProperty<>();
    reviews = new SimpleListProperty<>(FXCollections.observableArrayList(param
        -> new Observable[]{param.scoreProperty(), param.textProperty()}));
    hidden = new SimpleBooleanProperty();
    ranked = new SimpleBooleanProperty();
  }

  public static MapBean fromMapDto(com.faforever.client.api.dto.Map map) {
    MapVersion mapVersion = map.getLatestVersion();

    MapBean mapBean = new MapBean();
    Optional.ofNullable(map.getAuthor()).ifPresent(author -> mapBean.setAuthor(author.getLogin()));
    mapBean.setDescription(mapVersion.getDescription());
    mapBean.setMapName(map.getDisplayName());
    mapBean.setHpiArchiveName(mapVersion.getArchiveName());
    mapBean.setCrcFuture(CompletableFuture.completedFuture(mapVersion.getCrc()));
    mapBean.setSize(MapSize.valueOf(mapVersion.getWidth(), mapVersion.getHeight()));
    mapBean.setDownloads(map.getStatistics().getDownloads());
    mapBean.setId(mapVersion.getId());
    mapBean.setPlayers(mapVersion.getMaxPlayers());
    mapBean.setVersion(mapVersion.getVersion());
    mapBean.setDownloadUrl(mapVersion.getDownloadUrl());
    mapBean.setThumbnailUrl(mapVersion.getThumbnailUrl());
    mapBean.setCreateTime(mapVersion.getCreateTime().toLocalDateTime());
    mapBean.setNumberOfPlays(map.getStatistics().getPlays());
    mapBean.setRanked(mapVersion.getRanked());
    mapBean.setHidden(mapVersion.getHidden());
    mapBean.setReviewsSummary(ReviewsSummary.fromDto(map.getMapReviewsSummary()));
    map.getVersions().forEach(v -> {
      if (v.getReviews() != null) {
        v.getReviews().forEach(mapVersionReview -> {
          Review review = Review.fromDto(mapVersionReview);
          review.setVersion(mapVersion.getVersion());
          review.setLatestVersion(map.getLatestVersion().getVersion());
          mapBean.getReviews().add(review);
        });
      }
    });
    return mapBean;
  }

  public static MapBean fromMapVersionDto(com.faforever.client.api.dto.MapVersion mapVersion) {
    MapBean mapBean = new MapBean();
    Optional.ofNullable(mapVersion.getMap().getAuthor()).ifPresent(author -> mapBean.setAuthor(author.getLogin()));
    mapBean.setDescription(mapVersion.getDescription());
    mapBean.setMapName(mapVersion.getMap().getDisplayName());
    mapBean.setHpiArchiveName(mapVersion.getArchiveName());
    mapBean.setCrcFuture(CompletableFuture.completedFuture(mapVersion.getCrc()));
    mapBean.setSize(MapSize.valueOf(mapVersion.getWidth(), mapVersion.getHeight()));
    mapBean.setId(mapVersion.getId());
    mapBean.setPlayers(mapVersion.getMaxPlayers());
    mapBean.setVersion(mapVersion.getVersion());
    mapBean.setDownloadUrl(mapVersion.getDownloadUrl());
    mapBean.setThumbnailUrl(mapVersion.getThumbnailUrl());
    mapBean.setCreateTime(mapVersion.getCreateTime().toLocalDateTime());
    mapBean.setReviewsSummary(ReviewsSummary.fromDto(mapVersion.getMap().getMapReviewsSummary()));
    if (mapVersion.getMap().getStatistics() != null) {
      mapBean.setDownloads(mapVersion.getMap().getStatistics().getDownloads());
      mapBean.setNumberOfPlays(mapVersion.getMap().getStatistics().getPlays());
    }
    if (mapVersion.getMap().getVersions() != null) {
      mapVersion.getMap().getVersions().forEach(v -> {
        if (v.getReviews() != null) {
          v.getReviews().forEach(mapVersionReview -> {
            Review review = Review.fromDto(mapVersionReview);
            review.setVersion(v.getVersion());
            review.setLatestVersion(mapVersion.getMap().getLatestVersion().getVersion());
            mapBean.getReviews().add(review);
          });
        }
      });
    }
    mapBean.setHidden(mapVersion.getHidden());
    mapBean.setRanked(mapVersion.getRanked());
    return mapBean;
  }

  public String getAuthor() {
    return author.get();
  }

  public void setAuthor(String author) {
    this.author.set(author);
  }

  public StringProperty authorProperty() {
    return author;
  }

  public URL getDownloadUrl() {
    return downloadUrl.get();
  }

  public void setDownloadUrl(URL downloadUrl) {
    this.downloadUrl.set(downloadUrl);
  }

  public ObjectProperty<URL> downloadUrlProperty() {
    return downloadUrl;
  }

  public StringProperty mapNameProperty() {
    return mapName;
  }

  public String getDescription() {
    return description.get();
  }

  public void setDescription(String description) {
    this.description.set(description);
  }

  public StringProperty descriptionProperty() {
    return description;
  }

  public int getNumberOfPlays() {
    return numberOfPlays.get();
  }

  public void setNumberOfPlays(int plays) {
    this.numberOfPlays.set(plays);
  }

  public IntegerProperty numberOfPlaysProperty() {
    return numberOfPlays;
  }

  public int getDownloads() {
    return downloads.get();
  }

  public void setDownloads(int downloads) {
    this.downloads.set(downloads);
  }

  public IntegerProperty downloadsProperty() {
    return downloads;
  }

  public MapSize getSize() {
    return size.get();
  }

  public void setSize(MapSize size) {
    this.size.set(size);
  }

  public ObjectProperty<MapSize> sizeProperty() {
    return size;
  }

  public int getPlayers() {
    return players.get();
  }

  public void setPlayers(int players) {
    this.players.set(players);
  }

  public IntegerProperty playersProperty() {
    return players;
  }

  @Nullable
  public ComparableVersion getVersion() {
    return version.get();
  }

  public void setVersion(ComparableVersion version) {
    this.version.set(version);
  }

  public ObjectProperty<ComparableVersion> versionProperty() {
    return version;
  }

  /*
   * beware, may take a long time to retrieve.  Use getCrcFuture if you need CRC for a lot of maps
   */
  public String getCrcValue() {
    try {
      if (getCrcFuture() != null) {
        String crc = getCrcFuture().get();
        return crc;
      }
    } catch (InterruptedException | ExecutionException ignored) { }
    return "00000000";
  }

  public CompletableFuture<String> getCrcFuture() {
    synchronized(this) {
      if (crcFuture == null && getInstalledMapCrc != null) {
        crcFuture = CompletableFuture.supplyAsync(() -> getInstalledMapCrc.apply(null));
      }
    }
    return crcFuture;
  }

  public void setInstalledMapCrcGetter(Function<Void, String> getter) {
    this.getInstalledMapCrc = getter;
  }

  public void setCrcFuture(CompletableFuture<String> crcFuture) {
    this.crcFuture = crcFuture;
  }

  @Override
  public int compareTo(@NotNull MapBean o) {
    return getMapName().compareTo(o.getMapName());
  }

  public String getMapName() {
    return mapName.get();
  }

  public void setMapName(String mapName) {
    this.mapName.set(mapName);
  }

  public StringProperty idProperty() {
    return id;
  }

  public String getId() {
    return id.get();
  }

  public void setId(String id) {
    this.id.set(id);
  }

  public String getHpiArchiveName() {
    return hpiArchiveName.get();
  }

  public void setHpiArchiveName(String hpiArchiveName) {
    this.hpiArchiveName.set(hpiArchiveName);
  }

  public StringProperty hpiArchiveNameProperty() {
    return hpiArchiveName;
  }

  public URL getThumbnailUrl() {
    return thumbnailUrl.get();
  }

  public void setThumbnailUrl(URL thumbnailUrl) {
    this.thumbnailUrl.set(thumbnailUrl);
  }

  public ObjectProperty<URL> thumbnailUrlProperty() {
    return thumbnailUrl;
  }

  public LocalDateTime getCreateTime() {
    return createTime.get();
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime.set(createTime);
  }

  public ObjectProperty<LocalDateTime> createTimeProperty() {
    return createTime;
  }

  public Type getType() {
    return type.get();
  }

  public void setType(Type type) {
    this.type.set(type);
  }

  public ObjectProperty<Type> typeProperty() {
    return type;
  }

  public ReviewsSummary getReviewsSummary() {
    return reviewsSummary.get();
  }

  public void setReviewsSummary(ReviewsSummary reviewsSummary) {
    this.reviewsSummary.set(reviewsSummary);
  }

  public ObjectProperty<ReviewsSummary> reviewsSummaryProperty() {
    return reviewsSummary;
  }

  public ObservableList<Review> getReviews() {
    return reviews.get();
  }

  public ListProperty<Review> reviewsProperty() {
    return reviews;
  }

  public boolean isHidden() {
    return hidden.get();
  }

  public void setHidden(boolean hidden) {
    this.hidden.set(hidden);
  }

  public BooleanProperty hiddenProperty() {
    return hidden;
  }

  public boolean isRanked() {
    return ranked.get();
  }

  public void setRanked(boolean ranked) {
    this.ranked.set(ranked);
  }

  public BooleanProperty rankedProperty() {
    return ranked;
  }

  public enum Type {
    SKIRMISH("skirmish"),
    COOP("campaign_coop"),
    OTHER(null);

    private static final Map<String, Type> fromString;

    static {
      fromString = new HashMap<>();
      for (Type type : values()) {
        fromString.put(type.string, type);
      }
    }

    private final String string;

    Type(String string) {
      this.string = string;
    }

    public static Type fromString(String type) {
      if (fromString.containsKey(type)) {
        return fromString.get(type);
      }
      return OTHER;
    }
  }
}
