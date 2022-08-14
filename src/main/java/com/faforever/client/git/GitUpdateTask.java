package com.faforever.client.git;

import com.faforever.client.task.CompletableTask;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GitUpdateTask extends CompletableTask<Void> implements ProgressMonitor {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private File local;
  private Git git;
  private String progressTitle;

  public GitUpdateTask setLocal(File local) {
    this.local = local;
    return this;
  }

  public GitUpdateTask setGit(Git git) {
    this.git = git;
    return this;
  }

  public GitUpdateTask setProgressTitle(String progressTitle) {
    this.progressTitle = progressTitle;
    return this;
  }

  @Inject
  public GitUpdateTask() {
    super(Priority.HIGH);
  }

  @Override
  protected Void call() throws Exception {
    if (git == null) {
      Objects.requireNonNull(local, "either 'local' or 'git' must be set");
    }

    if (git == null) {
      git = Git.open(local);
    }

    BuiltinLFS.register();
    BuiltinLFS.getInstance().getInstallCommand()
        .setRepository(git.getRepository())
        .call();

    String branchName = git.getRepository().getBranch();
    logger.info("Updating {}", branchName);
    if (progressTitle != null) {
      updateTitle(progressTitle);
    }

    try {
      git.pull()
          .setProgressMonitor(this)
          .call();
    }
    catch (org.eclipse.jgit.api.errors.CheckoutConflictException e) {
      logger.info("git pull {} had conflicts", branchName);
      e.getConflictingPaths().forEach((path) -> {
        Path fullPath = git.getRepository().getDirectory().toPath().getParent().resolve(path);
        try {
          Files.delete(fullPath);
          logger.info("{}: local removed to make way for versioned upstream", fullPath);
        } catch (IOException ioException) {
          logger.warn("{}: unable to remove!", fullPath);
        }
      });

      logger.info("Updating after removal of conflicts {}", branchName);
      git.pull()
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

  private List<String> getFileList(Repository repository, String ref) throws IOException {
    RevWalk walk = new RevWalk(repository);
    RevCommit commit = walk.parseCommit(repository.resolve(ref));
    RevTree tree = commit.getTree();
    logger.info("Having tree: " + tree);

    TreeWalk treeWalk = new TreeWalk(repository);
    treeWalk.addTree(tree);
    treeWalk.setRecursive(true);
    List<String> result = new ArrayList<>();
    while (treeWalk.next()) {
      logger.info("found: " + treeWalk.getPathString());
      result.add(treeWalk.getPathString());
    }
    return result;
  }
}
