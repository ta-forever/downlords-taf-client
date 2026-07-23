package com.faforever.client.galacticwar;

/**
 * Posted on the EventBus when a fetched galaxy scenario reports a last_galaxy_winner.
 * Fires on every scenario fetch while last_galaxy_winner is set — subscribers must
 * de-duplicate using the (galaxyTechnicalName, iteration) pair.
 */
public record GalacticWarWinnerChangedEvent(
    String galaxyTechnicalName,
    String galaxyDisplayName,
    String factionName,
    int iteration) {}
