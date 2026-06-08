package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMapping;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildMappingStore;
import com.jetbrains.teamcity.jenkinsbridge.mapping.JenkinsBuildState;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;

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

  private final JenkinsBridgeSettings settings;
  private final TeamCityClient teamCityClient;
  private final JenkinsBuildMappingStore mappingStore;

  public TeamCityBuildMirrorService(
      JenkinsBridgeSettings settings,
      TeamCityClient teamCityClient,
      JenkinsBuildMappingStore mappingStore
  ) {
    this.settings = settings;
    this.teamCityClient = teamCityClient;
    this.mappingStore = mappingStore;
  }

  public long ensureTeamCityBuild(JenkinsBuildMapping mapping, JenkinsBuildInfo jenkinsInfo)
      throws BridgeHttpException, IOException {
    if (mapping.getTeamCityBuildId() != null) {
      return mapping.getTeamCityBuildId();
    }

    Long restoredBuildId = teamCityClient.findBuildIdByJenkinsBuildKey(mapping.getJenkinsBuildKey());
    if (restoredBuildId != null) {
      mapping.setTeamCityBuildId(restoredBuildId);
      mapping.setState(JenkinsBuildState.TEAMCITY_CREATED);
      mapping.setLastError(null);
      mappingStore.saveMapping(mapping);
      return restoredBuildId;
    }

    Map<String, String> properties = new LinkedHashMap<String, String>();
    properties.put("jenkins.job", mapping.getJenkinsJob());
    properties.put("jenkins.build.number", String.valueOf(mapping.getJenkinsBuildNumber()));
    properties.put("jenkins.build.key", mapping.getJenkinsBuildKey());
    properties.put("jenkins.build.url", nullToEmpty(jenkinsInfo.getUrl()));

    long buildId = teamCityClient.queueAgentlessBuild(settings.getTeamCityBuildTypeId(), properties);
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

    teamCityClient.markBuildAsRunning(teamCityBuildId, text);

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

    teamCityClient.addBuildLog(teamCityBuildId, text);

    mapping.setMetadataLogSent(true);
    mapping.setState(JenkinsBuildState.LOG_SYNCING);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  public void syncLogs(JenkinsBuildMapping mapping, long teamCityBuildId, String consoleText)
      throws BridgeHttpException, IOException {
    int lastOffset = Math.max(0, mapping.getLastLogOffset());
    if (lastOffset > consoleText.length()) {
      lastOffset = 0;
    }

    String newLog = consoleText.substring(lastOffset);
    if (newLog.length() == 0) {
      return;
    }

    teamCityClient.addBuildLog(teamCityBuildId, newLog);

    mapping.setLastLogOffset(consoleText.length());
    mapping.setState(JenkinsBuildState.LOG_SYNCING);
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

      teamCityClient.addBuildLog(teamCityBuildId, summary);

      mapping.setSummaryLogSent(true);
      mapping.setJenkinsResult(finalResult);
      mapping.setLastError(null);
      mappingStore.saveMapping(mapping);
    }

    String finishDate = formatTeamCityFinishDate(jenkinsInfo);

    teamCityClient.setBuildFinishDate(teamCityBuildId, finishDate);

    mapping.setState(JenkinsBuildState.TEAMCITY_FINISHED);
    mapping.setJenkinsResult(finalResult);
    mapping.setTeamCityFinishDate(finishDate);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  private String formatTeamCityFinishDate(JenkinsBuildInfo jenkinsInfo) {
    long finishMillis = jenkinsInfo.getTimestamp() + Math.max(0L, jenkinsInfo.getDuration());
    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ");
    formatter.setTimeZone(TimeZone.getTimeZone(settings.getZoneId()));
    return formatter.format(new Date(finishMillis));
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
