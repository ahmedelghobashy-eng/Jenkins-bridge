package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JenkinsBuildInfo {
  private int number;
  private String url;
  private boolean building;
  private String result;
  private long timestamp;
  private long duration;
  private long estimatedDuration;

  public static JenkinsBuildInfo fromJson(JsonObject json) {
    JenkinsBuildInfo info = new JenkinsBuildInfo();
    info.number = getInt(json, "number", 0);
    info.url = getString(json, "url", "");
    info.building = getBoolean(json, "building", false);
    info.result = getString(json, "result", null);
    info.timestamp = getLong(json, "timestamp", 0L);
    info.duration = getLong(json, "duration", 0L);
    info.estimatedDuration = getLong(json, "estimatedDuration", 0L);
    return info;
  }

  public int getNumber() {
    return number;
  }

  public String getUrl() {
    return url;
  }

  public boolean isBuilding() {
    return building;
  }

  public String getResult() {
    return result;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getDuration() {
    return duration;
  }

  public long getEstimatedDuration() {
    return estimatedDuration;
  }

  private static String getString(JsonObject json, String name, String defaultValue) {
    JsonElement element = json.get(name);
    if (element == null || element.isJsonNull()) {
      return defaultValue;
    }
    return element.getAsString();
  }

  private static boolean getBoolean(JsonObject json, String name, boolean defaultValue) {
    JsonElement element = json.get(name);
    if (element == null || element.isJsonNull()) {
      return defaultValue;
    }
    return element.getAsBoolean();
  }

  private static int getInt(JsonObject json, String name, int defaultValue) {
    JsonElement element = json.get(name);
    if (element == null || element.isJsonNull()) {
      return defaultValue;
    }
    return element.getAsInt();
  }

  private static long getLong(JsonObject json, String name, long defaultValue) {
    JsonElement element = json.get(name);
    if (element == null || element.isJsonNull()) {
      return defaultValue;
    }
    return element.getAsLong();
  }
}
