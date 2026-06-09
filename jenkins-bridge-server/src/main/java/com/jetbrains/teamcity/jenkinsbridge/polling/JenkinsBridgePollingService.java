package com.jetbrains.teamcity.jenkinsbridge.polling;

import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMapping;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMappingStore;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildState;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
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

  private final JenkinsBridgeSettingsProvider settingsProvider;
  private final JenkinsClient jenkinsClient;
  private final TeamCityBuildMirrorService mirrorService;
  private final JenkinsBuildMappingStore mappingStore;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private ScheduledExecutorService executorService;

  public JenkinsBridgePollingService(
      JenkinsBridgeSettingsProvider settingsProvider,
      JenkinsClient jenkinsClient,
      TeamCityBuildMirrorService mirrorService,
      JenkinsBuildMappingStore mappingStore
  ) {
    this.settingsProvider = settingsProvider;
    this.jenkinsClient = jenkinsClient;
    this.mirrorService = mirrorService;
    this.mappingStore = mappingStore;
  }

  public void start() {
    JenkinsBridgeSettings settings = settingsProvider.load();
    LOG.info("[Jenkins Bridge DEBUG] Loaded settings: " + settings.describeForLog());

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
    LOG.info("[Jenkins Bridge DEBUG] Scheduling poller every " + settings.getPollSeconds()
        + " seconds for Jenkins job " + settings.getJenkinsJob()
        + " and TeamCity build type " + settings.getTeamCityBuildTypeId());
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
      LOG.info("[Jenkins Bridge DEBUG] Poll cycle started");
      pollOnce();
      mappingStore.markPollSuccess();
      LOG.info("[Jenkins Bridge DEBUG] Poll cycle completed");
    } catch (Exception e) {
      mappingStore.markPollError(e);
      LOG.log(Level.WARNING, "Jenkins Bridge polling failed", e);
    }
  }

  private void pollOnce() throws Exception {
    JenkinsBridgeSettings settings = settingsProvider.load();
    if (!settings.hasMinimumConfiguration()) {
      throw new IllegalStateException(settings.describeMinimumConfigurationProblem());
    }

    // TODO for this POC we just poll the recent builds within a certain limit,
    // We should consider polling since the last build number we polled.
    List<JenkinsBuildInfo> recentBuilds = jenkinsClient.getRecentBuilds(
        settings.getJenkinsJob(),
        settings.getRecentBuildLimit()
    );
    LOG.info("[Jenkins Bridge DEBUG] Jenkins returned " + recentBuilds.size()
        + " recent build(s) for job " + settings.getJenkinsJob());
    Collections.reverse(recentBuilds);

    for (JenkinsBuildInfo recentBuild : recentBuilds) {
      JenkinsBuildInfo buildInfo = jenkinsClient.getBuildInfo(settings.getJenkinsJob(), recentBuild.getNumber());
      JenkinsBuildMapping mapping = mappingStore.getOrCreateMapping(settings.getJenkinsJob(), buildInfo);
      try {
        LOG.info("[Jenkins Bridge DEBUG] Syncing Jenkins build " + mapping.getJenkinsBuildKey()
            + " in state " + mapping.getState()
            + " with TeamCity build id " + mapping.getTeamCityBuildId());
        syncBuild(mapping, buildInfo);
        LOG.info("[Jenkins Bridge DEBUG] Synced Jenkins build " + mapping.getJenkinsBuildKey()
            + " now in state " + mapping.getState()
            + " with TeamCity build id " + mapping.getTeamCityBuildId());
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
    LOG.info("[Jenkins Bridge DEBUG] Read " + consoleText.length()
        + " console character(s) for " + mapping.getJenkinsBuildKey()
        + "; previous offset " + mapping.getLastLogOffset());
    mirrorService.syncLogs(mapping, teamCityBuildId, consoleText);
    if (!buildInfo.isBuilding()
        && !mapping.isTestsSynced()
        && !JenkinsBuildState.TEAMCITY_FINISHED.equals(mapping.getState())) {
      JenkinsTestReport testReport = jenkinsClient.getTestReport(mapping.getJenkinsJob(), mapping.getJenkinsBuildNumber());
      LOG.info("[Jenkins Bridge DEBUG] Read " + testReport.getTestCount()
          + " Jenkins test(s) for " + mapping.getJenkinsBuildKey());
      mirrorService.syncTestsIfNeeded(mapping, teamCityBuildId, testReport);
    }

    // I think here I should have a sync status if needed..
    mirrorService.finishBuildIfNeeded(mapping, teamCityBuildId, buildInfo);
  }
}
