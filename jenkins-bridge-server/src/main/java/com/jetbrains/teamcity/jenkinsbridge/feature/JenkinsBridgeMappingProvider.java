package com.jetbrains.teamcity.jenkinsbridge.feature;

import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsJobMapping;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Discovers the Jenkins-job mappings to mirror by scanning every active build configuration for the
 * {@link JenkinsBridgeBuildFeature}. When no configuration carries the feature, falls back to a
 * single mapping synthesized from the global settings so the legacy single-job setup keeps working.
 */
public class JenkinsBridgeMappingProvider {
  private final ProjectManager projectManager;
  private final JenkinsBridgeSettingsProvider settingsProvider;

  public JenkinsBridgeMappingProvider(
      ProjectManager projectManager,
      JenkinsBridgeSettingsProvider settingsProvider
  ) {
    this.projectManager = projectManager;
    this.settingsProvider = settingsProvider;
  }

  public List<JenkinsJobMapping> discoverMappings() {
    List<JenkinsJobMapping> mappings = new ArrayList<JenkinsJobMapping>();
    for (SBuildType buildType : projectManager.getActiveBuildTypes()) {
      // isMultipleFeaturesPerBuildTypeAllowed()==false is UI-advisory only; iterate defensively.
      for (SBuildFeatureDescriptor descriptor
          : buildType.getBuildFeaturesOfType(JenkinsBridgeBuildFeatureConstants.TYPE)) {
        mappings.add(toMapping(buildType, descriptor));
      }
    }

    if (mappings.isEmpty()) {
      JenkinsJobMapping legacy = legacyMappingOrNull();
      if (legacy != null) {
        mappings.add(legacy);
      }
    }
    return mappings;
  }

  private JenkinsJobMapping toMapping(SBuildType buildType, SBuildFeatureDescriptor descriptor) {
    Map<String, String> params = descriptor.getParameters();
    String job = params.get(JenkinsBridgeBuildFeatureConstants.PARAM_JENKINS_JOB);
    int limit = parsePositiveInt(params.get(JenkinsBridgeBuildFeatureConstants.PARAM_RECENT_LIMIT));
    return new JenkinsJobMapping(job, buildType.getExternalId(), buildType.getFullName(), limit, false);
  }

  private JenkinsJobMapping legacyMappingOrNull() {
    JenkinsBridgeSettings settings = settingsProvider.load();
    if (!settings.hasMinimumConfiguration()) {
      return null;
    }
    return JenkinsJobMapping.fromGlobalSettings(settings);
  }

  private static int parsePositiveInt(String value) {
    if (value == null) {
      return 0;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
