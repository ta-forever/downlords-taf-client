package com.faforever.client.galacticwar;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.galacticwar.Scenario.FactionScoreRank;
import com.faforever.client.io.DownloadService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.update.ClientConfiguration;
import com.google.common.eventbus.EventBus;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Lazy
@Service
@Slf4j
@RequiredArgsConstructor
public class GalacticWarService implements InitializingBean {

  final private PreferencesService preferencesService;
  final private TaskService taskService;
  final private DownloadService downloadService;
  final private PlayerService playerService;
  final private EventBus eventBus;

  final private Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
  final private Map<Integer, StringProperty> playerNames = new ConcurrentHashMap<>();

  @Override
  public void afterPropertiesSet() throws Exception {
    for (String url : getGwEndpoints()) {
      fetchScenario(url);
    }
  }

  List<String> getGwEndpoints() {
    ClientConfiguration.Endpoints ep = preferencesService.getClientRemoteConfiguration()
        .getEndpoints()
        .get(0);

    List<String> gwEndpoints;
    if (ep.getGalacticWar2() != null) {
      gwEndpoints = ep.getGalacticWar2();
    } else {
      gwEndpoints = new ArrayList<>(1);
      gwEndpoints.add(ep.getGalacticWar().getUrl());
    }

    return gwEndpoints;
  }

  CompletableFuture<Scenario> fetchScenario(String endpointUrl) throws URISyntaxException {
    String targetFilename = Paths.get(new URI(endpointUrl).getPath())
        .getFileName()
        .toString();

    final Path targetPath = preferencesService.getCacheDirectory().resolve(targetFilename);
    return taskService.submitTask(new CompletableTask<Void>(Priority.LOW) {
          protected Void call() {
            try {
              Files.deleteIfExists(targetPath);
              downloadService.downloadFile(new URL(endpointUrl), targetPath, null);
            } catch (IOException e) {
              throw new CompletionException(e);
            }
            return null;
          }
        }).getFuture()

        .thenApply(x -> {
          try {
            Scenario scenario = Scenario.fromFile(targetPath);
            scenarios.put(scenario.getTechnicalName(), scenario);
            if (scenario.getLastGalaxyWinner() != null) {
              eventBus.post(new GalacticWarWinnerChangedEvent(
                  scenario.getTechnicalName(),
                  scenario.getDisplayName(),
                  scenario.getLastGalaxyWinner(),
                  scenario.getIteration() != null ? scenario.getIteration() : 1));
            }
            return scenario;
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        })

        .thenCompose(scenario -> {
          Set<Integer> idsToFetch = new HashSet<>();

          // Reserve a property per player and note which still need resolving. The test is on the
          // VALUE, not key-absence: scenarios.put() above publishes the scenario to the UI a stage
          // before we get here, so the leaderboard may already have called
          // getPlayerNameProperty() and installed a placeholder. Keying off computeIfAbsent's
          // mapper would then skip those ids and strand them on the placeholder forever.
          java.util.function.Consumer<Integer> reserve = id -> {
            StringProperty prop =
                playerNames.computeIfAbsent(id, key -> new SimpleStringProperty(NAME_LOADING));
            if (isPlaceholderName(prop.get())) {
              idsToFetch.add(id);
            }
          };

          // The CAREER map, not getPlayers(): veterans who have not fought yet this galaxy exist
          // only in lifetime_players, and the career leaderboard lists them.
          scenario.getCareerPlayers().keySet().forEach(reserve);

          // Also resolve past wars' top contributors so the Hall of Victors and the
          // victory splash honours panel can show names
          if (scenario.getHistory() != null) {
            scenario.getHistory().stream()
                .filter(entry -> entry.getTopContributors() != null)
                .flatMap(entry -> entry.getTopContributors().values().stream())
                .flatMap(java.util.List::stream)
                .map(Scenario.Contributor::getPlayerId)
                .filter(java.util.Objects::nonNull)
                .forEach(reserve);
          }
          if (idsToFetch.isEmpty()) {
            return CompletableFuture.completedFuture(scenario);
          }

          return playerService
              .getPlayersByIds(idsToFetch)
              .thenApply(playersList -> {
                JavaFxUtil.runLater(() -> {
                  Set<Integer> resolved = new HashSet<>();
                  for (var p : playersList) {
                    StringProperty prop = playerNames.get(p.getId());
                    if (prop != null) {
                      prop.set(p.getUsername());
                    }
                    resolved.add(p.getId());
                  }
                  // Ids the API had nothing for (deleted accounts) would otherwise sit on
                  // "Loading..." forever; say so instead.
                  idsToFetch.stream()
                      .filter(id -> !resolved.contains(id))
                      .map(playerNames::get)
                      .filter(java.util.Objects::nonNull)
                      .forEach(prop -> prop.set(NAME_UNKNOWN));
                });
                return scenario;
              });
        });
  }

  static final String NAME_LOADING = "Loading...";
  static final String NAME_UNKNOWN = "<unknown>";

  /** True while a name has never been resolved — such ids are (re)tried on the next fetch. */
  private static boolean isPlaceholderName(String name) {
    return name == null || NAME_LOADING.equals(name) || NAME_UNKNOWN.equals(name);
  }

  public StringProperty getPlayerNameProperty(int playerId) {
    return playerNames.computeIfAbsent(playerId, key -> new SimpleStringProperty(NAME_UNKNOWN));
  }

  Scenario getScenario(String galaxyTechnicalName) {
    return scenarios.getOrDefault(galaxyTechnicalName, null);
  }

  /** One-shot request (snapshot-build debugging) for the GW view to preview the victory splash. */
  private final java.util.concurrent.atomic.AtomicReference<com.faforever.client.game.Faction>
      debugVictorySplashRequest = new java.util.concurrent.atomic.AtomicReference<>();

  public void requestDebugVictorySplash(com.faforever.client.game.Faction faction) {
    debugVictorySplashRequest.set(faction);
  }

  public Optional<com.faforever.client.game.Faction> consumeDebugVictorySplashRequest() {
    return Optional.ofNullable(debugVictorySplashRequest.getAndSet(null));
  }

  /** Returns a map of galaxy technical name → display name for all loaded scenarios. */
  public Map<String, String> getGalaxyDisplayNames() {
    Map<String, String> result = new java.util.LinkedHashMap<>();
    scenarios.forEach((techName, scenario) -> result.put(techName, scenario.getDisplayName()));
    return result;
  }

  /**
   * Get a GW rank icon from stored faction name and rank tier (0-based, as persisted in gw_game_player_stats).
   */
  public Image getMedalImage(String gwFaction, int gwRankTier) {
    if (gwFaction == null || gwRankTier < 0) return null;
    int tier = gwRankTier + 1; // DB stores 0-based, icon paths use 1-based
    String iconPath = String.format("images/ranks/RANK_%s%d.png", gwFaction.toUpperCase(), tier);
    return MEDAL_IMAGE_CACHE.computeIfAbsent(iconPath, path -> new Image(path, true));
  }

  private static final Map<String, Image> MEDAL_IMAGE_CACHE = new ConcurrentHashMap<>();

  public ImageView getMedalIcon(Scenario scenario, String factionName, GwRank rank) {
    factionName = factionName.toUpperCase();
    String iconPath = scenario.getMedalIconPath(factionName, rank);
    Image image = MEDAL_IMAGE_CACHE.computeIfAbsent(
        iconPath,
        path -> new Image(path, true)
    );

    ImageView icon = new ImageView(image);
    icon.setFitWidth(16);
    icon.setFitHeight(16);
    icon.setPreserveRatio(true);
    icon.setUserData(rank);
    return icon;
  }

  /**
   * Get a GW rank icon Image for a player in the given galaxy (from live scenario data).
   * Returns null if the player has no GW XP in that galaxy.
   */
  public Image getMedalImageForGalaxy(int playerId, String galaxyTechnicalName) {
    Scenario scenario = scenarios.get(galaxyTechnicalName);
    if (scenario == null) return null;

    Optional<FactionScoreRank> scoreRank = scenario.getFactionScoreRank(playerId);
    if (scoreRank.isEmpty()) return null;

    String factionName = scoreRank.get().faction().getString().toUpperCase();
    GwRank rank = scoreRank.get().rank();
    if (factionName == null || rank == null) return null;

    String iconPath = scenario.getMedalIconPath(factionName, rank);
    return MEDAL_IMAGE_CACHE.computeIfAbsent(iconPath, path -> new Image(path, true));
  }

  public ImageView getMedalIcon(int playerId, String planetName) {

    if (planetName == null || planetName.isBlank()) {
      return null;
    }

    Scenario scenario = null;
    String actualPlanetName = planetName;

    // Case 1: "<galaxy>/<planet>"
    int slashIndex = planetName.indexOf('/');
    if (slashIndex >= 0) {
      String galaxyName = planetName.substring(0, slashIndex);
      actualPlanetName = planetName.substring(slashIndex + 1);

      scenario = scenarios.get(galaxyName);
    }
    // Case 2: Only planet name → search all scenarios
    else {
      for (Scenario s : scenarios.values()) {
        boolean found = s.getPlanets().stream()
            .anyMatch(p -> p.getName().equalsIgnoreCase(planetName));

        if (found) {
          scenario = s;
          break;
        }
      }
    }

    if (scenario == null) {
      return null;
    }

    Optional<FactionScoreRank> scoreRank = scenario.getFactionScoreRank(playerId);
    if (scoreRank.isEmpty()) {
      return null;
    }

    String factionName = scoreRank.get().faction().getString();
    GwRank rank = scoreRank.get().rank();

    if (factionName == null || rank == null) {
      return null;
    }

    return getMedalIcon(scenario, factionName, rank);
  }
}
