package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionManager;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.impl.BuildAgentMessagesQueue;

import java.util.Collections;

public class TeamCityBuildLogger {
  private final BuildsManager buildsManager;
  private final BuildPromotionManager buildPromotionManager;
  private final BuildAgentMessagesQueue buildAgentMessagesQueue;

  public TeamCityBuildLogger(
      BuildsManager buildsManager,
      BuildPromotionManager buildPromotionManager,
      BuildAgentMessagesQueue buildAgentMessagesQueue
  ) {
    this.buildsManager = buildsManager;
    this.buildPromotionManager = buildPromotionManager;
    this.buildAgentMessagesQueue = buildAgentMessagesQueue;
  }

  public void addBuildLog(long buildId, String text) {
    if (text == null || text.length() == 0) {
      return;
    }

    RunningBuildEx runningBuild = findRunningBuild(buildId);
    if (runningBuild == null) {
      return;
    }

    try {
      buildAgentMessagesQueue.processMessages(
          runningBuild,
          Collections.singletonList(DefaultMessagesInfo.createTextMessage(text).updateTags(DefaultMessagesInfo.TAG_SERVER))
      );
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while logging TeamCity build message", e);
    } catch (BuildAgentMessagesQueue.BuildMessagesQueueFullException e) {
      throw new IllegalStateException("TeamCity build messages queue is full", e);
    }
  }

  private RunningBuildEx findRunningBuild(long buildId) {
    SRunningBuild runningBuild = buildsManager.findRunningBuildById(buildId);
    if (runningBuild instanceof RunningBuildEx) {
      return (RunningBuildEx)runningBuild;
    }

    SBuild build = buildsManager.findBuildInstanceById(buildId);
    if (build != null) {
      if (build.isFinished()) {
        return null;
      }
      if (build instanceof RunningBuildEx) {
        return (RunningBuildEx)build;
      }
    }

    BuildPromotion promotion = buildPromotionManager.findPromotionOrReplacement(buildId);
    if (promotion == null) {
      throw new IllegalStateException("TeamCity build " + buildId + " was not found");
    }

    SBuild associatedBuild = promotion.getAssociatedBuild();
    if (associatedBuild == null) {
      throw new IllegalStateException("TeamCity build " + buildId + " is not running");
    }
    if (associatedBuild.isFinished()) {
      return null;
    }
    if (associatedBuild instanceof RunningBuildEx) {
      return (RunningBuildEx)associatedBuild;
    }

    throw new IllegalStateException("TeamCity build " + buildId + " is not a running build");
  }
}
