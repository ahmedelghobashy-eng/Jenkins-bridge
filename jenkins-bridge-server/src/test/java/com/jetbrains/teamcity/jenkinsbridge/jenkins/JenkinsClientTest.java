package com.jetbrains.teamcity.jenkinsbridge.jenkins;

import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpClient;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpException;
import com.jetbrains.teamcity.jenkinsbridge.http.BridgeHttpResponse;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsCrumb;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsJobParameters;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsLogChunk;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStageLog;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStageNodes;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStages;
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

  @Test
  public void listJobsBuildsRootUrlWhenFolderBlank() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"jobs\":[]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    client.listJobs("");

    assertEquals("http://jenkins/api/json?tree=jobs%5Bname%2CfullName%2Curl%2C_class%2Cbuildable%2Ccolor%5D",
        httpClient.url);
  }

  @Test
  public void listJobsBuildsFolderUrlAndClassifiesEntries() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"jobs\":["
        + "{\"name\":\"free\",\"fullName\":\"team/free\",\"_class\":\"hudson.model.FreeStyleProject\"},"
        + "{\"name\":\"sub\",\"fullName\":\"team/sub\",\"_class\":\"com.cloudbees.hudson.plugins.folder.Folder\"}"
        + "]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    List<JenkinsJob> jobs = client.listJobs("team");

    assertEquals("http://jenkins/job/team/api/json?tree=jobs%5Bname%2CfullName%2Curl%2C_class%2Cbuildable%2Ccolor%5D",
        httpClient.url);
    assertEquals(2, jobs.size());
    assertEquals("team/free", jobs.get(0).getFullName());
    assertTrue(jobs.get(0).isImportable());
    assertFalse(jobs.get(1).isImportable());
  }

  @Test
  public void jobUrlBuildsAbsoluteJobPageUrlFromGlobalBase() {
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), new StubResponseHttpClient());

    assertEquals("http://jenkins/job/multi-test/", client.jobUrl("multi-test"));
    assertEquals("http://jenkins/job/team/job/my-pipeline/", client.jobUrl("team/my-pipeline"));
  }

  @Test
  public void getStagesBuildsWfapiUrlAndParsesStages() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"status\":\"IN_PROGRESS\",\"stages\":["
        + "{\"id\":\"6\",\"name\":\"Build\",\"status\":\"SUCCESS\",\"startTimeMillis\":1000,\"durationMillis\":500},"
        + "{\"id\":\"12\",\"name\":\"Test\",\"status\":\"IN_PROGRESS\",\"startTimeMillis\":2000,\"durationMillis\":0}"
        + "]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsStages stages = client.getStages("folder/job", 7);

    assertEquals("http://jenkins/job/folder/job/job/7/wfapi/describe", httpClient.url);
    assertTrue(stages.isPipeline());
    assertEquals(2, stages.getStages().size());
    assertEquals("Build", stages.getStages().get(0).getName());
    assertTrue(stages.getStages().get(0).isTerminal());
    assertFalse(stages.getStages().get(1).isTerminal());
  }

  @Test
  public void getStagesReturnsNotPipelineOn404() throws Exception {
    NotFoundHttpClient httpClient = new NotFoundHttpClient();
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsStages stages = client.getStages("job", 3);

    assertFalse(stages.isPipeline());
    assertTrue(stages.getStages().isEmpty());
    assertEquals("http://jenkins/job/job/3/wfapi/describe", httpClient.url);
  }

  @Test
  public void getStageNodesParsesStepNodeIdsWithLogLinks() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"stageFlowNodes\":["
        + "{\"id\":\"7\",\"_links\":{\"log\":{\"href\":\"a\"}}},"
        + "{\"id\":\"8\",\"_links\":{\"log\":{\"href\":\"b\"}}},"
        + "{\"id\":\"99\",\"_links\":{\"self\":{\"href\":\"c\"}}}"
        + "]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsStageNodes nodes = client.getStageNodes("folder/job", 7, "6");

    assertEquals("http://jenkins/job/folder/job/job/7/execution/node/6/wfapi/describe", httpClient.url);
    assertEquals(Arrays.asList("7", "8"), nodes.getLogNodeIds());
  }

  @Test
  public void getStageLogConcatenatesStepNodeLogsInOrder() throws Exception {
    RoutingHttpClient httpClient = new RoutingHttpClient();
    httpClient.responses.put("/execution/node/6/wfapi/describe",
        "{\"stageFlowNodes\":[{\"id\":\"7\",\"_links\":{\"log\":{\"href\":\"a\"}}},"
            + "{\"id\":\"8\",\"_links\":{\"log\":{\"href\":\"b\"}}}]}");
    httpClient.responses.put("/execution/node/7/wfapi/log", "{\"text\":\"executing step 1\\n\"}");
    httpClient.responses.put("/execution/node/8/wfapi/log", "{\"text\":\"+ sleep 10\\n\"}");
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsStageLog log = client.getStageLog("job", 26, "6");

    assertEquals("executing step 1\n+ sleep 10\n", log.getText());
  }

  @Test
  public void getNodeLogStripsConsoleNotes() throws Exception {
    String esc = "";
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"text\":\"" + esc + "[8mha:AAA==" + esc + "[0m+ mvn test\\nok\\n\"}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsStageLog log = client.getNodeLog("folder/job", 7, "8");

    assertEquals("http://jenkins/job/folder/job/job/7/execution/node/8/wfapi/log", httpClient.url);
    assertEquals("+ mvn test\nok\n", log.getText());
  }

  @Test
  public void getStageLogReturnsEmptyWhenStageNotMaterialized() throws Exception {
    NotFoundHttpClient httpClient = new NotFoundHttpClient();
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsStageLog log = client.getStageLog("job", 3, "9");

    assertEquals("", log.getText());
  }

  @Test
  public void getCrumbParsesFieldAndValue() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"crumbRequestField\":\"Jenkins-Crumb\",\"crumb\":\"abc123\"}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsCrumb crumb = client.getCrumb();

    assertEquals("http://jenkins/crumbIssuer/api/json", httpClient.url);
    assertTrue(crumb.isPresent());
    assertEquals("Jenkins-Crumb", crumb.getField());
    assertEquals("abc123", crumb.getValue());
  }

  @Test
  public void getCrumbReturnsDisabledOn404() throws Exception {
    NotFoundHttpClient httpClient = new NotFoundHttpClient();
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    assertFalse(client.getCrumb().isPresent());
  }

  @Test
  public void getJobParametersParsesDefinitions() throws Exception {
    StubResponseHttpClient httpClient = new StubResponseHttpClient();
    httpClient.body = "{\"property\":[{\"parameterDefinitions\":["
        + "{\"name\":\"BRANCH\",\"type\":\"StringParameterDefinition\",\"defaultParameterValue\":{\"value\":\"main\"}},"
        + "{\"name\":\"ENV\",\"type\":\"ChoiceParameterDefinition\",\"defaultParameterValue\":{\"value\":\"dev\"},\"choices\":[\"dev\",\"prod\"]}"
        + "]}]}";
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    JenkinsJobParameters params = client.getJobParameters("folder/job");

    assertEquals("http://jenkins/job/folder/job/job/api/json?tree=property%5BparameterDefinitions%5Bname%2Ctype%2CdefaultParameterValue%5Bvalue%5D%2Cchoices%5D%5D", httpClient.url);
    assertTrue(params.isParameterized());
    assertEquals(2, params.getParameters().size());
    assertEquals("BRANCH", params.getParameters().get(0).getName());
    assertEquals("main", params.getParameters().get(0).getDefaultValue());
    assertEquals(Arrays.asList("dev", "prod"), params.getParameters().get(1).getChoices());
  }

  @Test
  public void triggerBuildWithParametersPostsFormAndAttachesCrumb() throws Exception {
    TriggerHttpClient httpClient = new TriggerHttpClient();
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    Map<String, String> values = new LinkedHashMap<String, String>();
    values.put("BRANCH", "feature/x");
    values.put("DEPLOY", "true");

    String queueUrl = client.triggerBuild("folder/job", values);

    assertEquals("http://jenkins/job/folder/job/job/buildWithParameters", httpClient.postUrl);
    assertEquals("BRANCH=feature%2Fx&DEPLOY=true", httpClient.postBody);
    assertEquals("abc123", httpClient.postHeaders.get("Jenkins-Crumb"));
    assertEquals("http://jenkins/queue/item/42/", queueUrl);
  }

  @Test
  public void triggerBuildWithoutParametersUsesBuildEndpoint() throws Exception {
    TriggerHttpClient httpClient = new TriggerHttpClient();
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    String queueUrl = client.triggerBuild("job", new LinkedHashMap<String, String>());

    assertEquals("http://jenkins/job/job/build", httpClient.postUrl);
    assertEquals("", httpClient.postBody);
    assertEquals("http://jenkins/queue/item/42/", queueUrl);
  }

  @Test
  public void triggerBuildOmitsCrumbHeaderWhenDisabled() throws Exception {
    TriggerHttpClient httpClient = new TriggerHttpClient();
    httpClient.crumbDisabled = true;
    JenkinsClient client = new JenkinsClient(new StaticSettingsProvider(), httpClient);

    client.triggerBuild("job", new LinkedHashMap<String, String>());

    assertTrue(httpClient.postHeaders.isEmpty());
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

    @Override
    public String get(String url, String user, String password, String accept) {
      this.url = url;
      return body;
    }
  }

  // Serves the crumb on GET and captures the trigger POST, returning a 201 + Location header.
  private static class TriggerHttpClient extends BridgeHttpClient {
    boolean crumbDisabled = false;
    String postUrl;
    String postBody;
    Map<String, String> postHeaders;

    @Override
    public String get(String url, String user, String password, String accept) throws BridgeHttpException {
      if (url.contains("/crumbIssuer/")) {
        if (crumbDisabled) {
          throw new BridgeHttpException("GET", url, 404, "no crumb");
        }
        return "{\"crumbRequestField\":\"Jenkins-Crumb\",\"crumb\":\"abc123\"}";
      }
      return "{}";
    }

    @Override
    public BridgeHttpResponse postResponse(String url, String user, String password, String body,
                                           String contentType, String accept, Map<String, String> headers) {
      this.postUrl = url;
      this.postBody = body;
      this.postHeaders = headers;
      Map<String, String> responseHeaders = new LinkedHashMap<String, String>();
      responseHeaders.put("Location", "http://jenkins/queue/item/42/");
      return new BridgeHttpResponse(201, "", responseHeaders);
    }
  }

  // Routes each GET to a canned body by matching a URL substring; unmatched URLs 404.
  private static class RoutingHttpClient extends BridgeHttpClient {
    private final Map<String, String> responses = new LinkedHashMap<String, String>();
    private String url;

    @Override
    public String get(String url, String user, String password, String accept) throws BridgeHttpException {
      this.url = url;
      for (Map.Entry<String, String> entry : responses.entrySet()) {
        if (url.contains(entry.getKey())) {
          return entry.getValue();
        }
      }
      throw new BridgeHttpException("GET", url, 404, "not found");
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
