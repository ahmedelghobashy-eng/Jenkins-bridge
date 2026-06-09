package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import jetbrains.buildServer.serverSide.BuildCustomizer;
import jetbrains.buildServer.serverSide.BuildCustomizerFactory;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SQueuedBuild;

import java.util.LinkedHashMap;
import java.util.Map;

public class TeamCityBuildQueuer {
  private static final String AGENTLESS_BUILD_PROPERTY = "teamcity.build.agentLess";
  private static final String TRIGGERED_BY = "Jenkins Bridge";

  private final ProjectManager projectManager;
  private final BuildCustomizerFactory buildCustomizerFactory;

  public TeamCityBuildQueuer(ProjectManager projectManager, BuildCustomizerFactory buildCustomizerFactory) {
    this.projectManager = projectManager;
    this.buildCustomizerFactory = buildCustomizerFactory;
  }

  public long queueAgentlessBuild(String buildTypeId, Map<String, String> properties) {
    SBuildType buildType = findBuildType(buildTypeId);
    if (buildType == null) {
      throw new IllegalStateException("TeamCity build type " + buildTypeId + " was not found");
    }


    Map<String, String> parameters = new LinkedHashMap<String, String>();
    parameters.put(AGENTLESS_BUILD_PROPERTY, "true");
    parameters.putAll(properties);

    BuildCustomizer customizer = buildCustomizerFactory.createBuildCustomizer(buildType, null);
    customizer.setParameters(parameters);

    BuildPromotion promotion = customizer.createPromotion();
    SQueuedBuild queuedBuild = promotion.addToQueue(TRIGGERED_BY);
    if (queuedBuild == null) {
      throw new IllegalStateException("Failed to add TeamCity build type " + buildTypeId + " to the queue");
    }

    return queuedBuild.getBuildPromotion().getId();
  }

  private SBuildType findBuildType(String buildTypeId) {
    if (buildTypeId == null || buildTypeId.trim().length() == 0) {
      return null;
    }

    SBuildType buildType = projectManager.findBuildTypeByExternalId(buildTypeId);
    if (buildType != null) {
      return buildType;
    }
    return projectManager.findBuildTypeById(buildTypeId);
  }
}
