package com.jetbrains.teamcity.jenkinsbridge.artifactstorage;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.artifacts.ArtifactContentProvider;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class JenkinsArtifactContentProvider implements ArtifactContentProvider {

  private final static Logger LOG = Logger.getInstance(JenkinsArtifactDownloadProcessor.class.getName());

  @NotNull
  @Override
  public String getType() {
    return JenkinsStorageConstants.JENKINS_STORAGE_TYPE;
  }

  @NotNull
  @Override
  public InputStream getContent(@NotNull StoredBuildArtifactInfo storedBuildArtifactInfo) throws IOException {
    return null; // TODO: Implement content fetching
  }
}
