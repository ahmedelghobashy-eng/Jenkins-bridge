package com.jetbrains.teamcity.jenkinsbridge.jenkins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpClient;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class JenkinsClient {
  private final JenkinsBridgeSettings settings;
  private final BridgeHttpClient httpClient;
  private final JsonParser jsonParser = new JsonParser();

  public JenkinsClient(JenkinsBridgeSettings settings, BridgeHttpClient httpClient) {
    this.settings = settings;
    this.httpClient = httpClient;
  }

  public List<JenkinsBuildInfo> getRecentBuilds(String jobName, int limit) throws BridgeHttpException {
    String tree = "builds[number,url,building,result,timestamp,duration]{0," + limit + "}";
    String url = settings.getJenkinsUrl()
        + jenkinsJobPath(jobName)
        + "/api/json?tree="
        + encodeQueryValue(tree);

    String response = httpClient.get(url, settings.getJenkinsUser(), settings.getJenkinsToken(), "application/json");
    JsonObject root = jsonParser.parse(response).getAsJsonObject();
    JsonArray builds = root.getAsJsonArray("builds");
    List<JenkinsBuildInfo> result = new ArrayList<JenkinsBuildInfo>();
    if (builds == null) {
      return result;
    }

    for (JsonElement build : builds) {
      result.add(JenkinsBuildInfo.fromJson(build.getAsJsonObject()));
    }

    return result;
  }

  public JenkinsBuildInfo getBuildInfo(String jobName, int buildNumber) throws BridgeHttpException {
    String tree = "number,building,result,timestamp,duration,estimatedDuration,url";
    String url = settings.getJenkinsUrl()
        + jenkinsJobPath(jobName)
        + "/"
        + buildNumber
        + "/api/json?tree="
        + encodeQueryValue(tree);

    String response = httpClient.get(url, settings.getJenkinsUser(), settings.getJenkinsToken(), "application/json");
    return JenkinsBuildInfo.fromJson(jsonParser.parse(response).getAsJsonObject());
  }

  public String getConsoleText(String jobName, int buildNumber) throws BridgeHttpException {
    String url = settings.getJenkinsUrl()
        + jenkinsJobPath(jobName)
        + "/"
        + buildNumber
        + "/consoleText";

    return httpClient.get(url, settings.getJenkinsUser(), settings.getJenkinsToken(), "text/plain");
  }

  private String jenkinsJobPath(String jobName) {
    String[] segments = jobName.split("/");
    StringBuilder result = new StringBuilder();
    for (String segment : segments) {
      if (segment.length() > 0) {
        result.append("/job/").append(encodePathSegment(segment));
      }
    }
    return result.toString();
  }

  private String encodePathSegment(String value) {
    return encodeQueryValue(value).replace("+", "%20");
  }

  public static String encodeQueryValue(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 is not available", e);
    }
  }
}
