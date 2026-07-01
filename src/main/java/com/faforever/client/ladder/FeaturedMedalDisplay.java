package com.faforever.client.ladder;

import lombok.Value;

/** A player's chosen display medal (the medal-as-avatar) plus how many of it they have earned, for
 * the avatar slot tooltip's multiplicity (e.g. "Season Champion x3"). */
@Value
public class FeaturedMedalDisplay {
  String code;
  long count;
}
