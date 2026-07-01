package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsArtifact;
import jetbrains.buildServer.ArtifactsConstants;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.artifacts.ArtifactDataInstance;
import jetbrains.buildServer.artifacts.util.ArtifactListUtil;
import jetbrains.buildServer.artifacts.util.SerializableArtifactListData;
import jetbrains.buildServer.serverSide.BuildAttributes;
import jetbrains.buildServer.serverSide.RunningBuildEx;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TeamCityArtifactPublisher {

  private static final Logger LOG = Logger.getInstance(TeamCityArtifactPublisher.class.getName());

  @NotNull
  private static final Charset OUR_CHARSET = StandardCharsets.UTF_8;

  private final TeamCityRunningBuildLocator buildLocator;

  public TeamCityArtifactPublisher(TeamCityRunningBuildLocator buildLocator) {
    this.buildLocator = buildLocator;
  }

  @Deprecated
  public void publishArtifact(long buildId, String artifactPath, InputStream inputStream) throws IOException {
    RunningBuildEx runningBuild = buildLocator.findRunningBuild(buildId);
    if (runningBuild == null) {
      throw new IOException("TeamCity build " + buildId + " is already finished");
    }
    runningBuild.publishArtifact(artifactPath, inputStream);
  }

  /**
   * Registers {@code artifacts} as externally stored in Jenkins by publishing an artifact list
   * file to {@link ArtifactsConstants#ARTIFACT_LIST_PATH}.
   */
  public void publishArtifactList(long buildId, List<JenkinsArtifact> artifacts) throws IOException {
    RunningBuildEx runningBuild = buildLocator.findRunningBuild(buildId);
    if (runningBuild == null) {
      throw new IOException("TeamCity build " + buildId + " is already finished");
    }

    Object storageSettingReference = runningBuild
        .getBuildPromotion()
        .getAttribute(BuildAttributes.STORAGE_SETTINGS_REFERENCE);
    String storageFeatureId = storageSettingReference instanceof String
        ? (String) storageSettingReference
        : null;

    if (storageFeatureId == null || storageFeatureId.isEmpty()) {
      LOG.warn("'Jenkins' artifact storage is not configured as the active storage for "
          + "TeamCity project " + runningBuild.getProjectExternalId() + ". Skipping artifact registration. "
          + "To enable artifact serving, configure 'Jenkins' as the project artifact storage.");
      return;
    }

    List<ArtifactData> artifactDataList = new ArrayList<>();
    for (JenkinsArtifact artifact : artifacts) {
      String path = artifact.getRelativePath();
      artifactDataList.add(ArtifactDataInstance.create(path, artifact.getSize()));
    }

    if (artifactDataList.isEmpty()) {
      return;
    }

    SerializableArtifactListData listData = new SerializableArtifactListData(
        storageFeatureId, Collections.emptyMap(), artifactDataList);
    StringWriter writer = new StringWriter();
    ArtifactListUtil.writeArtifactList(listData, writer);
    byte[] bytes = writer.toString().getBytes(OUR_CHARSET);
    runningBuild.publishArtifact(ArtifactsConstants.ARTIFACT_LIST_PATH, bytes);
  }
}
