package com.faforever.client.remote.domain;

import com.google.gson.annotations.SerializedName;

public class TournamentCreateMessage extends ClientMessage {
  private String name;
  private String description;
  private String format;
  @SerializedName("best_of")
  private int bestOf;
  @SerializedName("noshow_timeout_minutes")
  private int noshowTimeoutMinutes;
  @SerializedName("check_in_minutes")
  private int checkInMinutes;
  @SerializedName("players_per_side")
  private int playersPerSide;
  @SerializedName("min_players")
  private int minPlayers;
  @SerializedName("featured_mod_id")
  private Integer featuredModId;
  @SerializedName("map_pool_id")
  private Integer mapPoolId;
  // A single fixed map (map_version id) used for every game in the tournament.
  // Takes precedence over map_pool_id server-side.
  @SerializedName("single_map_version_id")
  private Integer singleMapVersionId;
  @SerializedName("min_rating")
  private Integer minRating;
  @SerializedName("max_rating")
  private Integer maxRating;
  @SerializedName("leaderboard_id")
  private Integer leaderboardId;
  @SerializedName("scheduled_start_at")
  private String scheduledStartAt;
  @SerializedName("map_visibility")
  private String mapVisibility;
  // Swiss-specific knobs — consulted server-side only when format=="swiss"
  // (stored on the tournament row verbatim, but _parse_format_options
  // ignores them for other formats).
  @SerializedName("swiss_rounds")
  private Integer swissRounds;
  @SerializedName("top_cut")
  private Integer topCut;
  @SerializedName("top_cut_format")
  private String topCutFormat;

  public TournamentCreateMessage() {
    super(ClientMessageType.TOURNAMENT_CREATE);
    setTarget(MessageTarget.TOURNAMENT);
  }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getFormat() { return format; }
  public void setFormat(String format) { this.format = format; }
  public int getBestOf() { return bestOf; }
  public void setBestOf(int bestOf) { this.bestOf = bestOf; }
  public int getNoshowTimeoutMinutes() { return noshowTimeoutMinutes; }
  public void setNoshowTimeoutMinutes(int v) { this.noshowTimeoutMinutes = v; }
  public int getCheckInMinutes() { return checkInMinutes; }
  public void setCheckInMinutes(int v) { this.checkInMinutes = v; }
  public int getPlayersPerSide() { return playersPerSide; }
  public void setPlayersPerSide(int v) { this.playersPerSide = v; }
  public int getMinPlayers() { return minPlayers; }
  public void setMinPlayers(int v) { this.minPlayers = v; }
  public Integer getFeaturedModId() { return featuredModId; }
  public void setFeaturedModId(Integer v) { this.featuredModId = v; }
  public Integer getMapPoolId() { return mapPoolId; }
  public void setMapPoolId(Integer v) { this.mapPoolId = v; }
  public Integer getSingleMapVersionId() { return singleMapVersionId; }
  public void setSingleMapVersionId(Integer v) { this.singleMapVersionId = v; }
  public Integer getMinRating() { return minRating; }
  public void setMinRating(Integer v) { this.minRating = v; }
  public Integer getMaxRating() { return maxRating; }
  public void setMaxRating(Integer v) { this.maxRating = v; }
  public Integer getLeaderboardId() { return leaderboardId; }
  public void setLeaderboardId(Integer v) { this.leaderboardId = v; }
  public String getScheduledStartAt() { return scheduledStartAt; }
  public void setScheduledStartAt(String v) { this.scheduledStartAt = v; }
  public String getMapVisibility() { return mapVisibility; }
  public void setMapVisibility(String v) { this.mapVisibility = v; }
  public Integer getSwissRounds() { return swissRounds; }
  public void setSwissRounds(Integer v) { this.swissRounds = v; }
  public Integer getTopCut() { return topCut; }
  public void setTopCut(Integer v) { this.topCut = v; }
  public String getTopCutFormat() { return topCutFormat; }
  public void setTopCutFormat(String v) { this.topCutFormat = v; }
}
