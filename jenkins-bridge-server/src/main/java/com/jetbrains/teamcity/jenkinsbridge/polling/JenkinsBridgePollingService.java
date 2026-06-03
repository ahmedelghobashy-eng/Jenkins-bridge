package com.jetbrains.teamcity.jenkinsbridge.polling;

import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMapping;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMappingStore;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.teamcity.TeamCityBuildMirrorService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsBridgePollingService {
  private static final Logger LOG = Logger.getLogger(JenkinsBridgePollingService.class.getName());

  private final JenkinsBridgeSettings settings;
  private final JenkinsClient jenkinsClient;
  private final TeamCityBuildMirrorService mirrorService;
  private final JenkinsBuildMappingStore mappingStore;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private ScheduledExecutorService executorService;

  public JenkinsBridgePollingService(
      JenkinsBridgeSettings settings,
      JenkinsClient jenkinsClient,
      TeamCityBuildMirrorService mirrorService,
      JenkinsBuildMappingStore mappingStore
  ) {
    this.settings = settings;
    this.jenkinsClient = jenkinsClient;
    this.mirrorService = mirrorService;
    this.mappingStore = mappingStore;
  }

  public void start() {
    if (!settings.isEnabled()) {
      LOG.info("Jenkins Bridge polling is disabled");
      return;
    }

    if (!started.compareAndSet(false, true)) {
      return;
    }

    executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "jenkins-bridge-poller");
        thread.setDaemon(true);
        return thread;
      }
    });

    LOG.info("Starting Jenkins Bridge polling; state file: " + mappingStore.getStateFile());
    executorService.scheduleWithFixedDelay(new Runnable() {
      public void run() {
        pollOnceSafely();
      }
    }, 0L, settings.getPollSeconds(), TimeUnit.SECONDS);
  }

  public void stop() {
    if (!started.compareAndSet(true, false)) {
      return;
    }

    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  private void pollOnceSafely() {
    try {
      pollOnce();
      mappingStore.markPollSuccess();
    } catch (Exception e) {
      mappingStore.markPollError(e);
      LOG.log(Level.WARNING, "Jenkins Bridge polling failed", e);
    }
  }

  private void pollOnce() throws Exception {
    if (!settings.hasMinimumConfiguration()) {
      throw new IllegalStateException(settings.describeMinimumConfigurationProblem());
    }

    List<JenkinsBuildInfo> recentBuilds = jenkinsClient.getRecentBuilds(
        settings.getJenkinsJob(),
        settings.getRecentBuildLimit()
    );
    Collections.reverse(recentBuilds);

    for (JenkinsBuildInfo recentBuild : recentBuilds) {
      JenkinsBuildInfo buildInfo = jenkinsClient.getBuildInfo(settings.getJenkinsJob(), recentBuild.getNumber());
      JenkinsBuildMapping mapping = mappingStore.getOrCreateMapping(settings.getJenkinsJob(), buildInfo);
      try {
        syncBuild(mapping, buildInfo);
      } catch (Exception e) {
        mappingStore.markBuildError(mapping, e);
        LOG.log(Level.WARNING, "Failed to sync Jenkins build " + mapping.getJenkinsBuildKey(), e);
      }
    }
  }

  private void syncBuild(JenkinsBuildMapping mapping, JenkinsBuildInfo buildInfo) throws Exception {
    long teamCityBuildId = mirrorService.ensureTeamCityBuild(mapping, buildInfo);
    mirrorService.ensureRunningDataSent(mapping, teamCityBuildId);
    mirrorService.ensureMetadataLogSent(mapping, teamCityBuildId);

    String consoleText = jenkinsClient.getConsoleText(mapping.getJenkinsJob(), mapping.getJenkinsBuildNumber());
    mirrorService.syncLogs(mapping, teamCityBuildId, consoleText);
    mirrorService.finishBuildIfNeeded(mapping, teamCityBuildId, buildInfo);
  }
}
