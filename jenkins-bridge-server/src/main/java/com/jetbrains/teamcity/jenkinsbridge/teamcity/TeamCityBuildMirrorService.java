package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMapping;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMappingStore;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildState;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsLogChunk;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class TeamCityBuildMirrorService {
  private static final Set<String> RUNNING_DATA_ALREADY_SENT_STATES = new HashSet<String>(Arrays.asList(
      JenkinsBuildState.RUNNING_SENT,
      JenkinsBuildState.LOG_SYNCING,
      JenkinsBuildState.TEAMCITY_FINISHED
  ));

  private final JenkinsBridgeSettingsProvider settingsProvider;
  private final TeamCityClient teamCityClient;
  private final TeamCityBuildQueuer teamCityBuildQueuer;
  private final TeamCityBuildStarter teamCityBuildStarter;
  private final TeamCityBuildLogger teamCityBuildLogger;
  private final TeamCityTestReporter teamCityTestReporter;
  private final TeamCityBuildFinisher teamCityBuildFinisher;
  private final JenkinsBuildMappingStore mappingStore;

  public TeamCityBuildMirrorService(
      JenkinsBridgeSettingsProvider settingsProvider,
      TeamCityClient teamCityClient,
      TeamCityBuildQueuer teamCityBuildQueuer,
      TeamCityBuildStarter teamCityBuildStarter,
      TeamCityBuildLogger teamCityBuildLogger,
      TeamCityTestReporter teamCityTestReporter,
      TeamCityBuildFinisher teamCityBuildFinisher,
      JenkinsBuildMappingStore mappingStore
  ) {
    this.settingsProvider = settingsProvider;
    this.teamCityClient = teamCityClient;
    this.teamCityBuildQueuer = teamCityBuildQueuer;
    this.teamCityBuildStarter = teamCityBuildStarter;
    this.teamCityBuildLogger = teamCityBuildLogger;
    this.teamCityTestReporter = teamCityTestReporter;
    this.teamCityBuildFinisher = teamCityBuildFinisher;
    this.mappingStore = mappingStore;
  }


  // May need better naming
  public long ensureTeamCityBuild(JenkinsBuildMapping mapping, JenkinsBuildInfo jenkinsInfo)
      throws BridgeHttpException, IOException {
    if (mapping.getTeamCityBuildId() != null) {
      return mapping.getTeamCityBuildId();
    }

    Long restoredBuildId = teamCityClient.findBuildIdByJenkinsBuildKey(mapping.getJenkinsBuildKey());

    // If there already exists a build with the same Jenkins build key, use it

    if (restoredBuildId != null) {
      mapping.setTeamCityBuildId(restoredBuildId);
      mapping.setState(JenkinsBuildState.TEAMCITY_CREATED);
      mapping.setLastError(null);
      mappingStore.saveMapping(mapping);
      return restoredBuildId;
    }

    // Else create a new build (with the build queuer) and return the build ID


    // Prepare the build properties
    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put("jenkins.job", mapping.getJenkinsJob());
    properties.put("jenkins.build.number", String.valueOf(mapping.getJenkinsBuildNumber()));
    properties.put("jenkins.build.key", mapping.getJenkinsBuildKey());
    properties.put("jenkins.build.url", nullToEmpty(jenkinsInfo.getUrl()));

    long buildId = teamCityBuildQueuer.queueAgentlessBuild(settingsProvider.load().getTeamCityBuildTypeId(), properties);
    mapping.setTeamCityBuildId(buildId);
    mapping.setState(JenkinsBuildState.TEAMCITY_CREATED);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
    return buildId;
  }

  public void ensureRunningDataSent(JenkinsBuildMapping mapping, long teamCityBuildId)
      throws BridgeHttpException, IOException {
    if (RUNNING_DATA_ALREADY_SENT_STATES.contains(mapping.getState())) {
      return;
    }

    String text = "Monitoring Jenkins job " + mapping.getJenkinsJob()
        + " build #" + mapping.getJenkinsBuildNumber();

    teamCityBuildStarter.markBuildAsRunning(teamCityBuildId, text);

    mapping.setState(JenkinsBuildState.RUNNING_SENT);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  public void ensureMetadataLogSent(JenkinsBuildMapping mapping, long teamCityBuildId)
      throws BridgeHttpException, IOException {
    if (mapping.isMetadataLogSent()) {
      return;
    }

    String text = "Monitoring Jenkins job: " + mapping.getJenkinsJob() + "\n"
        + "Jenkins build number: " + mapping.getJenkinsBuildNumber() + "\n"
        + "Jenkins build key: " + mapping.getJenkinsBuildKey() + "\n"
        + "Jenkins build URL: " + nullToEmpty(mapping.getJenkinsBuildUrl()) + "\n"
        + "\n";

    teamCityBuildLogger.addBuildLog(teamCityBuildId, text);

    mapping.setMetadataLogSent(true);
    mapping.setState(JenkinsBuildState.LOG_SYNCING);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  public void syncLogs(JenkinsBuildMapping mapping, long teamCityBuildId, JenkinsLogChunk logChunk)
      throws BridgeHttpException, IOException {
    String newLog = logChunk.getText();
    if (newLog.length() == 0) {
      // Nothing new since the last poll; do not append or rewrite state.
      return;
    }

    teamCityBuildLogger.addBuildLog(teamCityBuildId, newLog);

    mapping.setLastLogOffset(logChunk.getNextStart());
    mapping.setState(JenkinsBuildState.LOG_SYNCING);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  public void syncTestsIfNeeded(JenkinsBuildMapping mapping, long teamCityBuildId, JenkinsTestReport testReport)
      throws IOException {
    if (mapping.isTestsSynced()) {
      return;
    }

    teamCityTestReporter.reportTests(teamCityBuildId, testReport);

    mapping.setTestsSynced(true);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  public void finishBuildIfNeeded(JenkinsBuildMapping mapping, long teamCityBuildId, JenkinsBuildInfo jenkinsInfo)
      throws BridgeHttpException, IOException {
    if (JenkinsBuildState.TEAMCITY_FINISHED.equals(mapping.getState())) {
      return;
    }

    if (jenkinsInfo.isBuilding()) {
      return;
    }

    String finalResult = jenkinsInfo.getResult() == null ? "UNKNOWN" : jenkinsInfo.getResult();
    if (!mapping.isSummaryLogSent()) {
      String summary = "\n--- Jenkins build summary ---\n"
          + "Jenkins job: " + mapping.getJenkinsJob() + "\n"
          + "Jenkins build number: " + mapping.getJenkinsBuildNumber() + "\n"
          + "Jenkins build key: " + mapping.getJenkinsBuildKey() + "\n"
          + "Jenkins URL: " + nullToEmpty(jenkinsInfo.getUrl()) + "\n"
          + "Jenkins result: " + finalResult + "\n"
          + "Jenkins duration: " + jenkinsInfo.getDuration() + " ms\n";

      teamCityBuildLogger.addBuildLog(teamCityBuildId, summary);

      mapping.setSummaryLogSent(true);
      mapping.setJenkinsResult(finalResult);
      mapping.setLastError(null);
      mappingStore.saveMapping(mapping);
    }

    Date finishTime = getJenkinsFinishTime(jenkinsInfo);
    String finishDate = formatTeamCityFinishDate(finishTime);

    teamCityBuildFinisher.finishBuild(teamCityBuildId, finishTime, finalResult);

    mapping.setState(JenkinsBuildState.TEAMCITY_FINISHED);
    mapping.setJenkinsResult(finalResult);
    mapping.setTeamCityFinishDate(finishDate);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
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
