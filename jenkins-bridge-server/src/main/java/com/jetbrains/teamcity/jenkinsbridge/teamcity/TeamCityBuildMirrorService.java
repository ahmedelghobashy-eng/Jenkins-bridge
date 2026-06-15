package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import com.jetbrains.teamcity.jenkinsbridge.persistence.BuildMirror;
import com.jetbrains.teamcity.jenkinsbridge.persistence.BuildMirrorStore;
import com.jetbrains.teamcity.jenkinsbridge.persistence.StageMirror;
import com.jetbrains.teamcity.jenkinsbridge.persistence.SyncState;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsLogChunk;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStage;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStageLog;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStages;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import jetbrains.buildServer.messages.BuildMessage1;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class TeamCityBuildMirrorService {
  private static final Set<SyncState> RUNNING_DATA_ALREADY_SENT_STATES = EnumSet.of(
      SyncState.RUNNING_SENT,
      SyncState.LOG_SYNCING,
      SyncState.TEAMCITY_FINISHED
  );

  private final JenkinsBridgeSettingsProvider settingsProvider;
  private final TeamCityClient teamCityClient;
  private final TeamCityBuildQueuer teamCityBuildQueuer;
  private final TeamCityBuildStarter teamCityBuildStarter;
  private final TeamCityBuildLogger teamCityBuildLogger;
  private final TeamCityTestReporter teamCityTestReporter;
  private final TeamCityStageReporter teamCityStageReporter;
  private final TeamCityBuildFinisher teamCityBuildFinisher;
  private final BuildMirrorStore mirrorStore;

  public TeamCityBuildMirrorService(
      JenkinsBridgeSettingsProvider settingsProvider,
      TeamCityClient teamCityClient,
      TeamCityBuildQueuer teamCityBuildQueuer,
      TeamCityBuildStarter teamCityBuildStarter,
      TeamCityBuildLogger teamCityBuildLogger,
      TeamCityTestReporter teamCityTestReporter,
      TeamCityStageReporter teamCityStageReporter,
      TeamCityBuildFinisher teamCityBuildFinisher,
      BuildMirrorStore mirrorStore
  ) {
    this.settingsProvider = settingsProvider;
    this.teamCityClient = teamCityClient;
    this.teamCityBuildQueuer = teamCityBuildQueuer;
    this.teamCityBuildStarter = teamCityBuildStarter;
    this.teamCityBuildLogger = teamCityBuildLogger;
    this.teamCityTestReporter = teamCityTestReporter;
    this.teamCityStageReporter = teamCityStageReporter;
    this.teamCityBuildFinisher = teamCityBuildFinisher;
    this.mirrorStore = mirrorStore;
  }


  // May need better naming
  public long ensureTeamCityBuild(BuildMirror mirror, JenkinsBuildInfo jenkinsInfo)
      throws BridgeHttpException, IOException {
    if (mirror.getTeamCityBuildId() != null) {
      return mirror.getTeamCityBuildId();
    }

    Long restoredBuildId = teamCityClient.findBuildIdByJenkinsBuildKey(mirror.getJenkinsBuildKey());

    // If there already exists a build with the same Jenkins build key, use it

    if (restoredBuildId != null) {
      mirror.setTeamCityBuildId(restoredBuildId);
      mirror.setSyncState(SyncState.TEAMCITY_CREATED);
      mirror.setLastError(null);
      mirrorStore.saveMirror(mirror);
      return restoredBuildId;
    }

    // Else create a new build (with the build queuer) and return the build ID


    // Prepare the build properties
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put("jenkins.job", mirror.getJenkinsJob());
    properties.put("jenkins.build.number", String.valueOf(mirror.getJenkinsBuildNumber()));
    properties.put("jenkins.build.key", mirror.getJenkinsBuildKey());
    properties.put("jenkins.build.url", nullToEmpty(jenkinsInfo.getUrl()));

    long buildId = teamCityBuildQueuer.queueAgentlessBuild(mirror.getTeamCityBuildTypeId(), properties);
    mirror.setTeamCityBuildId(buildId);
    mirror.setSyncState(SyncState.TEAMCITY_CREATED);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
    return buildId;
  }

  public void ensureRunningDataSent(BuildMirror mirror, long teamCityBuildId)
      throws BridgeHttpException, IOException {
    if (RUNNING_DATA_ALREADY_SENT_STATES.contains(mirror.getSyncState())) {
      return;
    }

    String text = "Monitoring Jenkins job " + mirror.getJenkinsJob()
        + " build #" + mirror.getJenkinsBuildNumber();

    teamCityBuildStarter.markBuildAsRunning(teamCityBuildId, text);

    mirror.setSyncState(SyncState.RUNNING_SENT);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
  }

  public void ensureMetadataLogSent(BuildMirror mirror, long teamCityBuildId)
      throws BridgeHttpException, IOException {
    if (mirror.isMetadataLogSent()) {
      return;
    }

    String text = "Monitoring Jenkins job: " + mirror.getJenkinsJob() + "\n"
        + "Jenkins build number: " + mirror.getJenkinsBuildNumber() + "\n"
        + "Jenkins build key: " + mirror.getJenkinsBuildKey() + "\n"
        + "Jenkins build URL: " + nullToEmpty(mirror.getJenkinsBuildUrl()) + "\n"
        + "\n";

    teamCityBuildLogger.addBuildLog(teamCityBuildId, text);

    mirror.setMetadataLogSent(true);
    mirror.setSyncState(SyncState.LOG_SYNCING);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
  }

  public void syncLogs(BuildMirror mirror, long teamCityBuildId, JenkinsLogChunk logChunk)
      throws BridgeHttpException, IOException {
    String newLog = logChunk.getText();
    if (newLog.length() == 0) {
      // Nothing new since the last poll; do not append or rewrite state.
      return;
    }

    teamCityBuildLogger.addBuildLog(teamCityBuildId, newLog);

    mirror.setLastLogOffset(logChunk.getNextStart());
    mirror.setSyncState(SyncState.LOG_SYNCING);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
  }

  /**
   * Mirrors Jenkins Pipeline stages as TeamCity build-step blocks, live and idempotently. Stages are
   * processed in {@code describe} order; for each stage we open its block once, append only the
   * console text produced since the last poll, and close it once the stage reaches a terminal status.
   *
   * To keep blocks well-formed (non-overlapping) in the linear TeamCity log, we never open the next
   * stage's block until the current one is closed: a stage that is still running (or paused, or not
   * yet started) stops this poll. Parallel stages are therefore serialized in describe order — a
   * documented v1 limitation.
   */
  public void syncStages(BuildMirror mirror, long teamCityBuildId, JenkinsStages stages, JenkinsClient jenkinsClient)
      throws BridgeHttpException, IOException {
    Map<String, StageMirror> state = mirror.getStages();
    List<BuildMessage1> messages = new ArrayList<BuildMessage1>();

    for (JenkinsStage stage : stages.getStages()) {
      StageMirror sm = state.get(stage.getId());
      if (sm == null) {
        sm = new StageMirror(stage.getName());
        state.put(stage.getId(), sm);
      }
      sm.setStatus(stage.getStatus());

      if (stage.isSkipped()) {
        // Skipped stages have no node log; emit an empty, informative block and keep going.
        if (!sm.isBlockClosed()) {
          messages.addAll(teamCityStageReporter.messagesForStage(
              stage.getName(), null, null, !sm.isBlockOpened(), "(stage skipped)", true));
          sm.setBlockOpened(true);
          sm.setBlockClosed(true);
        }
        continue;
      }

      if (stage.isNotStarted() && !sm.isBlockOpened()) {
        // Queued but not running yet: nothing to show, and nothing after it can have started either.
        break;
      }

      boolean open = !sm.isBlockOpened();

      String append = "";
      JenkinsStageLog log = jenkinsClient.getStageLog(
          mirror.getJenkinsJob(), mirror.getJenkinsBuildNumber(), stage.getId());
      String full = log.getText();
      long offset = sm.getLogOffset();
      if (offset < full.length()) {
        append = full.substring((int) offset);
        sm.setLogOffset(full.length());
      }

      boolean close = stage.isTerminal() && !sm.isBlockClosed();

      // Block start/end carry no explicit Date: they default to the current time, so every message
      // in the queue (block start, the now()-stamped console lines, block end) stays in monotonic
      // timestamp order. A historical Jenkins timestamp on the boundaries would land the blockEnd
      // before its own content and TeamCity would not render a foldable block.
      messages.addAll(teamCityStageReporter.messagesForStage(stage.getName(), null, null, open, append, close));
      if (open) {
        sm.setBlockOpened(true);
      }
      if (close) {
        sm.setBlockClosed(true);
      }

      // Do not open the next stage until this one is closed: keeps log blocks non-overlapping.
      if (!sm.isBlockClosed()) {
        break;
      }
    }

    if (!messages.isEmpty()) {
      teamCityStageReporter.report(teamCityBuildId, messages);
    }

    mirror.setSyncState(SyncState.LOG_SYNCING);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
  }

  public void syncTestsIfNeeded(BuildMirror mirror, long teamCityBuildId, JenkinsTestReport testReport)
      throws IOException {
    if (mirror.isTestsSynced()) {
      return;
    }

    teamCityTestReporter.reportTests(teamCityBuildId, testReport);

    mirror.setTestsSynced(true);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
  }

  public void finishBuildIfNeeded(BuildMirror mirror, long teamCityBuildId, JenkinsBuildInfo jenkinsInfo)
      throws BridgeHttpException, IOException {
    if (mirror.getSyncState() == SyncState.TEAMCITY_FINISHED) {
      return;
    }

    if (jenkinsInfo.isBuilding()) {
      return;
    }

    String finalResult = jenkinsInfo.getResult() == null ? "UNKNOWN" : jenkinsInfo.getResult();
    if (!mirror.isSummaryLogSent()) {
      String summary = "\n--- Jenkins build summary ---\n"
          + "Jenkins job: " + mirror.getJenkinsJob() + "\n"
          + "Jenkins build number: " + mirror.getJenkinsBuildNumber() + "\n"
          + "Jenkins build key: " + mirror.getJenkinsBuildKey() + "\n"
          + "Jenkins URL: " + nullToEmpty(jenkinsInfo.getUrl()) + "\n"
          + "Jenkins result: " + finalResult + "\n"
          + "Jenkins duration: " + jenkinsInfo.getDuration() + " ms\n";

      teamCityBuildLogger.addBuildLog(teamCityBuildId, summary);

      mirror.setSummaryLogSent(true);
      mirror.setJenkinsResult(finalResult);
      mirror.setLastError(null);
      mirrorStore.saveMirror(mirror);
    }

    Date finishTime = getJenkinsFinishTime(jenkinsInfo);
    String finishDate = formatTeamCityFinishDate(finishTime);

    teamCityBuildFinisher.finishBuild(teamCityBuildId, finishTime, finalResult);

    mirror.setSyncState(SyncState.TEAMCITY_FINISHED);
    mirror.setJenkinsResult(finalResult);
    mirror.setTeamCityFinishDate(finishDate);
    mirror.setLastError(null);
    mirrorStore.saveMirror(mirror);
  }

  private Date getJenkinsFinishTime(JenkinsBuildInfo jenkinsInfo) {
    long finishMillis = jenkinsInfo.getTimestamp() + Math.max(0L, jenkinsInfo.getDuration());
    return new Date(finishMillis);
  }

  private String formatTeamCityFinishDate(Date finishTime) {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ");
    formatter.setTimeZone(TimeZone.getTimeZone(settingsProvider.load().getZoneId()));
    return formatter.format(finishTime);
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
