package com.faforever.client.git;

import com.faforever.client.task.CompletableTask;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.SneakyThrows;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Objects;


@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GitCloneTask extends CompletableTask<Void> implements ProgressMonitor {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private String remoteUrl;
  private File local;
  private String branchName;
  private String progressTitle;
  private boolean deleteExisting;

  public GitCloneTask setRemoteUrl(String remoteUrl) {
    this.remoteUrl = remoteUrl;
    return this;
  }

  public GitCloneTask setLocal(File local) {
    this.local = local;
    return this;
  }

  public GitCloneTask setBranchName(String branchName) {
    this.branchName = branchName;
    return this;
  }

  public GitCloneTask setProgressTitle(String progressTitle) {
    this.progressTitle = progressTitle;
    return this;
  }

  public GitCloneTask setDeleteExisting(boolean deleteExisting) {
    this.deleteExisting = deleteExisting;
    return this;
  }

  @Inject
  public GitCloneTask() {
    super(Priority.HIGH);
    this.deleteExisting = false;
  }

  @Override
  protected Void call() throws Exception {
    Objects.requireNonNull(remoteUrl, "remote has not been set");
    Objects.requireNonNull(local, "local has not been set");
    Objects.requireNonNull(branchName, "branch has not been set");

    if (local.exists()) {
       if (this.deleteExisting) {
         logger.info("{} already exists. nuking", local);
         FileUtils.deleteDirectory(local);
         FileUtils.forceMkdir(local);
       }
       else {
         logger.info("{} already exists. init'ing in place", local);
       }
    }
    else {
      logger.info("{} not exists. creating", local);
      FileUtils.forceMkdir(local);
    }
    logger.info("Cloning {} to {}", remoteUrl, local);
    if (progressTitle != null) {
      updateTitle(progressTitle);
    }

    if (true) {
      Git git = Git.init()
          .setDirectory(local)
          .call();

      BuiltinLFS.register();
      BuiltinLFS.getInstance()
          .getInstallCommand()
          .setRepository(git.getRepository())
          .call();

      git.remoteAdd()
          .setName("origin")
          .setUri(new URIish(remoteUrl))
          .call();

      git.fetch()
          .setRemote("origin")
          .setProgressMonitor(this)
          .call();

      git.branchCreate()
          .setName(branchName)
          .setStartPoint("origin/" + branchName)
          .setUpstreamMode(SetupUpstreamMode.TRACK)
          .call();

      git.checkout()
          .setForced(true)
          .setName(branchName)
          .setProgressMonitor(this)
          .call();

      if (git.getRepository().resolve(Constants.HEAD) != null) {
        String head = git.getRepository().resolve(Constants.HEAD).getName();
        logger.info("head={}", head);
      }

      return null;
    }
    else {

      CloneCommand cloneCommand = Git.cloneRepository()
          .setBare(false)
          .setURI(remoteUrl)
          .setDirectory(local)
          .setBranch(branchName)
          .setProgressMonitor(this);

      BuiltinLFS.register();
      BuiltinLFS.getInstance()
          .getInstallCommand()
          .setRepository(cloneCommand.getRepository())
          .call();


      if (remoteUrl.startsWith("ssh")) {
        SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
          @Override
          protected void configure(Host host, Session session) {
          }

          @SneakyThrows
          @Override
          protected JSch createDefaultJSch(FS fs) throws JSchException {
            String sshHome = new ClassPathResource("/.ssh").getURI().getPath();
            fs.setUserHome(new File(sshHome).getParentFile());
            JSch jsch = super.createDefaultJSch(fs);
            return jsch;
          }
        };
        SshSessionFactory.setInstance(sshSessionFactory);

        cloneCommand.setTransportConfigCallback(transport -> {
          SshTransport sshTransport = (SshTransport) transport;
          sshTransport.setSshSessionFactory(sshSessionFactory);
        });
      }

      Git git = cloneCommand.call();
      return null;
    }
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
