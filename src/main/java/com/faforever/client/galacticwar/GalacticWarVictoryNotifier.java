package com.faforever.client.galacticwar;

import com.faforever.client.game.Faction;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.OpenGalacticWarEvent;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.PersistentNotification;
import com.faforever.client.notification.Severity;
import com.faforever.client.notification.TransientNotification;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.theme.UiService;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javafx.scene.image.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Announces the end of a Galactic War: when a freshly fetched scenario carries a
 * last_galaxy_winner for an iteration the user hasn't been told about yet, shows a
 * transient toast plus a persistent notification linking to the Galactic War tab.
 * The in-tab victory splash (GalaxyViewController) has its own, separate dismissal
 * tracking so it still greets players who arrive via the notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GalacticWarVictoryNotifier implements InitializingBean {

  private final EventBus eventBus;
  private final NotificationService notificationService;
  private final I18n i18n;
  private final PreferencesService preferencesService;
  private final UiService uiService;

  @Override
  public void afterPropertiesSet() {
    eventBus.register(this);
  }

  @Subscribe
  public void onGalacticWarWinnerChanged(GalacticWarWinnerChangedEvent event) {
    Integer alreadyNotified = preferencesService.getPreferences()
        .getGalacticWarNotifiedIterations()
        .get(event.galaxyTechnicalName());
    if (alreadyNotified != null && alreadyNotified >= event.iteration()) {
      return;
    }
    preferencesService.getPreferences()
        .getGalacticWarNotifiedIterations()
        .put(event.galaxyTechnicalName(), event.iteration());
    preferencesService.storeInBackground();

    Faction faction = Faction.fromString(event.factionName());
    String factionDisplayName = faction != null ? faction.getString() : event.factionName();

    log.info("[onGalacticWarWinnerChanged] {} won galaxy {} (iteration {})",
        factionDisplayName, event.galaxyTechnicalName(), event.iteration());

    notificationService.addNotification(new TransientNotification(
        i18n.get("galacticWar.victory.notification.title", event.galaxyDisplayName()),
        i18n.get("galacticWar.victory.notification", factionDisplayName, event.galaxyDisplayName()),
        getFactionImage(faction),
        actionEvent -> eventBus.post(new OpenGalacticWarEvent())));

    notificationService.addNotification(new PersistentNotification(
        i18n.get("galacticWar.victory.notification", factionDisplayName, event.galaxyDisplayName()),
        Severity.INFO,
        Collections.singletonList(new Action(
            i18n.get("galacticWar.victory.view"),
            actionEvent -> eventBus.post(new OpenGalacticWarEvent())))));
  }

  private Image getFactionImage(Faction faction) {
    if (faction == Faction.ARM) {
      return uiService.getThemeImage(UiService.ARM_ICON_IMAGE_LARGE);
    }
    if (faction == Faction.CORE) {
      return uiService.getThemeImage(UiService.CORE_ICON_IMAGE_LARGE);
    }
    return uiService.getThemeImage(UiService.GOK_ICON_IMAGE_LARGE);
  }
}
