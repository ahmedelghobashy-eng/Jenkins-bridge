package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonObject;

public class JenkinsArtifact {
  private final String fileName;
  private final String relativePath;

  public JenkinsArtifact(String fileName, String relativePath) {
    this.fileName = fileName == null ? "" : fileName;
    this.relativePath = relativePath == null ? "" : relativePath;
  }

  public static JenkinsArtifact fromJson(JsonObject json) {
    return new JenkinsArtifact(stringValue(json, "fileName"), stringValue(json, "relativePath"));
  }

  public String getFileName() {
    return fileName;
  }

  public String getRelativePath() {
    return relativePath;
  }

  private static String stringValue(JsonObject object, String key) {
    if (object == null || object.get(key) == null || object.get(key).isJsonNull()) {
      return "";
    }
    return object.get(key).getAsString();
  }
}
