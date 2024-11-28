package com.faforever.client.ui.tray;

import com.faforever.client.task.CompletableTask;
import com.faforever.client.ui.StageHolder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ProgressIndicator;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/*
 * A task that just holds INDETERMINATE_PROGRESS until JavaFX Stage indicates isFocused
 * with the idea that on Windows the taskbar icon will flash as long as INDETERMINATE_PROGRESS is indicated.
 * (see WindowsTaskbarProgressUpdater)
 * This is a workaround for TrayIconManager unable to change taskbar icon on Windows
 * (due to Install4j launcher? not sure)
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TaskBarFlashTask extends CompletableTask<Void> {

  private final CountDownLatch latch = new CountDownLatch(1);

  private final ChangeListener<Boolean> onFocusedListener = (obs, oldValue, newValue) -> {
    if (newValue) {
      latch.countDown();
    }
  };

  public TaskBarFlashTask() {
    super(Priority.LOW);
    Platform.runLater(() -> StageHolder.getStage().focusedProperty().addListener(onFocusedListener));
  }

  @Override
  protected Void call() throws Exception {
    updateProgress(0.0, ProgressIndicator.INDETERMINATE_PROGRESS);
    if (!StageHolder.getStage().isFocused()) {
      latch.await();
    }
    Platform.runLater(() -> StageHolder.getStage().focusedProperty().removeListener(onFocusedListener));
    return null;
  }

  @Override
  protected void cancelled() {
    super.cancelled();
    latch.countDown();
    Platform.runLater(() -> StageHolder.getStage().focusedProperty().removeListener(onFocusedListener));
  }

  @Override
  protected void failed() {
    super.failed();
    Platform.runLater(() -> StageHolder.getStage().focusedProperty().removeListener(onFocusedListener));
  }

  @Override
  protected void succeeded() {
    super.succeeded();
  }
}
