package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.Getter;
import lombok.Setter;

/**
 * Realised P&amp;L of the house model-maker bot for one (season, board) — the public
 * "beat the model" scoreboard (read-only, backed by the {@code wager_bot_pnl_view}, V140).
 * Aggregate/lagging only: the bot's live per-game position is never exposed, so nothing here
 * helps bait it. A positive {@code pnlLp} means the crowd is collectively losing to the model
 * on that board. {@code id} is a synthetic {@code season-league} key.
 */
@Getter
@Setter
@Type("wagerBotPnl")
public class WagerBotPnl {
  @Id
  private String id;
  private String ratingType;
  private int markets;
  private int wins;
  private long stakedLp;
  private long pnlLp;
  private int roiBps;
}
