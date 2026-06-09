package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JenkinsTestReport {
  private final List<JenkinsTestSuite> suites;

  private JenkinsTestReport(List<JenkinsTestSuite> suites) {
    this.suites = suites;
  }

  public static JenkinsTestReport empty() {
    return new JenkinsTestReport(Collections.<JenkinsTestSuite>emptyList());
  }

  public static JenkinsTestReport fromJson(JsonObject json) {
    JsonArray suitesJson = json.getAsJsonArray("suites");
    if (suitesJson == null || suitesJson.size() == 0) {
      return empty();
    }

    List<JenkinsTestSuite> suites = new ArrayList<JenkinsTestSuite>();
    for (JsonElement suiteJson : suitesJson) {
      if (suiteJson != null && suiteJson.isJsonObject()) {
        suites.add(JenkinsTestSuite.fromJson(suiteJson.getAsJsonObject()));
      }
    }
    return new JenkinsTestReport(Collections.unmodifiableList(suites));
  }

  public List<JenkinsTestSuite> getSuites() {
    return suites;
  }

  public boolean isEmpty() {
    return getTestCount() == 0;
  }

  public int getTestCount() {
    int count = 0;
    for (JenkinsTestSuite suite : suites) {
      count += suite.getCases().size();
    }
    return count;
  }
}
