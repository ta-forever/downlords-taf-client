package com.faforever.client.galacticwar;

import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.galacticwar.Scenario.FactionScoreRank;
import com.faforever.client.game.Faction;
import com.faforever.client.io.DownloadService;
import com.faforever.client.player.Player;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.CompletableTask.Priority;
import com.faforever.client.task.TaskService;
import com.faforever.client.update.ClientConfiguration;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
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

  final private Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
  final private Map<Integer, StringProperty> playerNames = new ConcurrentHashMap<>();

  @Override
  public void afterPropertiesSet() throws Exception {
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
            return scenario;
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        })

        .thenCompose(scenario -> {
          Set<Integer> idsToFetch = new HashSet<>();
          // Atomically reserve properties
          for (Integer id : scenario.getPlayers().keySet()) {
            playerNames.computeIfAbsent(id, key -> {
              idsToFetch.add(key);
              return new SimpleStringProperty("Loading...");
            });
          }
          if (idsToFetch.isEmpty()) {
            return CompletableFuture.completedFuture(scenario);
          }

          return playerService
              .getPlayersByIds(idsToFetch)
              .thenApply(playersList -> {
                JavaFxUtil.runLater(() -> {
                  for (var p : playersList) {
                    StringProperty prop = playerNames.get(p.getId());
                    if (prop != null) {
                      prop.set(p.getUsername());
                    }
                  }
                });
                return scenario;
              });
        });
  }

  public StringProperty getPlayerNameProperty(int playerId) {
    return playerNames.computeIfAbsent(playerId, key -> new SimpleStringProperty("<unknown>"));
  }

  Scenario getScenario(String galaxyTechnicalName) {
    return scenarios.getOrDefault(galaxyTechnicalName, null);
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
