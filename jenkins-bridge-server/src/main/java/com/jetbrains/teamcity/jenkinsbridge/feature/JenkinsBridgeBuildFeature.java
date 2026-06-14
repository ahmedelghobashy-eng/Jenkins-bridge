package com.jetbrains.teamcity.jenkinsbridge.feature;

import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TeamCity build feature that marks a build configuration as a Jenkins Bridge mirror target. The
 * feature carries the Jenkins job path (the Jenkins server/credentials are global); the hosting
 * build configuration is the mirror target. Discovered by {@link JenkinsBridgeMappingProvider}.
 */
public class JenkinsBridgeBuildFeature extends BuildFeature {
  private final String editUrl;

  public JenkinsBridgeBuildFeature(PluginDescriptor pluginDescriptor) {
    this.editUrl = pluginDescriptor.getPluginResourcesPath("editJenkinsBridge.jsp");
  }

  @Override
  public String getType() {
    return JenkinsBridgeBuildFeatureConstants.TYPE;
  }

  @Override
  public String getDisplayName() {
    return "Jenkins Bridge";
  }

  @Override
  public String getEditParametersUrl() {
    return editUrl;
  }

  @Override
  public boolean isMultipleFeaturesPerBuildTypeAllowed() {
    return false;
  }

  @Override
  public boolean isRequiresAgent() {
    // The mirror runs server-side as an agentless build; it never needs a TeamCity agent.
    return false;
  }

  @Override
  public PlaceToShow getPlaceToShow() {
    return PlaceToShow.GENERAL;
  }

  @Override
  public String describeParameters(Map<String, String> params) {
    String job = params.get(JenkinsBridgeBuildFeatureConstants.PARAM_JENKINS_JOB);
    return isBlank(job) ? "Mirrors a Jenkins job" : "Mirrors Jenkins job '" + job.trim() + "'";
  }

  @Override
  public PropertiesProcessor getParametersProcessor() {
    return props -> {
      List<InvalidProperty> errors = new ArrayList<InvalidProperty>();
      if (isBlank(props.get(JenkinsBridgeBuildFeatureConstants.PARAM_JENKINS_JOB))) {
        errors.add(new InvalidProperty(JenkinsBridgeBuildFeatureConstants.PARAM_JENKINS_JOB,
            "Jenkins job path is required"));
      }
      String limit = props.get(JenkinsBridgeBuildFeatureConstants.PARAM_RECENT_LIMIT);
      if (!isBlank(limit) && !isPositiveInt(limit)) {
        errors.add(new InvalidProperty(JenkinsBridgeBuildFeatureConstants.PARAM_RECENT_LIMIT,
            "Recent build limit must be a positive whole number"));
      }
      return errors;
    };
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().length() == 0;
  }

  private static boolean isPositiveInt(String value) {
    try {
      return Integer.parseInt(value.trim()) > 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
