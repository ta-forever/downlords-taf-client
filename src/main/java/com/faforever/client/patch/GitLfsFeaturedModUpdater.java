package com.faforever.client.patch;

import com.faforever.client.FafClientApplication;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.git.GitCheckoutTask;
import com.faforever.client.git.GitCloneTask;
import com.faforever.client.git.GitUpdateTask;
import com.faforever.client.i18n.I18n;
import com.faforever.client.mod.FeaturedMod;
import com.faforever.client.notification.Action;
import com.faforever.client.notification.ImmediateNotification;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.notification.Severity;
import com.faforever.client.preferences.AskAlwaysOrNever;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Lazy
@Component
@Profile("!" + FafClientApplication.PROFILE_OFFLINE)
@RequiredArgsConstructor
@Slf4j
public class GitLfsFeaturedModUpdater implements FeaturedModUpdater {

  private final TaskService taskService;
  private final PreferencesService preferencesService;
  private final NotificationService notificationService;
  private final PlatformService platformService;
  private final I18n i18n;

  /*
   * @param version set to null to do a proactive check for updates of the currently checked out version
   *                (unless there is no currently checked out version [git not initialised]
   *                 in which case we clone the latest version)
   */
  @Override
  public CompletableFuture<String> updateMod(FeaturedMod featuredMod, @Nullable String version) {

    log.info("[updateMod]");
    TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(featuredMod.getTechnicalName());
    Path deployPath = taPrefs.getInstalledPath();
    String repoUrl = featuredMod.getGitUrl();

    Git _git;
    boolean _hasUncommittedChanges;
    try {
      log.info("[updateMod] git open");
      _git = Git.open(deployPath.toFile());
      log.info("[updateMod] git status");
      _hasUncommittedChanges = _git.status().call().hasUncommittedChanges();
    } catch (IOException | GitAPIException e1) {
      _git = null;
      _hasUncommittedChanges = false;
    }
    final Git git = _git;
    boolean hasUncommittedChanges = _hasUncommittedChanges;

    String _gitCommit;
    if (version != null) {
      _gitCommit = version;
    } else if (git != null) {
      try {
        _gitCommit = git.getRepository().getBranch();
      }
      catch (IOException e) {
        CompletableFuture<String> future = CompletableFuture.completedFuture(null);
        future.completeExceptionally(e);
        return future;
      }
    } else if (featuredMod.getGitBranch() != null) {
      _gitCommit = featuredMod.getGitBranch();
    } else {
      log.info("[updateMod] unable to determine which branch to checkout because both 'version' and 'featuredMod.getGitBranch()' are null");
      return CompletableFuture.completedFuture(null);
    }
    String gitCommit = _gitCommit;

    try {
      if (git != null) {
        if (git.getRepository().getBranch().equals(gitCommit) ||
            git.getRepository().resolve(Constants.HEAD).getName().equals(gitCommit)) {
          if (version != null && isVersionAlreadyPulled(version)) {
            log.info("[updateMod] no need to update because correct branch is checked out already and upstream branch has already been checked this session");
            return CompletableFuture.completedFuture(null);
          }
        }
      }
    } catch (IOException e) {
      log.warn("[updateMod] while checking whether to update: {}", e.getMessage());
    }

    final boolean[] okToUpdate = {true};
    final boolean[] okToReset = {true};
    log.info("[updateMod] promptOkToUpdate");
    return promptOkToUpdate(featuredMod)
        .thenApply(ok -> {
          okToUpdate[0] = ok;
          return null;
        })
        .thenCompose((aVoid) -> {
          if (okToUpdate[0] && hasUncommittedChanges) {
            return promptOkToReset(deployPath);
          } else {
            return CompletableFuture.completedFuture(false);
          }
        })
        .thenApply((ok) -> {
          okToReset[0] = ok;
          return null;
        })
        .thenCompose((aVoid) -> {
          if (!okToUpdate[0] || hasUncommittedChanges && !okToReset[0]) {
            return CompletableFuture.completedFuture(null);
          }
          else if (git == null) {
            return taskService.submitTask(new GitCloneTask()
                .setRemoteUrl(repoUrl)
                .setLocal(deployPath.toFile())
                .setBranchName(gitCommit)
                .setProgressTitle(i18n.get("checkoutFeaturedMod.progress.title", featuredMod.getDisplayName()))
            ).getFuture();
          }
          else {
            return doUpdate(git, gitCommit, hasUncommittedChanges, featuredMod, version);
          }
        })
        .exceptionally((throwable) -> {
          notificationService.addImmediateErrorNotification(throwable, "error.game.cannotUpdate",
              featuredMod.getDisplayName(), version);
          return null;
        })
        .thenApply((aVoid) -> {
          String head = getCheckedOutCommit(git, featuredMod);
          if (git != null) {
            git.close();
          }
          return head;
        });
  }

  String getCheckedOutCommit(Git git, FeaturedMod featuredMod) {
    try {
      if (git == null) {
        return featuredMod.getGitBranch();
      } else {
        return git.getRepository().resolve(Constants.HEAD).getName();
      }
    }
    catch (IOException e) {
      return featuredMod.getGitBranch();
    }
  }

  private void resetExceptUserIniFiles(Git git) throws GitAPIException, IOException {
    final String[] userIniFiles = { "TA.ini" };
    List<String> foundUserIniFiles = new ArrayList<>();
    for (String userIniFile: userIniFiles) {
      Path src = Path.of(git.getRepository().getDirectory().getParent()).resolve(userIniFile);
      Path dest = preferencesService.getCacheDirectory().resolve(userIniFile);
      if (Files.exists(src)) {
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        foundUserIniFiles.add(userIniFile);
      }
    }

    git.reset().setMode(ResetType.HARD).call();

    for (String userIniFile: foundUserIniFiles) {
      Path src = preferencesService.getCacheDirectory().resolve(userIniFile);
      Path dest = Path.of(git.getRepository().getDirectory().getParent()).resolve(userIniFile);
      if (Files.exists(src)) {
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private final Set<String> versionsAlreadyPulled = new HashSet<>();
  private boolean isVersionAlreadyPulled(String version) {
    synchronized(versionsAlreadyPulled) {
      return versionsAlreadyPulled.contains(version);
    }
  }
  private boolean setVersionAlreadyPulled(String version) {
    if (version == null) {
      return false;
    }
    synchronized(versionsAlreadyPulled) {
      boolean oldValue = versionsAlreadyPulled.contains(version);
      versionsAlreadyPulled.add(version);
      return oldValue;
    }
  }

  private CompletableFuture<Void> doUpdate(Git git, String gitCommit, boolean hasUncommitedChanges,
                                           FeaturedMod featuredMod, String version) {
    try {
      if (hasUncommitedChanges) {
        // unfortunately files like TA.ini contain both options that should be under version control
        // (eg PlayerNDotColors) and options that user may need to tweak for their specific system
        // (eg UseVideoMemory).  So we only want to reset the non-TA.ini-like files
        log.info("[doUpdate] Git reset HARD {}", git.getRepository().getDirectory());
        resetExceptUserIniFiles(git);
        hasUncommitedChanges = git.status().call().hasUncommittedChanges();
      }

      boolean stashCreated = false;
      if (hasUncommitedChanges) {
        // and we're going to stash the TA.ini-like files
        try {
          if (git.status().call().hasUncommittedChanges()) {
            log.info("[doUpdate] Git stash {}", git.getRepository().getDirectory());
            git.stashCreate().call();
            stashCreated = true;
          }
        } catch (GitAPIException e) {
          log.warn("[doUpdate] Git stash failed: {}", e.getMessage());
        }
      }

      boolean versionAlreadyPulled = setVersionAlreadyPulled(version);
      CompletableFuture<Void> future;
      if (git.getRepository().getBranch().equals(gitCommit) ||
          git.getRepository().resolve(Constants.HEAD).getName().equals(gitCommit)) {
        if (version == null || !versionAlreadyPulled) {
          // version == null is a proactive request to check for updates
          log.info("[doUpdate] checking for branch updates. version={}, gitCommit={}", version, gitCommit);
          future = taskService.submitTask(new GitUpdateTask().setGit(git)
              .setProgressTitle(i18n.get("checkoutFeaturedMod.progress.title", featuredMod.getDisplayName()))
          ).getFuture();
        }
        else {
          // this is a request to switch branches, but we're already on the correct branch
          // (and we already checked the server for updates on a previous invocation)
          log.info("[doUpdate] nothing to do. version={}, gitCommit={}", version, gitCommit);
          future = CompletableFuture.completedFuture(null);
        }
      }
      else {
        log.info("[doUpdate] switching branches. version={}, gitCommit={}", version, gitCommit);
        future = taskService.submitTask(new GitCheckoutTask()
            .setGit(git)
            .setBranchName(gitCommit)
            .setProgressTitle(i18n.get("checkoutFeaturedMod.progress.title", featuredMod.getDisplayName()))
            .setDoPull(true)
        ).getFuture();
      }

      if (stashCreated) {
        // stash-pop the TA.ini-like files
        future = future.thenRun(() -> {
          try {
            git.stashApply().call();
            git.stashDrop().call();

          } catch (GitAPIException e) {
            log.warn("[doUpdate] Git stash pop failed: {}", e.getMessage());
            log.warn("[doUpdate] Doing HARD reset");
            try {
              git.reset().setMode(ResetType.HARD).call();
              git.stashDrop().call();
            }
            catch (GitAPIException ignored) {
            }
          }
        });
      }
      return future;

    } catch (GitAPIException | IOException e) {
      notificationService.addImmediateErrorNotification(e, "error.game.cannotUpdate",
          featuredMod.getDisplayName(), version);
      return CompletableFuture.completedFuture(null);
    }
  }

  @Override
  public boolean canUpdate(FeaturedMod featuredMod) {
    return true;
  }

  private CompletableFuture<Boolean> promptOkToUpdate(FeaturedMod featuredMod) {
    TotalAnnihilationPrefs taPrefs = preferencesService.getTotalAnnihilation(featuredMod.getTechnicalName());
    if (taPrefs.getAutoUpdateEnable() == AskAlwaysOrNever.ALWAYS) {
      return CompletableFuture.completedFuture(true);
    }
    if (taPrefs.getAutoUpdateEnable() == AskAlwaysOrNever.NEVER) {
      return CompletableFuture.completedFuture(false);
    }

    CompletableFuture<Boolean> future = new CompletableFuture<>();
    List<Action> actions = List.of(
        new Action(i18n.get("yesOnce"), Action.Type.OK_DONE, (a) -> future.complete(true)),
        new Action(i18n.get("noOnce"), Action.Type.OK_DONE, (a) -> future.complete(false)),
        new Action(i18n.get("always"), Action.Type.OK_DONE, (a) -> {
          taPrefs.setAutoUpdateEnable(AskAlwaysOrNever.ALWAYS);
          preferencesService.storeInBackground();
          future.complete(true);
        }),
        new Action(i18n.get("never"), Action.Type.OK_DONE, (a) -> {
          taPrefs.setAutoUpdateEnable(AskAlwaysOrNever.NEVER);
          preferencesService.storeInBackground();
          future.complete(false);
        }),
        new Action(i18n.get("menu.revealModFolder"), Action.Type.OK_STAY, (a) ->
            this.platformService.reveal(taPrefs.getInstalledPath())));

    notificationService.addNotification(new ImmediateNotification(
        i18n.get("checkoutFeaturedMod.okToUpdate.title", featuredMod.getDisplayName()),
        i18n.get("checkoutFeaturedMod.okToUpdate.text", featuredMod.getDisplayName()),
        Severity.INFO, actions));

    return future;
  }

  private CompletableFuture<Boolean> promptOkToReset(Path folder) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();

    if (preferencesService.getPreferences().getFeaturedModRevertOption() == AskAlwaysOrNever.ALWAYS) {
      future.complete(true);
      return future;
    }
    else if (preferencesService.getPreferences().getFeaturedModRevertOption() == AskAlwaysOrNever.NEVER) {
      future.complete(false);
      return future;
    }

    List<Action> actions = List.of(
        new Action(i18n.get("yesOnce"), Action.Type.OK_DONE, (a) -> future.complete(true)),
        new Action(i18n.get("noOnce"), Action.Type.OK_DONE, (a) -> future.complete(false)),
        new Action(i18n.get("always"), Action.Type.OK_DONE, (a) -> {
          preferencesService.getPreferences().setFeaturedModRevertOption(AskAlwaysOrNever.ALWAYS);
          future.complete(true);
        }),
        new Action(i18n.get("never"), Action.Type.OK_DONE, (a) -> {
          preferencesService.getPreferences().setFeaturedModRevertOption(AskAlwaysOrNever.NEVER);
          future.complete(false);
        }),
        new Action(i18n.get("menu.revealModFolder"), Action.Type.OK_STAY, (a) ->
            this.platformService.reveal(folder)));

    notificationService.addNotification(new ImmediateNotification(
        i18n.get("checkoutFeaturedMod.localChanges.title"),
        i18n.get("checkoutFeaturedMod.localChanges.text"),
        Severity.INFO, actions));

    return future;
  }

}
