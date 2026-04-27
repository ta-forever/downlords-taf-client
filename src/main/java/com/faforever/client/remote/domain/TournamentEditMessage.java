package com.faforever.client.remote.domain;

import com.google.gson.annotations.SerializedName;

/**
 * Sent by the tournament creator to update mutable fields on a pending
 * player-created tournament. Only non-null fields are applied server-side.
 * Use the sentinel "__clear__" to explicitly null a field.
 */
public class TournamentEditMessage extends ClientMessage {
  public static final String CLEAR = "__clear__";

  @SerializedName("tournament_id")
  private int tournamentId;
  private String name;
  private String description;
  private String format;
  @SerializedName("best_of")
  private Integer bestOf;
  @SerializedName("noshow_timeout_minutes")
  private Integer noshowTimeoutMinutes;
  @SerializedName("check_in_minutes")
  private Integer checkInMinutes;
  @SerializedName("min_players")
  private Integer minPlayers;
  @SerializedName("featured_mod_id")
  private Integer featuredModId;
  @SerializedName("map_pool_id")
  private Integer mapPoolId;
  @SerializedName("min_rating")
  private Integer minRating;
  @SerializedName("max_rating")
  private Integer maxRating;
  @SerializedName("leaderboard_id")
  private Integer leaderboardId;
  @SerializedName("map_visibility")
  private String mapVisibility;
  @SerializedName("scheduled_start_at")
  private String scheduledStartAt;
  // Swiss-specific knobs. Absent fields mean "don't change" on the server;
  // present fields re-serialise the format_options JSON.
  @SerializedName("swiss_rounds")
  private Integer swissRounds;
  @SerializedName("top_cut")
  private Integer topCut;
  @SerializedName("top_cut_format")
  private String topCutFormat;

  public TournamentEditMessage(int tournamentId) {
    super(ClientMessageType.TOURNAMENT_EDIT);
    setTarget(MessageTarget.TOURNAMENT);
    this.tournamentId = tournamentId;
  }

  public int getTournamentId() { return tournamentId; }
  public String getName() { return name; }
  public void setName(String v) { this.name = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { this.description = v; }
  public String getFormat() { return format; }
  public void setFormat(String v) { this.format = v; }
  public Integer getBestOf() { return bestOf; }
  public void setBestOf(Integer v) { this.bestOf = v; }
  public Integer getNoshowTimeoutMinutes() { return noshowTimeoutMinutes; }
  public void setNoshowTimeoutMinutes(Integer v) { this.noshowTimeoutMinutes = v; }
  public Integer getCheckInMinutes() { return checkInMinutes; }
  public void setCheckInMinutes(Integer v) { this.checkInMinutes = v; }
  public Integer getMinPlayers() { return minPlayers; }
  public void setMinPlayers(Integer v) { this.minPlayers = v; }
  public Integer getFeaturedModId() { return featuredModId; }
  public void setFeaturedModId(Integer v) { this.featuredModId = v; }
  public Integer getMapPoolId() { return mapPoolId; }
  public void setMapPoolId(Integer v) { this.mapPoolId = v; }
  public Integer getMinRating() { return minRating; }
  public void setMinRating(Integer v) { this.minRating = v; }
  public Integer getMaxRating() { return maxRating; }
  public void setMaxRating(Integer v) { this.maxRating = v; }
  public Integer getLeaderboardId() { return leaderboardId; }
  public void setLeaderboardId(Integer v) { this.leaderboardId = v; }
  public String getMapVisibility() { return mapVisibility; }
  public void setMapVisibility(String v) { this.mapVisibility = v; }
  public String getScheduledStartAt() { return scheduledStartAt; }
  public void setScheduledStartAt(String v) { this.scheduledStartAt = v; }
  public Integer getSwissRounds() { return swissRounds; }
  public void setSwissRounds(Integer v) { this.swissRounds = v; }
  public Integer getTopCut() { return topCut; }
  public void setTopCut(Integer v) { this.topCut = v; }
  public String getTopCutFormat() { return topCutFormat; }
  public void setTopCutFormat(String v) { this.topCutFormat = v; }
}
