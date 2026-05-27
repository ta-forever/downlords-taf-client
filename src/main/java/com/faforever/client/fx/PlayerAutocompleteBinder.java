package com.faforever.client.fx;

import com.faforever.client.player.PlayerService;
import com.faforever.client.remote.FafService;
import javafx.animation.PauseTransition;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.TextFields;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Reusable player-name autocomplete binder. Originally lived inside
 * TournamentsController for the team-tournament invite field; lifted out so
 * the reserved-slots editor (and any future username picker) can reuse the
 * same matching rules.
 *
 * Each instance owns one set of caches (lower-cased name -> id / display /
 * clan) and one debounce timer, so a caller that needs to bind multiple
 * fields independently should create one binder per field.
 *
 * Matching rules, in order:
 *   1. lower-cased login startsWith the typed prefix
 *   2. any underscore-split part of the login startsWith the prefix
 *      (so "Axle" matches "TAFR_Axle", mirroring chat behaviour)
 *   3. real clan tag startsWith the prefix (uses Player.clan, not just
 *      the underscore prefix of the login)
 *
 * Display strings shown in the popup are decorated as "[CLAN] Name" when
 * the player has a clan; {@link #resolveSelection(String)} undoes that
 * decoration so callers can get back to a player id.
 */
@Slf4j
public class PlayerAutocompleteBinder {

  private final PlayerService playerService;
  private final FafService fafService;

  /** Lower-cased login -> player id. Read at selection-resolve time. */
  private final Map<String, Integer> nameToId = new ConcurrentHashMap<>();
  /** Lower-cased login -> display-case login (so we can show "Foo_Bar"
   *  even for offline players whose case PlayerService doesn't know). */
  private final Map<String, String> displayName = new ConcurrentHashMap<>();
  /** Lower-cased login -> lower-cased clan tag (absent if no clan). */
  private final Map<String, String> clanLower = new ConcurrentHashMap<>();

  /** Current api-search debounce; cancelled on each keystroke. */
  private PauseTransition searchDebounce;

  public PlayerAutocompleteBinder(PlayerService playerService, FafService fafService) {
    this.playerService = playerService;
    this.fafService = fafService;
  }

  /**
   * Wire autocomplete onto the given field. Resets internal caches first and
   * pre-seeds them from {@link PlayerService}'s in-memory list of online
   * players so the popup is useful immediately, before any api round-trip.
   * Offline players are added later as the debounced api search returns.
   */
  public void bind(TextField field) {
    nameToId.clear();
    displayName.clear();
    clanLower.clear();

    for (String name : playerService.getPlayerNames()) {
      playerService.getPlayerForUsername(name).ifPresent(p -> {
        String lc = name.toLowerCase(Locale.US);
        nameToId.put(lc, p.getId());
        displayName.put(lc, name);
        String clan = p.getClan();
        if (clan != null && !clan.isBlank()) {
          clanLower.put(lc, clan.toLowerCase(Locale.US));
        }
      });
    }

    AutoCompletionBinding<String> binding = TextFields.bindAutoCompletion(field, request -> {
      String text = request.getUserText();
      if (text == null || text.isBlank()) {
        return Collections.emptyList();
      }
      String lc = text.toLowerCase(Locale.US);
      return nameToId.keySet().stream()
          .filter(name -> matchesPrefix(name, lc))
          .sorted()
          .limit(20)
          .map(this::displayWithClan)
          .collect(Collectors.toList());
    });
    binding.setDelay(0);
    binding.setVisibleRowCount(10);

    // Async api search for offline players. 500ms debounce — fast enough to
    // feel reactive, slow enough that typing 2-3 chars/sec doesn't waste
    // calls. Results merge into nameToId; popup is re-triggered if new
    // names came in AND the field still contains the prompting prefix.
    field.textProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal == null || newVal.length() < 2) {
        return;
      }
      if (searchDebounce != null) {
        searchDebounce.stop();
      }
      searchDebounce = new PauseTransition(Duration.millis(500));
      searchDebounce.setOnFinished(ev -> {
        String prefix = newVal.trim();
        fafService.findPlayersByLoginPrefix(prefix, 15).thenAccept(players ->
            JavaFxUtil.runLater(() -> {
              boolean changed = false;
              for (com.faforever.client.api.dto.Player p : players) {
                String key = p.getLogin().toLowerCase(Locale.US);
                Integer existing = nameToId.get(key);
                int newId = Integer.parseInt(p.getId());
                if (existing == null || existing != newId) {
                  nameToId.put(key, newId);
                  displayName.put(key, p.getLogin());
                  changed = true;
                }
              }
              String current = field.getText() == null ? "" : field.getText().trim();
              if (changed && prefix.equals(current)) {
                binding.setUserInput(field.getText());
              }
            })).exceptionally(t -> {
              log.debug("findPlayersByLoginPrefix failed for {}", prefix, t);
              return null;
            });
      });
      searchDebounce.play();
    });
  }

  /**
   * Resolve a selection chosen from the autocomplete popup back to a player
   * id. The argument may be either the bare login or the "[CLAN] Name" form
   * produced by the popup decorator.
   */
  public Optional<Integer> resolveSelection(String selectionOrTypedText) {
    if (selectionOrTypedText == null || selectionOrTypedText.isBlank()) {
      return Optional.empty();
    }
    String bareName = stripClanDecoration(selectionOrTypedText);
    String lc = bareName.toLowerCase(Locale.US);
    return Optional.ofNullable(nameToId.get(lc));
  }

  /** Display-case login for a known player id, if the binder has seen them. */
  public Optional<String> displayNameForId(int playerId) {
    for (Map.Entry<String, Integer> e : nameToId.entrySet()) {
      if (e.getValue() != null && e.getValue() == playerId) {
        return Optional.ofNullable(displayName.get(e.getKey()));
      }
    }
    return Optional.empty();
  }

  /** Strip a "[CLAN] " decoration from a display string. Public so callers
   *  can use it on raw text the user typed (without invoking the popup). */
  public static String stripClanDecoration(String decorated) {
    if (decorated == null) {
      return "";
    }
    int rb = decorated.indexOf("] ");
    if (decorated.startsWith("[") && rb > 0) {
      return decorated.substring(rb + 2).trim();
    }
    return decorated.trim();
  }

  private boolean matchesPrefix(String lcName, String lcPrefix) {
    if (lcName.startsWith(lcPrefix)) {
      return true;
    }
    String lcClan = clanLower.get(lcName);
    if (lcClan != null && lcClan.startsWith(lcPrefix)) {
      return true;
    }
    if (!lcPrefix.contains("_")) {
      for (String part : lcName.split("_")) {
        if (part.startsWith(lcPrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  private String displayCaseFor(String lcName) {
    String cached = displayName.get(lcName);
    return cached != null ? cached : lcName;
  }

  private String displayWithClan(String lcName) {
    String name = displayCaseFor(lcName);
    String lcClan = clanLower.get(lcName);
    if (lcClan == null) {
      return name;
    }
    return "[" + lcClan.toUpperCase(Locale.US) + "] " + name;
  }
}
