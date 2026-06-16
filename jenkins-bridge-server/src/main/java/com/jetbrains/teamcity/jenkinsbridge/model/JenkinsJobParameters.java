package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The build parameters a Jenkins job declares, parsed from {@code /job/<path>/api/json}'s
 * {@code property[]} array (the entry carrying {@code parameterDefinitions}). Empty for a
 * non-parameterized job.
 */
public class JenkinsJobParameters {
  private final List<JenkinsParameterDefinition> parameters;

  private JenkinsJobParameters(List<JenkinsParameterDefinition> parameters) {
    this.parameters = parameters;
  }

  public static JenkinsJobParameters empty() {
    return new JenkinsJobParameters(Collections.<JenkinsParameterDefinition>emptyList());
  }

  public static JenkinsJobParameters fromJson(JsonObject json) {
    JsonArray properties = json.getAsJsonArray("property");
    if (properties == null || properties.size() == 0) {
      return empty();
    }

    List<JenkinsParameterDefinition> defs = new ArrayList<JenkinsParameterDefinition>();
    for (JsonElement property : properties) {
      if (property == null || !property.isJsonObject()) {
        continue;
      }
      JsonArray paramDefs = property.getAsJsonObject().getAsJsonArray("parameterDefinitions");
      if (paramDefs == null) {
        continue;
      }
      for (JsonElement paramDef : paramDefs) {
        if (paramDef != null && paramDef.isJsonObject()) {
          defs.add(JenkinsParameterDefinition.fromJson(paramDef.getAsJsonObject()));
        }
      }
    }
    return new JenkinsJobParameters(Collections.unmodifiableList(defs));
  }

  public List<JenkinsParameterDefinition> getParameters() {
    return parameters;
  }

  public boolean isParameterized() {
    return !parameters.isEmpty();
  }
}
