package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.impl.BuildAgentMessagesQueue;

import java.util.Collections;
import java.util.Date;

public class TeamCityBuildFinisher {
  private static final String FINISH_REQUEST_MESSAGE = "Build finish request received via Jenkins Bridge";

  private final BuildsManager buildsManager;
  private final BuildAgentMessagesQueue buildAgentMessagesQueue;

  public TeamCityBuildFinisher(
      BuildsManager buildsManager,
      BuildAgentMessagesQueue buildAgentMessagesQueue
  ) {
    this.buildsManager = buildsManager;
    this.buildAgentMessagesQueue = buildAgentMessagesQueue;
  }

  public void finishBuild(long buildId, Date finishTime) {
    SRunningBuild runningBuild = buildsManager.findRunningBuildById(buildId);
    if (runningBuild instanceof RunningBuildEx) {
      finishRunningBuild((RunningBuildEx)runningBuild, finishTime);
      return;
    }

    SBuild build = buildsManager.findBuildInstanceById(buildId);
    if (build == null) {
      throw new IllegalStateException("TeamCity build " + buildId + " was not found");
    }

    if (build.isFinished()) {
      return;
    }

    if (build instanceof RunningBuildEx) {
      finishRunningBuild((RunningBuildEx)build, finishTime);
      return;
    }

    throw new IllegalStateException("TeamCity build " + buildId + " is not a running build");
  }

  private void finishRunningBuild(RunningBuildEx runningBuild, Date finishTime) {
    try {
      buildAgentMessagesQueue.processMessages(
          runningBuild,
          Collections.singletonList(
              DefaultMessagesInfo.createTextMessage(FINISH_REQUEST_MESSAGE).updateTags(DefaultMessagesInfo.TAG_SERVER)
          )
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while logging TeamCity build finish request", e);
    } catch (BuildAgentMessagesQueue.BuildMessagesQueueFullException e) {
      throw new IllegalStateException("TeamCity build messages queue is full", e);
    }

    buildAgentMessagesQueue.buildFinished(runningBuild, finishTime, false);
  }
}
