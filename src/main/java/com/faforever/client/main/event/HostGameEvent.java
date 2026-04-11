package com.faforever.client.main.event;

import com.faforever.client.game.Game;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HostGameEvent extends OpenCustomGamesEvent {
  final private String mapFolderName;
  private Game contextGame;

  // Optional presets — used when launching from a tournament match.
  // If null, the create game dialog uses the user's normal defaults.
  /** Featured mod *technical* name (not display name) — matches what
   *  ModService.getFeaturedMod and mapService.loadPreview expect. */
  private String presetFeaturedMod;
  private Integer presetMaxPlayers;
  private Boolean presetRanked;
  private Boolean presetFriendsOnly;
  private Boolean presetEnforceRating;
  private String presetMinRating;
  private String presetMaxRating;
  private String presetPassword;
  /** A one-shot title that should NOT be persisted as the user's default. */
  private String presetTransientTitle;

  public HostGameEvent setContextGame(Game game) {
    contextGame = game;
    return this;
  }
}
