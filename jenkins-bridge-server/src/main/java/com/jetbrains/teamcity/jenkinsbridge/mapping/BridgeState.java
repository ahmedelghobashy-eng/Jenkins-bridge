package com.jetbrains.teamcity.jenkinsbridge.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class BridgeState {
  private int version = 1;
  private Map<String, JenkinsBuildMapping> builds = new LinkedHashMap<String, JenkinsBuildMapping>();
  // Highest Jenkins build number already discovered per job, used as an incremental-polling watermark.
  private Map<String, Integer> lastSeenBuildNumbers = new LinkedHashMap<String, Integer>();
  private String lastPollTime;
  private String lastError;

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public Map<String, JenkinsBuildMapping> getBuilds() {
    if (builds == null) {
      builds = new LinkedHashMap<String, JenkinsBuildMapping>();
    }
    return builds;
  }

  public Map<String, Integer> getLastSeenBuildNumbers() {
    if (lastSeenBuildNumbers == null) {
      lastSeenBuildNumbers = new LinkedHashMap<String, Integer>();
    }
    return lastSeenBuildNumbers;
  }

  public String getLastPollTime() {
    return lastPollTime;
  }

  public void setLastPollTime(String lastPollTime) {
    this.lastPollTime = lastPollTime;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }
}
