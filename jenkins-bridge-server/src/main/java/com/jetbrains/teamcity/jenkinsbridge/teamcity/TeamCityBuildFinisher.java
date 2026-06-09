package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsVerdict;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.serverSide.BuildsManager;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.impl.BuildAgentMessagesQueue;
import jetbrains.buildServer.serverSide.impl.RunningBuildState;

import java.util.Collections;
import java.util.Date;

public class TeamCityBuildFinisher {
  private static final String FINISH_REQUEST_MESSAGE = "Build finish request received via Jenkins Bridge";
  // Build problem type shared by all Jenkins-result problems; the per-result identity is appended below.
  private static final String JENKINS_RESULT_PROBLEM_TYPE = "jenkinsBuildResult";

  private final BuildsManager buildsManager;
  private final BuildAgentMessagesQueue buildAgentMessagesQueue;

  public TeamCityBuildFinisher(
      BuildsManager buildsManager,
      BuildAgentMessagesQueue buildAgentMessagesQueue
  ) {
    this.buildsManager = buildsManager;
    this.buildAgentMessagesQueue = buildAgentMessagesQueue;
  }

  public void finishBuild(long buildId, Date finishTime, String jenkinsResult) {
    SRunningBuild runningBuild = buildsManager.findRunningBuildById(buildId);
    if (runningBuild instanceof RunningBuildEx) {
      finishRunningBuild((RunningBuildEx)runningBuild, finishTime, jenkinsResult);
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
      finishRunningBuild((RunningBuildEx)build, finishTime, jenkinsResult);
      return;
    }

    throw new IllegalStateException("TeamCity build " + buildId + " is not a running build");
  }

  private void finishRunningBuild(RunningBuildEx runningBuild, Date finishTime, String jenkinsResult) {
    // The verdict (build problem / interruption / status text) must be applied while the build is
    // still running, i.e. before buildFinished(...) finalizes it.
    applyJenkinsVerdict(runningBuild, jenkinsResult);

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

    // buildFailedOnAgent must stay false: per the TeamCity API it only exists for backwards
    // compatibility with old agents and otherwise produces a generic "Unknown build problem".
    // The real verdict is carried by the build problem / interruption applied above.
    buildAgentMessagesQueue.buildFinished(runningBuild, finishTime, false);
  }

  /**
   * Maps the Jenkins result onto a TeamCity outcome. TeamCity has no first-class "unstable" state,
   * so UNSTABLE/FAILURE/UNKNOWN become build problems (failed build) while ABORTED/NOT_BUILT become
   * interruptions (canceled build). SUCCESS is left untouched so TeamCity keeps its own status text.
   */
  private void applyJenkinsVerdict(RunningBuildEx runningBuild, String jenkinsResult) {
    JenkinsVerdict verdict = JenkinsVerdict.fromResult(jenkinsResult);
    switch (verdict) {
      case SUCCESS:
        break;
      case UNSTABLE:
        addJenkinsResultProblem(runningBuild, "UNSTABLE", "Jenkins marked build UNSTABLE");
        runningBuild.setCustomStatusText("Jenkins result: UNSTABLE");
        break;
      case FAILURE:
        addJenkinsResultProblem(runningBuild, "FAILURE", "Jenkins marked build FAILURE");
        runningBuild.setCustomStatusText("Jenkins result: FAILURE");
        break;
      case ABORTED:
        runningBuild.setInterrupted(RunningBuildState.INTERRUPTED_BY_USER, null, "Canceled in Jenkins");
        break;
      case NOT_BUILT:
        runningBuild.setInterrupted(RunningBuildState.INTERRUPTED_BY_SYSTEM, null, "Jenkins result: NOT_BUILT");
        break;
      case UNKNOWN:
      default:
        String raw = jenkinsResult == null ? "null" : jenkinsResult;
        addJenkinsResultProblem(runningBuild, "UNKNOWN", "Unknown Jenkins result: " + raw);
        runningBuild.setCustomStatusText("Jenkins result: " + raw);
        break;
    }
  }

  private void addJenkinsResultProblem(RunningBuildEx runningBuild, String resultId, String description) {
    // Stable identity so retried finish attempts dedupe instead of stacking duplicate problems.
    runningBuild.addBuildProblem(
        BuildProblemData.createBuildProblem("jenkinsResult_" + resultId, JENKINS_RESULT_PROBLEM_TYPE, description));
  }
}
