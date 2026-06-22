package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JenkinsBuildParameterValue {
  private final String name;
  private final String value;
  private final String parameterClass;

  private JenkinsBuildParameterValue(String name, String value, String parameterClass) {
    this.name = name;
    this.value = value;
    this.parameterClass = parameterClass;
  }

  public static JenkinsBuildParameterValue fromJson(JsonObject json) {
    return new JenkinsBuildParameterValue(
        getString(json, "name", ""),
        getValue(json.get("value")),
        getString(json, "_class", "")
    );
  }

  public String getName() {
    return name == null ? "" : name;
  }

  public String getValue() {
    return value == null ? "" : value;
  }

  public String getParameterClass() {
    return parameterClass == null ? "" : parameterClass;
  }

  private static String getValue(JsonElement element) {
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
      return "";
    }
    return element.getAsString();
  }

  private static String getString(JsonObject json, String name, String defaultValue) {
    JsonElement element = json.get(name);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
      return defaultValue;
    }
    return element.getAsString();
  }
}
