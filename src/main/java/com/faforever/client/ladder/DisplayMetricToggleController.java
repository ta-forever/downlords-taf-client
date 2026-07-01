package com.faforever.client.ladder;

import com.faforever.client.fx.Controller;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.DisplayMetric;
import com.faforever.client.preferences.PreferencesService;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The reusable "Season Ladder ⇄ Skill Rating" pill (LADDER_POINTS_DESIGN §13.1). It is a thin view
 * over the single global {@code displayMetric} preference: every instance binds to the same pref, so
 * one in the main top bar plus a one-line {@code <fx:include>} in any dialog all stay in lock-step,
 * and flipping any of them updates every surface that listens to the pref. No per-surface toggle
 * logic needed.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class DisplayMetricToggleController implements Controller<Node> {

  private final PreferencesService preferencesService;
  private final I18n i18n;

  public HBox root;
  public ToggleGroup group;
  public ToggleButton ladderToggle;
  public ToggleButton ratingToggle;

  private ChangeListener<DisplayMetric> prefListener;

  @Override
  public void initialize() {
    ladderToggle.setTooltip(new Tooltip(i18n.get("leaderboard.toggle.lp.tooltip")));
    ratingToggle.setTooltip(new Tooltip(i18n.get("leaderboard.toggle.ratings.tooltip")));

    // Never let the pill shrink below its label width — in tight headers (e.g. the Manage dialog,
    // next to a growing hint label) the toggles would otherwise truncate to ".../...".
    ladderToggle.setMinWidth(Region.USE_PREF_SIZE);
    ratingToggle.setMinWidth(Region.USE_PREF_SIZE);
    root.setMinWidth(Region.USE_PREF_SIZE);

    applyFromPref();

    group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
      if (newToggle == null) {              // a ToggleGroup allows deselecting all; keep one active
        if (oldToggle != null) {
          oldToggle.setSelected(true);
        }
        return;
      }
      DisplayMetric metric = newToggle == ratingToggle ? DisplayMetric.RATINGS : DisplayMetric.LADDER_POINTS;
      if (preferencesService.getPreferences().getDisplayMetric() != metric) {
        preferencesService.getPreferences().setDisplayMetric(metric);
        preferencesService.storeInBackground();
      }
    });

    // Keep this instance in sync when the pref is flipped elsewhere (another pill, settings, …).
    prefListener = (obs, oldValue, newValue) -> applyFromPref();
    JavaFxUtil.addListener(preferencesService.getPreferences().displayMetricProperty(),
        new WeakChangeListener<>(prefListener));
  }

  private void applyFromPref() {
    boolean lpMode = preferencesService.getPreferences().getDisplayMetric() != DisplayMetric.RATINGS;
    Toggle desired = lpMode ? ladderToggle : ratingToggle;
    if (!desired.isSelected()) {
      ((ToggleButton) desired).setSelected(true);
    }
  }

  @Override
  public Node getRoot() {
    return root;
  }
}
