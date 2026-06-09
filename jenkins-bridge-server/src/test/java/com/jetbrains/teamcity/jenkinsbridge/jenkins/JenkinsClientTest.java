package com.jetbrains.teamcity.jenkinsbridge.jenkins;

import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpClient;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JenkinsClientTest {
  @Test
  public void returnsEmptyReportWhenJenkinsHasNoTestReport() throws Exception {
    NotFoundHttpClient httpClient = new NotFoundHttpClient();
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsTestReport report = client.getTestReport("folder/job", 7);

    assertTrue(report.isEmpty());
    assertEquals("http://jenkins/job/folder/job/job/7/testReport/api/json?tree=suites%5Bname%2Ccases%5BclassName%2Cname%2Cstatus%2Cduration%2CerrorDetails%2CerrorStackTrace%2CskippedMessage%2Cstdout%2Cstderr%5D%5D", httpClient.url);
  }

  private static class StaticSettingsProvider extends JenkinsBridgeSettingsProvider {
    StaticSettingsProvider() {
      super(null);
    }

    @Override
    public JenkinsBridgeSettings load() {
      try {
        Constructor<JenkinsBridgeSettings> constructor = JenkinsBridgeSettings.class.getDeclaredConstructor(
            boolean.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            int.class,
            int.class,
            String.class,
            String.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
            true,
            "http://jenkins",
            "jenkins-user",
            "jenkins-token",
            "job",
            "http://teamcity",
            "teamcity-user",
            "teamcity-password",
            "buildType",
            10,
            1,
            "Europe/Berlin",
            ""
        );
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }

  private static class NotFoundHttpClient extends BridgeHttpClient {
    private String url;

    @Override
    public String get(String url, String user, String password, String accept) throws BridgeHttpException {
      this.url = url;
      throw new BridgeHttpException("GET", url, 404, "not found");
    }
  }
}
