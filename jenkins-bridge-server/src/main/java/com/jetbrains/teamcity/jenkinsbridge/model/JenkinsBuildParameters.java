package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter values saved on one concrete Jenkins build run, parsed from the build's
 * {@code ParametersAction}. This is distinct from job parameter definitions.
 */
public class JenkinsBuildParameters {
  private final List<JenkinsBuildParameterValue> parameters;

  private JenkinsBuildParameters(List<JenkinsBuildParameterValue> parameters) {
    this.parameters = parameters;
  }

  public static JenkinsBuildParameters empty() {
    return new JenkinsBuildParameters(Collections.<JenkinsBuildParameterValue>emptyList());
  }

  public static JenkinsBuildParameters fromJson(JsonObject json) {
    JsonArray actions = json.getAsJsonArray("actions");
    if (actions == null || actions.size() == 0) {
      return empty();
    }

    List<JenkinsBuildParameterValue> values = new ArrayList<JenkinsBuildParameterValue>();
    for (JsonElement action : actions) {
      if (action == null || !action.isJsonObject()) {
        continue;
      }
      JsonArray parameters = action.getAsJsonObject().getAsJsonArray("parameters");
      if (parameters == null) {
        continue;
      }
      for (JsonElement parameter : parameters) {
        if (parameter != null && parameter.isJsonObject()) {
          JenkinsBuildParameterValue value = JenkinsBuildParameterValue.fromJson(parameter.getAsJsonObject());
          if (value.getName().length() > 0) {
            values.add(value);
          }
        }
      }
    }

    if (values.isEmpty()) {
      return empty();
    }
    return new JenkinsBuildParameters(Collections.unmodifiableList(values));
  }

  public List<JenkinsBuildParameterValue> getParameters() {
    return parameters;
  }

  public Map<String, String> asMap() {
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (JenkinsBuildParameterValue parameter : parameters) {
      result.put(parameter.getName(), parameter.getValue());
    }
    return result;
  }

  public boolean isEmpty() {
    return parameters.isEmpty();
  }
}
