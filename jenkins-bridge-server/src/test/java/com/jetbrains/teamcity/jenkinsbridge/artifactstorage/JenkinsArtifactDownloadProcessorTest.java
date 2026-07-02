package com.jetbrains.teamcity.jenkinsbridge.artifactstorage;

import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import jetbrains.buildServer.artifacts.ArtifactData;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.artifacts.StoredBuildArtifactInfo;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JenkinsArtifactDownloadProcessorTest {
  private final String JENKINS_BASE_URL = "http://jenkins.instance";
  private final JenkinsBridgeSettings settings = mock(JenkinsBridgeSettings.class);
  private final JenkinsBridgeSettingsProvider settingsProvider = mock(JenkinsBridgeSettingsProvider.class);
  private final JenkinsClient jenkinsClient = new JenkinsClient(settingsProvider, null);
  private final JenkinsArtifactDownloadProcessor processor =
      new JenkinsArtifactDownloadProcessor(jenkinsClient, new JenkinsArtifactInfoUtils());

  @Test
  public void processDownloadRedirectsToTheJenkinsArtifactUrl() throws IOException {
    when(settings.getJenkinsUrl()).thenReturn(JENKINS_BASE_URL);
    when(settingsProvider.load()).thenReturn(settings);
    StoredBuildArtifactInfo info = artifactInfo("folder/job", "7", "target/app.jar");
    HttpServletResponse response = mock(HttpServletResponse.class);

    processor.processDownload(info, info.getBuildPromotion(), mock(HttpServletRequest.class), response);

    verify(response).sendRedirect("http://jenkins.instance/job/folder/job/job/7/artifact/target/app.jar");
  }

  @Test
  public void processDownloadReturnsTrueAfterRedirecting() throws IOException {
    when(settings.getJenkinsUrl()).thenReturn(JENKINS_BASE_URL);
    when(settingsProvider.load()).thenReturn(settings);
    StoredBuildArtifactInfo info = artifactInfo("job", "1", "a.txt");

    boolean handled = processor.processDownload(
        info, info.getBuildPromotion(), mock(HttpServletRequest.class), mock(HttpServletResponse.class));

    assertTrue(handled);
  }

  @Test(expected = IllegalArgumentException.class)
  public void processDownloadThrowsWhenJobParameterIsMissing() throws IOException {
    StoredBuildArtifactInfo info = artifactInfo(null, "1", "a.txt");

    processor.processDownload(
        info, info.getBuildPromotion(), mock(HttpServletRequest.class), mock(HttpServletResponse.class));
  }

  private static StoredBuildArtifactInfo artifactInfo(String job, String buildNumber, String relativePath) {
    BuildPromotion promotion = mock(BuildPromotion.class);
    when(promotion.getParameterValue("jenkins.job")).thenReturn(job);
    when(promotion.getParameterValue("jenkins.build.number")).thenReturn(buildNumber);

    ArtifactData artifactData = mock(ArtifactData.class);
    when(artifactData.getPath()).thenReturn(relativePath);

    StoredBuildArtifactInfo info = mock(StoredBuildArtifactInfo.class);
    when(info.getBuildPromotion()).thenReturn(promotion);
    when(info.getArtifactData()).thenReturn(artifactData);
    return info;
  }
}
