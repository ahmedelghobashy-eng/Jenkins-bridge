package com.jetbrains.teamcity.jenkinsbridge.jenkins;

import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpClient;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpResponse;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsLogChunk;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsTestReport;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

  @Test
  public void progressiveLogBuildsUrlAndParsesHeaders() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "new log line\n";
    httpClient.headers.put("X-Text-Size", "2048");
    httpClient.headers.put("X-More-Data", "true");
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsLogChunk chunk = client.getProgressiveLog("folder/job", 7, 1024);

    assertEquals("http://jenkins/job/folder/job/job/7/logText/progressiveText?start=1024", httpClient.url);
    assertEquals("new log line\n", chunk.getText());
    assertEquals(2048L, chunk.getNextStart());
    assertTrue(chunk.hasMoreData());
  }

  @Test
  public void progressiveLogFallsBackToBodyLengthWhenSizeHeaderMissing() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "abcde";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsLogChunk chunk = client.getProgressiveLog("job", 3, 100);

    assertEquals(105L, chunk.getNextStart());
    assertFalse(chunk.hasMoreData());
  }

  @Test
  public void progressiveLogParsesMoreDataCaseInsensitively() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "";
    httpClient.headers.put("x-more-data", "TRUE");
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsLogChunk chunk = client.getProgressiveLog("job", 3, 0);

    assertTrue(chunk.hasMoreData());
  }

  @Test
  public void progressiveLogStripsJenkinsConsoleNotes() throws Exception {
    String esc = "";
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = esc + "[8mha:////AAAA==" + esc + "[0m[Pipeline] Start of Pipeline\nplain line\n";
    httpClient.headers.put("X-Text-Size", "5000");
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsLogChunk chunk = client.getProgressiveLog("job", 7, 100);

    // Console note removed; visible text kept; offset still driven by X-Text-Size (raw byte size).
    assertEquals("[Pipeline] Start of Pipeline\nplain line\n", chunk.getText());
    assertEquals(5000L, chunk.getNextStart());
  }

  @Test
  public void stripConsoleNotesRemovesConcealBlocksAcrossLines() {
    String esc = "";
    String input = esc + "[8mha:AAA==" + esc + "[0mStarted by user Ahmed\n"
        + esc + "[8mha:BBB==" + esc + "[0m[Pipeline] node\n";

    assertEquals("Started by user Ahmed\n[Pipeline] node\n", JenkinsClient.stripConsoleNotes(input));
    assertEquals("plain text", JenkinsClient.stripConsoleNotes("plain text"));
    assertEquals("", JenkinsClient.stripConsoleNotes(""));
    assertEquals("", JenkinsClient.stripConsoleNotes(null));
  }

  @Test
  public void getBuildNumbersUsesBuildsTreeAndParsesNumbers() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"builds\":[{\"number\":50},{\"number\":49},{\"number\":48}]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    List<Integer> numbers = client.getBuildNumbers("folder/job");

    assertEquals("http://jenkins/job/folder/job/job/api/json?tree=builds%5Bnumber%5D", httpClient.url);
    assertEquals(Arrays.asList(50, 49, 48), numbers);
  }

  @Test
  public void getAllBuildNumbersUsesAllBuildsTree() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"allBuilds\":[{\"number\":2},{\"number\":1}]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    List<Integer> numbers = client.getAllBuildNumbers("job");

    assertEquals("http://jenkins/job/job/api/json?tree=allBuilds%5Bnumber%5D", httpClient.url);
    assertEquals(Arrays.asList(2, 1), numbers);
  }

  private static class StubResponseHttpClient extends BridgeHttpClient {
    private String url;
    private String body = "";
    private final Map<String, String> headers = new LinkedHashMap<String, String>();

    @Override
    public BridgeHttpResponse getResponse(String url, String user, String password, String accept) {
      this.url = url;
      return new BridgeHttpResponse(200, body, headers);
    }
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
