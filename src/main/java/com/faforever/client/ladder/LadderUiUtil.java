package com.faforever.client.ladder;

import com.faforever.client.i18n.I18n;

import java.util.List;

/** Shared display helpers for the Ladder Points UI surfaces (badge, leaderboards, score screen). */
public final class LadderUiUtil {
  private LadderUiUtil() {
  }

  /** The v1 medal roster, in display order — also the slot order of the User Info medal cabinet
   * (design §9.2). Mirrors the server's enabled metric medals (combat_rating_service/medals.py
   * DEFAULT_MEDALS["enabled"]); keep the two in sync so the cabinet only ever shows greyed slots
   * for medals that can actually be earned. (top_fragger is intentionally omitted — server-side it
   * has the identical recipient as mvp every game and is disabled; a legacy holder still renders as
   * an appended slot.) Codes not in this list still render (appended) but have no fixed slot. */
  public static final List<String> MEDAL_CODES = List.of("mvp", "economy_king", "last_stand");

  /** Hard, prestige per-game medals (combat_rating_service/medals.py predicate roster). Calibrated
   * to ~0.5-0.9% of wins and format-fair. Always shown in the cabinet (greyed until earned). */
  public static final String CARRY = "carry";
  public static final String JUGGERNAUT = "juggernaut";
  public static final String UNTOUCHABLE = "untouchable";
  public static final String GIANT_SLAYER = "giant_slayer";
  public static final List<String> HARD_MEDAL_CODES =
      List.of(CARRY, JUGGERNAUT, UNTOUCHABLE, GIANT_SLAYER);

  /** Tournament-placement medal codes, also featured-selectable. Their counts come from the
   * tournament summary (firsts/seconds/thirds summed across mods), not the LP medal tables. */
  public static final String TOURNAMENT_GOLD = "tournament_gold";
  public static final String TOURNAMENT_SILVER = "tournament_silver";
  public static final String TOURNAMENT_BRONZE = "tournament_bronze";
  public static final List<String> TOURNAMENT_MEDAL_CODES =
      List.of(TOURNAMENT_GOLD, TOURNAMENT_SILVER, TOURNAMENT_BRONZE);

  /** Season-ladder placement medals, awarded at season close (db V139 / season_medals.py). One per
   * player per season — the best tier earned: #1/#2/#3, then top 10% / top 33% (ladders >= 20). */
  public static final String SEASON_GOLD = "season_gold";
  public static final String SEASON_SILVER = "season_silver";
  public static final String SEASON_BRONZE = "season_bronze";
  public static final String SEASON_TOP10 = "season_top10";
  public static final String SEASON_TOP33 = "season_top33";
  public static final List<String> SEASON_MEDAL_CODES =
      List.of(SEASON_GOLD, SEASON_SILVER, SEASON_BRONZE, SEASON_TOP10, SEASON_TOP33);

  /** Achievement medals — per-game personal feats (combat_rating_service/medals.py
   * {@code _achievement_awards}). NOT win-gated (a loser who did the deed still earns it) and
   * with no team-size floor (1v1-eligible). Sniper is a 1-5 tier ladder (kill N enemy
   * Commanders in one game); only the single highest tier is awarded per game, but a player's
   * career can hold different tiers from different games, so all five get a cabinet slot. */
  public static final List<String> SNIPER_MEDAL_CODES =
      List.of("sniper_1", "sniper_2", "sniper_3", "sniper_4", "sniper_5");
  public static final String MASS_PRODUCER = "mass_producer";  // built >= 1000 of one unit type
  public static final String ACE = "ace";                      // a unit with > 100 kills
  public static final List<String> ACHIEVEMENT_MEDAL_CODES = java.util.stream.Stream
      .concat(SNIPER_MEDAL_CODES.stream(), java.util.stream.Stream.of(MASS_PRODUCER, ACE))
      .collect(java.util.stream.Collectors.toUnmodifiableList());

  /** A cabinet display tier: a localized header key + the medal codes in it. The Medal Cabinet
   * lays the still-to-earn medals out under these labelled tiers (earned medals are shown first,
   * in their own section). */
  public record MedalClass(String labelKey, List<String> codes) {}

  /** The cabinet tiers, in display order. */
  public static final List<MedalClass> MEDAL_CLASSES = List.of(
      new MedalClass("medal.class.common", MEDAL_CODES),
      new MedalClass("medal.class.rare", HARD_MEDAL_CODES),
      new MedalClass("medal.class.achievement", ACHIEVEMENT_MEDAL_CODES),
      new MedalClass("medal.class.podium", List.of(SEASON_GOLD, SEASON_SILVER, SEASON_BRONZE)),
      new MedalClass("medal.class.percentile", List.of(SEASON_TOP10, SEASON_TOP33)),
      new MedalClass("medal.class.tournament", TOURNAMENT_MEDAL_CODES));

  /** Theme-relative path to a medal's icon. Every medal now has dedicated art under
   * theme/images/medals/&lt;code&gt;.png (the SVG + Krea-2 badge set, tools/medals/). Podium
   * (season_*) and tournament_* used to share the hall-of-fame medallions; they have their
   * own distinct art now (podium = metal coin, tournament = purple rosette + cup). */
  public static String medalIconPath(String medalCode) {
    return "theme/images/medals/" + medalCode + ".png";
  }

  /** Within-board rank as "#14", or the Unranked label when the player has no rank yet (0). */
  public static String rankDisplay(I18n i18n, int rank) {
    return rank > 0 ? i18n.get("lp.rank.value", rank) : i18n.get("lp.badge.unranked");
  }

  /** Rank + cumulative LP as one cell, e.g. "#14 · 1,240 LP" ("1,240 LP" when unranked). */
  public static String standingDisplay(I18n i18n, SeasonStanding standing) {
    if (standing.getRank() > 0) {
      return i18n.get("lp.standing.value", standing.getRank(), i18n.number(standing.getScore()));
    }
    return i18n.get("lp.standing.noRank", i18n.number(standing.getScore()));
  }

  /** Localized medal name from its stable code, falling back to the raw code. */
  public static String medalDisplayName(I18n i18n, String medalCode) {
    return i18n.getWithDefault(medalCode, "medal." + medalCode + ".name");
  }

  /** Tooltip for a medal shown as a player's avatar: name + how many they hold (the multiplicity),
   * e.g. "Season Champion x3". */
  public static String medalAvatarTooltip(I18n i18n, String medalCode, long count) {
    return i18n.get("medal.avatar.tooltip", medalDisplayName(i18n, medalCode), count);
  }

  /** "wins-draws-losses" for the Season Ladder W-D-L column. */
  public static String winDrawLoss(SeasonStanding standing) {
    return standing.getWins() + "-" + standing.getDraws() + "-" + standing.getLosses();
  }

  /** Pick the standing whose board the player has played most this season (most games),
   * for the context-free inline badge (design §13.5). Null if the player has none. */
  public static SeasonStanding mostPlayed(List<SeasonStanding> standings) {
    return standings.stream().max(java.util.Comparator.comparingInt(SeasonStanding::getGames)).orElse(null);
  }

  /** The player's standing on one specific board (by leaderboard technical name), or null if they
   * have none there. Used where the display context fixes the game format — e.g. the game-lobby
   * team cards — so the rank shown is for that exact board, never a most-played fallback (§13.5). */
  public static SeasonStanding forBoard(List<SeasonStanding> standings, String leaderboardTechnicalName) {
    if (leaderboardTechnicalName == null) {
      return null;
    }
    return standings.stream()
        .filter(s -> leaderboardTechnicalName.equals(s.getLeaderboardTechnicalName()))
        .findFirst()
        .orElse(null);
  }
}
