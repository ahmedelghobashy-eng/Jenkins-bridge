package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpClient;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
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
import java.util.Set;
import java.util.TimeZone;

public class TeamCityBuildMirrorService {
  private static final Set<String> RUNNING_DATA_ALREADY_SENT_STATES = new HashSet<String>(Arrays.asList(
      JenkinsBuildState.RUNNING_SENT,
      JenkinsBuildState.LOG_SYNCING,
      JenkinsBuildState.TEAMCITY_FINISHED
  ));

  private final JenkinsBridgeSettings settings;
  private final BridgeHttpClient httpClient;
  private final JenkinsBuildMappingStore mappingStore;
  private final Gson gson = new Gson();
  private final JsonParser jsonParser = new JsonParser();

  public TeamCityBuildMirrorService(
      JenkinsBridgeSettings settings,
      BridgeHttpClient httpClient,
      JenkinsBuildMappingStore mappingStore
  ) {
    this.settings = settings;
    this.httpClient = httpClient;
    this.mappingStore = mappingStore;
  }

  public long ensureTeamCityBuild(JenkinsBuildMapping mapping, JenkinsBuildInfo jenkinsInfo)
      throws BridgeHttpException, IOException {
    if (mapping.getTeamCityBuildId() != null) {
      return mapping.getTeamCityBuildId();
    }

    Long restoredBuildId = findExistingTeamCityBuildId(mapping.getJenkinsBuildKey());
    if (restoredBuildId != null) {
      mapping.setTeamCityBuildId(restoredBuildId);
      mapping.setState(JenkinsBuildState.TEAMCITY_CREATED);
      mapping.setLastError(null);
      mappingStore.saveMapping(mapping);
      return restoredBuildId;
    }

    JsonObject payload = new JsonObject();
    JsonObject buildType = new JsonObject();
    buildType.addProperty("id", settings.getTeamCityBuildTypeId());
    payload.add("buildType", buildType);

    JsonArray properties = new JsonArray();
    addProperty(properties, "teamcity.build.agentLess", "true");
    addProperty(properties, "jenkins.job", mapping.getJenkinsJob());
    addProperty(properties, "jenkins.build.number", String.valueOf(mapping.getJenkinsBuildNumber()));
    addProperty(properties, "jenkins.build.key", mapping.getJenkinsBuildKey());
    addProperty(properties, "jenkins.build.url", nullToEmpty(jenkinsInfo.getUrl()));

    JsonObject propertiesWrapper = new JsonObject();
    propertiesWrapper.add("property", properties);
    payload.add("properties", propertiesWrapper);

    String response = httpClient.post(
        teamCityApi("/buildQueue"),
        settings.getTeamCityUser(),
        settings.getTeamCityPassword(),
        gson.toJson(payload),
        "application/json",
        "application/json"
    );

    long buildId = jsonParser.parse(response).getAsJsonObject().get("id").getAsLong();
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

    httpClient.put(
        teamCityApi("/builds/id:" + teamCityBuildId + "/runningData"),
        settings.getTeamCityUser(),
        settings.getTeamCityPassword(),
        text,
        "text/plain",
        null
    );

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

    postBuildLog(teamCityBuildId, text);

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

    postBuildLog(teamCityBuildId, newLog);

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

      postBuildLog(teamCityBuildId, summary);

      mapping.setSummaryLogSent(true);
      mapping.setJenkinsResult(finalResult);
      mapping.setLastError(null);
      mappingStore.saveMapping(mapping);
    }

    String finishDate = formatTeamCityFinishDate(jenkinsInfo);

    httpClient.put(
        teamCityApi("/builds/id:" + teamCityBuildId + "/finishDate"),
        settings.getTeamCityUser(),
        settings.getTeamCityPassword(),
        finishDate,
        "text/plain",
        null
    );

    mapping.setState(JenkinsBuildState.TEAMCITY_FINISHED);
    mapping.setJenkinsResult(finalResult);
    mapping.setTeamCityFinishDate(finishDate);
    mapping.setLastError(null);
    mappingStore.saveMapping(mapping);
  }

  private Long findExistingTeamCityBuildId(String jenkinsBuildKey) throws BridgeHttpException {
    String locator = "property:(name:jenkins.build.key,value:" + jenkinsBuildKey + "),count:1,defaultFilter:false";
    String url = teamCityApi("/builds?locator=" + JenkinsClient.encodeQueryValue(locator)
        + "&fields=" + JenkinsClient.encodeQueryValue("build(id)"));

    String response = httpClient.get(
        url,
        settings.getTeamCityUser(),
        settings.getTeamCityPassword(),
        "application/json"
    );

    JsonObject root = jsonParser.parse(response).getAsJsonObject();
    JsonArray builds = root.getAsJsonArray("build");
    if (builds == null || builds.size() == 0) {
      return null;
    }

    JsonElement id = builds.get(0).getAsJsonObject().get("id");
    if (id == null || id.isJsonNull()) {
      return null;
    }

    return id.getAsLong();
  }

  private void postBuildLog(long teamCityBuildId, String text) throws BridgeHttpException {
    if (text == null || text.length() == 0) {
      return;
    }

    httpClient.post(
        teamCityApi("/builds/id:" + teamCityBuildId + "/log"),
        settings.getTeamCityUser(),
        settings.getTeamCityPassword(),
        text,
        "text/plain",
        null
    );
  }

  private String formatTeamCityFinishDate(JenkinsBuildInfo jenkinsInfo) {
    long finishMillis = jenkinsInfo.getTimestamp() + Math.max(0L, jenkinsInfo.getDuration());
    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd'T'HHmmssZ");
    formatter.setTimeZone(TimeZone.getTimeZone(settings.getZoneId()));
    return formatter.format(new Date(finishMillis));
  }

  private String teamCityApi(String path) {
    return settings.getTeamCityUrl() + "/app/rest" + path;
  }

  private void addProperty(JsonArray properties, String name, String value) {
    JsonObject property = new JsonObject();
    property.addProperty("name", name);
    property.addProperty("value", nullToEmpty(value));
    properties.add(property);
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
