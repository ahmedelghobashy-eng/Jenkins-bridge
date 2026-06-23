package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import jetbrains.buildServer.serverSide.RunningBuildEx;

import java.io.IOException;
import java.io.InputStream;

public class TeamCityArtifactPublisher {
  private final TeamCityRunningBuildLocator buildLocator;

  public TeamCityArtifactPublisher(TeamCityRunningBuildLocator buildLocator) {
    this.buildLocator = buildLocator;
  }

  public void publishArtifact(long buildId, String artifactPath, InputStream inputStream) throws IOException {
    RunningBuildEx runningBuild = buildLocator.findRunningBuild(buildId);
    if (runningBuild == null) {
      throw new IOException("TeamCity build " + buildId + " is already finished");
    }
    runningBuild.publishArtifact(artifactPath, inputStream);
  }
}
