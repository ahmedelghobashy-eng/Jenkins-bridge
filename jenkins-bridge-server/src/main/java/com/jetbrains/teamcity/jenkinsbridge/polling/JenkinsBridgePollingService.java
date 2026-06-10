package com.jetbrains.teamcity.jenkinsbridge.polling;

import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import com.jetbrains.teamcity.jenkinsbridge.persistence.BuildMirror;
import com.jetbrains.teamcity.jenkinsbridge.persistence.BuildMirrorStore;
import com.jetbrains.teamcity.jenkinsbridge.persistence.SyncState;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsLogChunk;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import com.jetbrains.teamcity.jenkinsbridge.teamcity.TeamCityBuildMirrorService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
  private final BuildMirrorStore mirrorStore;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private ScheduledExecutorService executorService;

  public JenkinsBridgePollingService(
      JenkinsBridgeSettingsProvider settingsProvider,
      JenkinsClient jenkinsClient,
      TeamCityBuildMirrorService mirrorService,
      BuildMirrorStore mirrorStore
  ) {
    this.settingsProvider = settingsProvider;
    this.jenkinsClient = jenkinsClient;
    this.mirrorService = mirrorService;
    this.mirrorStore = mirrorStore;
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

    LOG.info("Starting Jenkins Bridge polling; state file: " + mirrorStore.getStateFile());
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
      mirrorStore.markPollSuccess();
      LOG.info("[Jenkins Bridge DEBUG] Poll cycle completed");
    } catch (Exception e) {
      mirrorStore.markPollError(e);
      LOG.log(Level.WARNING, "Jenkins Bridge polling failed", e);
    }
  }

  private void pollOnce() throws Exception {
    JenkinsBridgeSettings settings = settingsProvider.load();
    if (!settings.hasMinimumConfiguration()) {
      throw new IllegalStateException(settings.describeMinimumConfigurationProblem());
    }

    String job = settings.getJenkinsJob();

    // Fetch the most recent build numbers (Jenkins caps this at the 100 newest).
    List<Integer> numbers = jenkinsClient.getBuildNumbers(job);
    if (numbers.isEmpty()) {
      LOG.info("[Jenkins Bridge DEBUG] Jenkins job " + job + " has no builds yet");
      return;
    }

    int latest = Collections.max(numbers);
    int lastSeen = mirrorStore.getLastSeenBuildNumber(job);

    if (lastSeen == 0) {
      // Cold start: don't replay the whole history. Backfill only the most recent builds, where
      // recentBuildLimit is the backfill depth (default 1 = start from the latest build).
      lastSeen = Math.max(0, latest - settings.getRecentBuildLimit());
      LOG.info("[Jenkins Bridge DEBUG] Cold start for job " + job
          + "; backfilling from build " + (lastSeen + 1) + " (latest=" + latest + ")");
    } else if (Collections.min(numbers) > lastSeen + 1) {
      // We were running before but more than ~100 builds have happened since, so the cheap
      // builds[number] view no longer reaches back to lastSeen. Escalate to allBuilds so we
      // never skip a build (rare; only after a long outage).
      LOG.info("[Jenkins Bridge DEBUG] Gap detected for job " + job
          + " (lastSeen=" + lastSeen + ", oldest fetched=" + Collections.min(numbers)
          + "); fetching all build numbers");
      numbers = jenkinsClient.getAllBuildNumbers(job);
    }

    // Process every build strictly after the watermark, oldest first.
    // TODO caveat : the builds in Jenkins restart after a certain limit. This has to be considered.
    List<Integer> toProcess = new ArrayList<Integer>();
    for (Integer number : numbers) {
      if (number > lastSeen) {
        toProcess.add(number);
      }
    }
    Collections.sort(toProcess);
    LOG.info("[Jenkins Bridge DEBUG] Job " + job + ": " + toProcess.size()
        + " build(s) after watermark " + lastSeen);

    Set<Integer> handled = new HashSet<Integer>();
    int maxNumber = lastSeen;

    for (Integer number : toProcess) {
      maxNumber = Math.max(maxNumber, number);

      BuildMirror existing = mirrorStore.findMirror(BuildMirrorStore.buildKey(job, number));
      if (existing != null && existing.getSyncState() == SyncState.TEAMCITY_FINISHED) {
        // Already mirrored and finished: account for it (advance watermark) without any Jenkins calls (P2).
        continue;
      }

      syncOneBuild(job, number);
      handled.add(number);
    }

    // Keep syncing builds that are still in progress but already past the watermark.
    for (BuildMirror active : mirrorStore.getActiveMirrors(job)) {
      if (handled.contains(active.getJenkinsBuildNumber())) {
        continue;
      }
      syncOneBuild(job, active.getJenkinsBuildNumber());
    }

    if (maxNumber > mirrorStore.getLastSeenBuildNumber(job)) {
      mirrorStore.setLastSeenBuildNumber(job, maxNumber);
    }
  }

  // Syncs a single Jenkins build, isolating failures so one bad build does not abort the poll cycle.
  private void syncOneBuild(String job, int buildNumber) {
    BuildMirror mirror = null;
    try {
      JenkinsBuildInfo buildInfo = jenkinsClient.getBuildInfo(job, buildNumber);
      mirror = mirrorStore.getOrCreateMirror(job, buildInfo);
      LOG.info("[Jenkins Bridge DEBUG] Syncing Jenkins build " + mirror.getJenkinsBuildKey()
          + " in state " + mirror.getSyncState()
          + " with TeamCity build id " + mirror.getTeamCityBuildId());
      syncBuild(mirror, buildInfo);
      LOG.info("[Jenkins Bridge DEBUG] Synced Jenkins build " + mirror.getJenkinsBuildKey()
          + " now in state " + mirror.getSyncState()
          + " with TeamCity build id " + mirror.getTeamCityBuildId());
    } catch (Exception e) {
      if (mirror != null) {
        mirrorStore.markBuildError(mirror, e);
      }
      LOG.log(Level.WARNING, "Failed to sync Jenkins build " + job + "#" + buildNumber, e);
    }
  }

  private void syncBuild(BuildMirror mirror, JenkinsBuildInfo buildInfo) throws Exception {
    long teamCityBuildId = mirrorService.ensureTeamCityBuild(mirror, buildInfo);
    mirrorService.ensureRunningDataSent(mirror, teamCityBuildId);
    mirrorService.ensureMetadataLogSent(mirror, teamCityBuildId);

    long start = Math.max(0L, mirror.getLastLogOffset());
    JenkinsLogChunk logChunk = jenkinsClient.getProgressiveLog(
        mirror.getJenkinsJob(), mirror.getJenkinsBuildNumber(), start);
    LOG.info("[Jenkins Bridge DEBUG] Fetched " + logChunk.getText().length()
        + " new console character(s) for " + mirror.getJenkinsBuildKey()
        + " from byte offset " + start + " (nextStart=" + logChunk.getNextStart() + ")");
    mirrorService.syncLogs(mirror, teamCityBuildId, logChunk);

    if (!buildInfo.isBuilding()
        && !mirror.isTestsSynced()
        && mirror.getSyncState() != SyncState.TEAMCITY_FINISHED) {
      JenkinsTestReport testReport = jenkinsClient.getTestReport(mirror.getJenkinsJob(), mirror.getJenkinsBuildNumber());
      LOG.info("[Jenkins Bridge DEBUG] Read " + testReport.getTestCount()
          + " Jenkins test(s) for " + mirror.getJenkinsBuildKey());
      mirrorService.syncTestsIfNeeded(mirror, teamCityBuildId, testReport);
    }
    mirrorService.finishBuildIfNeeded(mirror, teamCityBuildId, buildInfo);
  }
}
