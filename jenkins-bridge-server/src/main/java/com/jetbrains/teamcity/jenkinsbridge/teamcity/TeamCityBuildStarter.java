package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.BuildPromotionManager;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.QueuedBuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SQueuedBuild;

public class TeamCityBuildStarter {
  private final BuildsManager buildsManager;
  private final BuildPromotionManager buildPromotionManager;

  public TeamCityBuildStarter(BuildsManager buildsManager, BuildPromotionManager buildPromotionManager) {
    this.buildsManager = buildsManager;
    this.buildPromotionManager = buildPromotionManager;
  }

  public void markBuildAsRunning(long buildId, String requestor) {
    SBuild build = buildsManager.findBuildInstanceById(buildId);
    if (build != null) {
      return;
    }

    BuildPromotion promotion = buildPromotionManager.findPromotionOrReplacement(buildId);
    if (promotion == null) {
      throw new IllegalStateException("TeamCity build promotion " + buildId + " was not found");
    }

    SBuild associatedBuild = promotion.getAssociatedBuild();
    if (associatedBuild != null) {
      return;
    }

    SQueuedBuild queuedBuild = promotion.getQueuedBuild();
    if (queuedBuild == null) {
      throw new IllegalStateException("TeamCity build promotion " + buildId + " is not queued");
    }
    if (!(queuedBuild instanceof QueuedBuildEx)) {
      throw new IllegalStateException("TeamCity build promotion " + buildId + " cannot be started as an agentless build");
    }

    ((QueuedBuildEx)queuedBuild).startBuild(requestor);
  }
}
