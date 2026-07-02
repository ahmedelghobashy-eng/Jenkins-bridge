package com.jetbrains.teamcity.jenkinsbridge.artifactstorage;

import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import jetbrains.buildServer.web.openapi.artifacts.ArtifactDownloadProcessor;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JenkinsArtifactDownloadProcessor implements ArtifactDownloadProcessor {

  private final JenkinsClient myJenkinsClient;
  private final JenkinsArtifactInfoUtils myJenkinsArtifactInfoUtils;

  public JenkinsArtifactDownloadProcessor(
      @NotNull JenkinsClient jenkinsClient,
      @NotNull JenkinsArtifactInfoUtils jenkinsArtifactInfoUtils
  ) {
    myJenkinsClient = jenkinsClient;
    myJenkinsArtifactInfoUtils = jenkinsArtifactInfoUtils;
  }

  @NotNull
  @Override
  public String getType() {
    return JenkinsStorageConstants.JENKINS_STORAGE_TYPE;
  }

  @Override
  public boolean processDownload(@NotNull StoredBuildArtifactInfo info,
                                 @NotNull BuildPromotion buildPromotion,
                                 @NotNull HttpServletRequest httpServletRequest,
                                 @NotNull HttpServletResponse httpServletResponse) throws IOException {
    String job = myJenkinsArtifactInfoUtils.jenkinsJob(info);
    int buildNumber = myJenkinsArtifactInfoUtils.jenkinsBuildNumber(info);
    String relativePath = myJenkinsArtifactInfoUtils.jenkinsRelativePath(info);

    String jenkinsUrl = myJenkinsClient.artifactUrl(job, buildNumber, relativePath);
    httpServletResponse.sendRedirect(jenkinsUrl);
    return true;
  }

}
