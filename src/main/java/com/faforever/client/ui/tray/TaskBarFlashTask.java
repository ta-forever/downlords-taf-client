package com.faforever.client.ui.tray;

import com.faforever.client.task.CompletableTask;
import com.faforever.client.ui.StageHolder;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ProgressIndicator;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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

  ChangeListener<Boolean> onFocusedListener;
  Integer semaphore;

  TaskBarFlashTask() {
    super(Priority.LOW);
    semaphore = 0;
    onFocusedListener = (obs, oldValue, newValue) -> {
      if (newValue) {
        synchronized(semaphore) {
          semaphore.notify();
        }
      }
    };
  }

  @Override
  protected Void call() throws Exception {
    updateProgress(0.0, ProgressIndicator.INDETERMINATE_PROGRESS);
    StageHolder.getStage().focusedProperty().addListener(onFocusedListener);
    if (!StageHolder.getStage().isFocused()) {
      synchronized (semaphore) {
        semaphore.wait();
      }
    }
    StageHolder.getStage().focusedProperty().removeListener(onFocusedListener);
    return null;
  }
}
