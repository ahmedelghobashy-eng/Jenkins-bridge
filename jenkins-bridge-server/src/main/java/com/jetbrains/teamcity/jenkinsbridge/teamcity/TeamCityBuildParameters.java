package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TeamCityBuildParameters {
  public static final String AGENTLESS_BUILD_PROPERTY = "teamcity.build.agentLess";

  private TeamCityBuildParameters() {
  }

  public static Map<String, String> mergeWithJenkinsParameters(
      Map<String, String> bridgeParameters,
      Map<String, String> jenkinsParameters,
      Set<String> existingTeamCityParameterNames
  ) {
    Map<String, String> result = new LinkedHashMap<String, String>();
    result.putAll(bridgeParameters);

    Map<String, String> safeJenkinsParameters = jenkinsParameters == null
        ? Collections.<String, String>emptyMap()
        : jenkinsParameters;
    List<String> collisions = collisions(result.keySet(), safeJenkinsParameters.keySet(), existingTeamCityParameterNames);
    if (!collisions.isEmpty()) {
      throw new IllegalStateException(
          "Jenkins build parameter name(s) collide with TeamCity build parameters: "
              + join(collisions));
    }

    result.putAll(safeJenkinsParameters);
    return result;
  }

  public static Set<String> reservedParameterNames(Map<String, String> bridgeParameters) {
    Set<String> names = new LinkedHashSet<String>();
    names.add(AGENTLESS_BUILD_PROPERTY);
    if (bridgeParameters != null) {
      names.addAll(bridgeParameters.keySet());
    }
    return names;
  }

  private static List<String> collisions(
      Set<String> bridgeNames,
      Set<String> jenkinsNames,
      Set<String> existingTeamCityParameterNames
  ) {
    Set<String> reserved = new LinkedHashSet<String>();
    reserved.add(AGENTLESS_BUILD_PROPERTY);
    if (bridgeNames != null) {
      reserved.addAll(bridgeNames);
    }
    if (existingTeamCityParameterNames != null) {
      reserved.addAll(existingTeamCityParameterNames);
    }

    List<String> collisions = new ArrayList<String>();
    for (String name : jenkinsNames) {
      if (reserved.contains(name)) {
        collisions.add(name);
      }
    }
    Collections.sort(collisions);
    return collisions;
  }

  private static String join(List<String> values) {
    StringBuilder builder = new StringBuilder();
    for (String value : values) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append(value);
    }
    return builder.toString();
  }
}
