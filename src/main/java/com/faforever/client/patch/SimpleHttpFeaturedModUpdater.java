package com.faforever.client.patch;

import com.faforever.client.FafClientApplication;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.mod.FeaturedModVersion;
import com.faforever.client.task.TaskService;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


@Lazy
@Component
@Profile("!" + FafClientApplication.PROFILE_OFFLINE)
@RequiredArgsConstructor
public class SimpleHttpFeaturedModUpdater implements FeaturedModUpdater {

  private final TaskService taskService;
  private final ApplicationContext applicationContext;

  @Override
  public CompletableFuture<String> updateMod(FeaturedMod featuredMod, @Nullable String version) {
    SimpleHttpFeaturedModUpdaterTask task = applicationContext.getBean(SimpleHttpFeaturedModUpdaterTask.class);
    task.setVersion(Integer.valueOf(version));
    task.setFeaturedMod(featuredMod);

    return taskService.submitTask(task).getFuture();
  }

  @Override
  public boolean canUpdate(FeaturedMod featuredMod) {
    return true;
  }
}
