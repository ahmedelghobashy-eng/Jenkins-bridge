package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Console text of one Pipeline stage node from {@code execution/node/<id>/wfapi/log}. The endpoint
 * returns the full node text on every call (no byte cursor), so callers track how much they have
 * already mirrored against {@link #getText()}.
 */
public class JenkinsStageLog {
  private final String text;

  private JenkinsStageLog(String text) {
    this.text = text == null ? "" : text;
  }

  public static JenkinsStageLog empty() {
    return new JenkinsStageLog("");
  }

  public static JenkinsStageLog of(String text) {
    return new JenkinsStageLog(text);
  }

  public static JenkinsStageLog fromJson(JsonObject json) {
    JsonElement textElement = json.get("text");
    if (textElement == null || textElement.isJsonNull()) {
      return empty();
    }
    return new JenkinsStageLog(textElement.getAsString());
  }

  public String getText() {
    return text;
  }
}
