package com.faforever.client.galacticwar;

/** Posted on the EventBus when a fetched galaxy scenario reports a last_galaxy_winner. */
public record GalacticWarWinnerChangedEvent(String factionName) {}
