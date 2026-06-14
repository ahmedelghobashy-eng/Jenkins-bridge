package com.jetbrains.teamcity.jenkinsbridge.jenkins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpClient;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpResponse;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsLogChunk;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class JenkinsClient {
  // Jenkins embeds invisible ConsoleNote annotations in the raw console as an ANSI "conceal" block:
  // ESC[8m + "ha:" + base64 payload + ESC[0m. Jenkins' own UI hides them; the bridge must strip them
  // so they do not leak into the mirrored TeamCity log as base64 garbage. A note never spans a line,
  // so the (line-bounded) non-greedy match is safe; a note split across two progressive fetches is a
  // rare edge that can leak one partial note at the boundary.
  private static final Pattern CONSOLE_NOTE = Pattern.compile("\\[8m.*?\\[0m");

  private final JenkinsBridgeSettingsProvider settingsProvider;
  private final BridgeHttpClient httpClient;
  private final JsonParser jsonParser = new JsonParser();

  public JenkinsClient(JenkinsBridgeSettingsProvider settingsProvider, BridgeHttpClient httpClient) {
    this.settingsProvider = settingsProvider;
    this.httpClient = httpClient;
  }

  /**
   * Returns the numbers of the (up to 100) most recent builds. Jenkins caps the {@code builds}
   * collection at the 100 newest builds to avoid loading the whole history; callers that may have
   * fallen further behind can escalate to {@link #getAllBuildNumbers(String)}.
   */
  public List<Integer> getBuildNumbers(String jobName) throws BridgeHttpException {
    return fetchBuildNumbers(jobName, "builds");
  }

  /**
   * Returns the numbers of all builds via Jenkins' {@code allBuilds} collection. This is complete
   * but can be expensive on jobs with a long history, so use it only as a fallback when a gap is
   * detected below the {@link #getBuildNumbers(String)} window.
   */
  public List<Integer> getAllBuildNumbers(String jobName) throws BridgeHttpException {
    return fetchBuildNumbers(jobName, "allBuilds");
  }

  private List<Integer> fetchBuildNumbers(String jobName, String collection) throws BridgeHttpException {
    JenkinsBridgeSettings settings = settingsProvider.load();
    String tree = collection + "[number]";
    String url = settings.getJenkinsUrl()
        + jenkinsJobPath(jobName)
        + "/api/json?tree="
        + encodeQueryValue(tree);

    String response = httpClient.get(url, settings.getJenkinsUser(), settings.getJenkinsToken(), "application/json");
    JsonObject root = jsonParser.parse(response).getAsJsonObject();
    JsonArray builds = root.getAsJsonArray(collection);
    List<Integer> numbers = new ArrayList<Integer>();
    if (builds == null) {
      return numbers;
    }

    for (JsonElement build : builds) {
      if (build == null || !build.isJsonObject()) {
        continue;
      }
      JsonElement number = build.getAsJsonObject().get("number");
      if (number != null && !number.isJsonNull()) {
        numbers.add(number.getAsInt());
      }
    }

    return numbers;
  }

  public JenkinsBuildInfo getBuildInfo(String jobName, int buildNumber) throws BridgeHttpException {
    JenkinsBridgeSettings settings = settingsProvider.load();
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

  /**
   * Fetches only the console output produced since {@code start} (a byte offset) using Jenkins'
   * progressive log API, instead of re-downloading the whole console each poll.
   */
  public JenkinsLogChunk getProgressiveLog(String jobName, int buildNumber, long start) throws BridgeHttpException {
    JenkinsBridgeSettings settings = settingsProvider.load();
    long safeStart = Math.max(0L, start);
    String url = settings.getJenkinsUrl()
        + jenkinsJobPath(jobName)
        + "/"
        + buildNumber
        + "/logText/progressiveText?start="
        + safeStart;

    BridgeHttpResponse response = httpClient.getResponse(
        url, settings.getJenkinsUser(), settings.getJenkinsToken(), "text/plain");

    String rawBody = response.getBody();
    // X-Text-Size is the new total byte size; fall back to advancing by the raw body length if
    // absent. Offsets track the raw log, so stripping console notes below must not change nextStart.
    long nextStart = parseLong(response.getHeader("X-Text-Size"), safeStart + rawBody.length());
    boolean hasMoreData = "true".equalsIgnoreCase(response.getHeader("X-More-Data"));
    return new JenkinsLogChunk(stripConsoleNotes(rawBody), nextStart, hasMoreData);
  }

  private static long parseLong(String value, long defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Removes Jenkins ConsoleNote annotations (ESC[8m...ESC[0m conceal blocks) from console text so
   * they don't leak into the mirrored TeamCity log. Visible for testing.
   */
  static String stripConsoleNotes(String text) {
    if (text == null || text.isEmpty()) {
      return text == null ? "" : text;
    }
    return CONSOLE_NOTE.matcher(text).replaceAll("");
  }

  public JenkinsTestReport getTestReport(String jobName, int buildNumber) throws BridgeHttpException {
    JenkinsBridgeSettings settings = settingsProvider.load();
    String tree = "suites[name,cases[className,name,status,duration,errorDetails,errorStackTrace,skippedMessage,stdout,stderr]]";
    String url = settings.getJenkinsUrl()
        + jenkinsJobPath(jobName)
        + "/"
        + buildNumber
        + "/testReport/api/json?tree="
        + encodeQueryValue(tree);

    try {
      String response = httpClient.get(url, settings.getJenkinsUser(), settings.getJenkinsToken(), "application/json");
      return JenkinsTestReport.fromJson(jsonParser.parse(response).getAsJsonObject());
    } catch (BridgeHttpException e) {
      if (e.getStatusCode() == 404) {
        return JenkinsTestReport.empty();
      }
      throw e;
    }
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
