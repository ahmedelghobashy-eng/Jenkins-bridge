package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One Jenkins job build parameter, from the job's {@code ParametersDefinitionProperty}
 * (see {@code /job/<path>/api/json?tree=property[parameterDefinitions[...]]}). Used to render a
 * trigger form in TeamCity and to know which values {@code buildWithParameters} accepts.
 */
public class JenkinsParameterDefinition {
  private String name;
  private String type;
  private String defaultValue;
  private List<String> choices;

  public static JenkinsParameterDefinition fromJson(JsonObject json) {
    JenkinsParameterDefinition def = new JenkinsParameterDefinition();
    def.name = getString(json, "name", "");
    def.type = getString(json, "type", "");

    JsonElement defaultValue = json.get("defaultParameterValue");
    if (defaultValue != null && defaultValue.isJsonObject()) {
      def.defaultValue = getString(defaultValue.getAsJsonObject(), "value", "");
    } else {
      def.defaultValue = "";
    }

    List<String> choices = new ArrayList<String>();
    JsonArray choicesJson = json.getAsJsonArray("choices");
    if (choicesJson != null) {
      for (JsonElement choice : choicesJson) {
        if (choice != null && !choice.isJsonNull()) {
          choices.add(choice.getAsString());
        }
      }
    }
    def.choices = Collections.unmodifiableList(choices);
    return def;
  }

  public String getName() {
    return name == null ? "" : name;
  }

  public String getType() {
    return type == null ? "" : type;
  }

  public String getDefaultValue() {
    return defaultValue == null ? "" : defaultValue;
  }

  public List<String> getChoices() {
    return choices == null ? Collections.<String>emptyList() : choices;
  }

  private static String getString(JsonObject json, String name, String defaultValue) {
    JsonElement element = json.get(name);
    if (element == null || element.isJsonNull()) {
      return defaultValue;
    }
    return element.getAsString();
  }
}
