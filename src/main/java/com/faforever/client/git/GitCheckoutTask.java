package com.faforever.client.git;

import com.faforever.client.task.CompletableTask;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Objects;


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GitCheckoutTask extends CompletableTask<Void> implements ProgressMonitor {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private File local;
  private String branchName;
  private String progressTitle;
  private boolean doPull;
  private Git git;

  public GitCheckoutTask setLocal(File local) {
    this.local = local;
    return this;
  }

  public GitCheckoutTask setBranchName(String branchName) {
    this.branchName = branchName;
    return this;
  }

  public GitCheckoutTask setProgressTitle(String progressTitle) {
    this.progressTitle = progressTitle;
    return this;
  }

  public GitCheckoutTask setDoPull(boolean doPull) {
    this.doPull = doPull;
    return this;
  }

  public GitCheckoutTask setGit(Git git) {
    this.git = git;
    return this;
  }

  @Inject
  public GitCheckoutTask() {
    super(Priority.HIGH);
    this.doPull = false;
  }

  @Override
  protected Void call() throws Exception {
    if (git == null) {
      Objects.requireNonNull(local, "either 'local' or 'git' must be set");
    }
    Objects.requireNonNull(branchName, "branch has not been set");

    logger.info("Checking out {}", branchName);
    if (git == null) {
      git = Git.open(local);
    }

    BuiltinLFS.register();
    BuiltinLFS.getInstance().getInstallCommand()
        .setRepository(git.getRepository())
        .call();

    if (progressTitle != null) {
      updateTitle(progressTitle);
    }

    if (git.branchList().call().stream()
        .map(Ref::getName)
        .anyMatch(name -> name.equals("refs/heads/" + branchName))) {

      git.checkout()
          .setForced(true)
          .setName(branchName)
          .setForceRefUpdate(true)
          .setProgressMonitor(this)
          .call();

      if (this.doPull) {
        git.pull()
            .setProgressMonitor(this)
            .call();
      }

    }
    else {
      git.branchCreate()
          .setName(branchName)
          .setStartPoint("origin/"+ branchName)
          .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
          .call();
      git.checkout()
          .setForced(true)
          .setName(branchName)
          .setProgressMonitor(this)
          .call();
    }

    return null;
  }

  int totalTasks;
  public void start(int totalTasks) {
    this.totalTasks = totalTasks;
  }

  int totalWork;
  int completedWork;
  public void beginTask(String title, int totalWork) {
    this.completedWork = 0;
    this.totalWork = totalWork;
    updateProgress(0, totalWork);
  }

  public void update(int completed) {
    this.completedWork += completed;
    updateProgress(completedWork, totalWork);
  }

  public void endTask() {
    updateProgress(totalWork, totalWork);
  }

  public boolean isCancelled() {
    return false;
  }
}
