package com.jetbrains.teamcity.jenkinsbridge.mapping;

import java.util.LinkedHashMap;
import java.util.Map;

public class BridgeState {
  private int version = 1;
  private Map<String, JenkinsBuildMapping> builds = new LinkedHashMap<String, JenkinsBuildMapping>();
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
