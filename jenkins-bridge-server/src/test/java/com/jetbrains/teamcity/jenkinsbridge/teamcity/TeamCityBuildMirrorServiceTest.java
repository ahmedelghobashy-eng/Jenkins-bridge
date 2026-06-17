package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import com.google.gson.JsonParser;
import com.jetbrains.teamcity.jenkinsbridge.jenkins.JenkinsClient;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStageLog;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsStages;
import com.jetbrains.teamcity.jenkinsbridge.persistence.BuildMirror;
import com.jetbrains.teamcity.jenkinsbridge.persistence.BuildMirrorStore;
import jetbrains.buildServer.messages.BuildMessage1;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeamCityBuildMirrorServiceTest {
  private final JsonParser parser = new JsonParser();

  @Test
  public void syncStagesIsLiveAndIdempotent() throws Exception {
    CapturingStageReporter reporter = new CapturingStageReporter();
    FakeStageLogClient client = new FakeStageLogClient();
    TeamCityBuildMirrorService service = new TeamCityBuildMirrorService(
        null, null, null, null, null, null, reporter, null, null, new NoopStore());

    BuildMirror mirror = new BuildMirror();

    // Poll 1: stage running, first slice of log. Block opens, log appended, not closed.
    client.text = "+ mvn compile";
    service.syncStages(mirror, 1L, stages("Build", "IN_PROGRESS", 1000, 0), client);

    assertEquals(1, reporter.reports.size());
    List<BuildMessage1> first = reporter.reports.get(0);
    assertEquals(2, first.size());
    assertEquals(DefaultMessagesInfo.MSG_BLOCK_START, first.get(0).getTypeId());
    assertEquals("+ mvn compile", first.get(1).getValue().toString());
    assertTrue(mirror.getStages().get("6").isBlockOpened());

    // Poll 2: stage finished, log grew. Only the delta is appended, then the block closes.
    client.text = "+ mvn compileBUILD OK";
    service.syncStages(mirror, 1L, stages("Build", "SUCCESS", 1000, 500), client);

    assertEquals(2, reporter.reports.size());
    List<BuildMessage1> second = reporter.reports.get(1);
    assertEquals("BUILD OK", second.get(0).getValue().toString());
    assertEquals(DefaultMessagesInfo.MSG_BLOCK_END, second.get(second.size() - 1).getTypeId());
    assertTrue(mirror.getStages().get("6").isBlockClosed());

    // Poll 3: nothing changed. No messages emitted at all (idempotent).
    service.syncStages(mirror, 1L, stages("Build", "SUCCESS", 1000, 500), client);
    assertEquals(2, reporter.reports.size());
    assertEquals(21L, mirror.getStages().get("6").getLogOffset());
  }

  @Test
  public void skippedPipelineNodeFinishesRedWithoutBeingCanceled() {
    assertEquals("FAILURE", TeamCityBuildMirrorService.jenkinsResultForPipelineNodeStatus("NOT_EXECUTED"));
  }

  private JenkinsStages stages(String name, String status, long start, long duration) {
    String json = "{\"stages\":[{\"id\":\"6\",\"name\":\"" + name + "\",\"status\":\"" + status
        + "\",\"startTimeMillis\":" + start + ",\"durationMillis\":" + duration + "}]}";
    return JenkinsStages.fromJson(parser.parse(json).getAsJsonObject());
  }

  private static class CapturingStageReporter extends TeamCityStageReporter {
    final List<List<BuildMessage1>> reports = new ArrayList<List<BuildMessage1>>();

    CapturingStageReporter() {
      super(null, null);
    }

    @Override
    public void report(long buildId, List<BuildMessage1> messages) {
      reports.add(new ArrayList<BuildMessage1>(messages));
    }
  }

  private static class FakeStageLogClient extends JenkinsClient {
    String text = "";

    FakeStageLogClient() {
      super(null, null);
    }

    @Override
    public JenkinsStageLog getStageLog(String jobName, int buildNumber, String stageId) {
      return JenkinsStageLog.of(text);
    }
  }

  private static class NoopStore extends BuildMirrorStore {
    NoopStore() {
      super(null, null);
    }

    @Override
    public void saveMirror(BuildMirror mirror) {
      // no-op: tests assert on the in-memory mirror, not on disk
    }
  }
}
