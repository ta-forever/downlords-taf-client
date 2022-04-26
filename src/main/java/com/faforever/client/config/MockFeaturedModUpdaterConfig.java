package com.faforever.client.config;

import com.faforever.client.FafClientApplication;
import com.faforever.client.map.MapService;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.patch.FeaturedModUpdater;
import com.faforever.client.patch.GameUpdater;
import com.faforever.client.patch.GameUpdaterImpl;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.FafService;
import com.faforever.client.task.TaskService;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(FafClientApplication.PROFILE_OFFLINE)
@AllArgsConstructor
public class MockFeaturedModUpdaterConfig {

  private final ApplicationContext applicationContext;
  private final TaskService taskService;
  private final FafService fafService;
  private final MapService mapService;
  private final FeaturedModUpdater featuredModUpdater;
  private final NotificationService notificationService;
  private final PreferencesService preferencesService;

  @Bean
  GameUpdater gameUpdater() {
    return new GameUpdaterImpl(
        applicationContext, taskService, fafService, mapService, notificationService, preferencesService)
        .addFeaturedModUpdater(featuredModUpdater);
  }
}
