package com.jetbrains.teamcity.jenkinsbridge.persistence;

import com.google.gson.JsonObject;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsPipelineGraph;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsPipelineGraphNode;
import com.jetbrains.teamcity.jenkinsbridge.model.GraphConfidence;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuildMirrorStoreTest {
  @Test
  public void lastSeenBuildNumberPersistsAndIsMonotonic() throws Exception {
    JenkinsBridgeSettingsProvider provider = providerWithTempStateFile();
    BuildMirrorStore store = new BuildMirrorStore(null, provider);

    assertEquals(0, store.getLastSeenBuildNumber("job"));
    store.setLastSeenBuildNumber("job", 42);
    assertEquals(42, store.getLastSeenBuildNumber("job"));

    // A fresh store instance must read the watermark back from disk.
    BuildMirrorStore reloaded = new BuildMirrorStore(null, provider);
    assertEquals(42, reloaded.getLastSeenBuildNumber("job"));

    // Lower values are ignored (watermark only moves forward).
    reloaded.setLastSeenBuildNumber("job", 10);
    assertEquals(42, reloaded.getLastSeenBuildNumber("job"));
  }

  @Test
  public void getActiveMirrorsExcludesFinishedBuilds() throws Exception {
    BuildMirrorStore store = new BuildMirrorStore(null, providerWithTempStateFile());

    store.getOrCreateMirror(BuildMirrorStore.buildKey("job", 1), "job", "buildType", buildInfo(1));
    BuildMirror finished = store.getOrCreateMirror(BuildMirrorStore.buildKey("job", 2), "job", "buildType", buildInfo(2));
    finished.setSyncState(SyncState.TEAMCITY_FINISHED);
    store.saveMirror(finished);

    List<BuildMirror> active = store.getActiveMirrors("job");
    assertEquals(1, active.size());
    assertEquals(1, active.get(0).getJenkinsBuildNumber());
  }

  @Test
  public void findMirrorReturnsNullWhenAbsent() throws Exception {
    BuildMirrorStore store = new BuildMirrorStore(null, providerWithTempStateFile());
    store.getOrCreateMirror(BuildMirrorStore.buildKey("job", 1), "job", "buildType", buildInfo(1));

    assertNotNull(store.findMirror(BuildMirrorStore.buildKey("job", 1)));
    assertNull(store.findMirror(BuildMirrorStore.buildKey("job", 999)));
  }

  @Test
  public void corruptStateFileIsQuarantinedAndBridgeStartsFresh() throws Exception {
    File stateFile = File.createTempFile("jenkins-bridge-store-test", ".json");
    stateFile.deleteOnExit();
    Files.write(stateFile.toPath(), "@@@ definitely not json @@@".getBytes(StandardCharsets.UTF_8));

    BuildMirrorStore store =
        new BuildMirrorStore(null, providerForStateFile(stateFile.getAbsolutePath()));

    // Must not throw, and must start from empty state.
    assertEquals(0, store.getLastSeenBuildNumber("job"));
    assertNull(store.findMirror(BuildMirrorStore.buildKey("job", 1)));

    // The corrupt file is moved aside to a .corrupt-* sibling rather than left to brick every poll.
    File[] quarantined = stateFile.getParentFile()
        .listFiles((dir, name) -> name.startsWith(stateFile.getName() + ".corrupt-"));
    assertNotNull(quarantined);
    assertTrue("expected a quarantined .corrupt-* file", quarantined.length >= 1);
    for (File f : quarantined) {
      f.deleteOnExit();
    }
  }

  @Test
  public void pipelineGraphSnapshotPersistsAcrossReload() throws Exception {
    JenkinsBridgeSettingsProvider provider = providerWithTempStateFile();
    BuildMirrorStore store = new BuildMirrorStore(null, provider);
    BuildMirror mirror = store.getOrCreateMirror(BuildMirrorStore.buildKey("job", 7), "job", "buildType", buildInfo(7));

    mirror.setPipelineGraph(graph("hash-a", "SUCCESS", Collections.<String>emptyList()));
    store.saveMirror(mirror);

    BuildMirrorStore reloaded = new BuildMirrorStore(null, provider);
    BuildMirror restored = reloaded.findMirror(BuildMirrorStore.buildKey("job", 7));

    assertNotNull(restored);
    assertNotNull(restored.getPipelineGraph());
    assertEquals("hash-a", restored.getPipelineGraph().getTopologyHash());
    assertEquals(GraphConfidence.EXPLICIT, restored.getPipelineGraph().getConfidence());
    assertEquals("job#7:1", restored.getPipelineGraph().getNodes().get(0).getFlowId());
  }

  @Test
  public void jenkinsBuildParametersPersistAcrossReload() throws Exception {
    JenkinsBridgeSettingsProvider provider = providerWithTempStateFile();
    BuildMirrorStore store = new BuildMirrorStore(null, provider);
    BuildMirror mirror = store.getOrCreateMirror(BuildMirrorStore.buildKey("job", 9), "job", "buildType", buildInfo(9));

    Map<String, String> parameters = new LinkedHashMap<String, String>();
    parameters.put("BRANCH", "feature/x");
    parameters.put("RUN_TESTS", "true");
    mirror.setJenkinsBuildParameters(parameters);
    store.saveMirror(mirror);

    BuildMirrorStore reloaded = new BuildMirrorStore(null, provider);
    BuildMirror restored = reloaded.findMirror(BuildMirrorStore.buildKey("job", 9));

    assertNotNull(restored);
    assertTrue(restored.isJenkinsBuildParametersLoaded());
    assertEquals("feature/x", restored.getJenkinsBuildParameters().get("BRANCH"));
    assertEquals("true", restored.getJenkinsBuildParameters().get("RUN_TESTS"));
  }

  @Test
  public void pipelineChainSnapshotPersistsAcrossReload() throws Exception {
    JenkinsBridgeSettingsProvider provider = providerWithTempStateFile();
    BuildMirrorStore store = new BuildMirrorStore(null, provider);
    BuildMirror mirror = store.getOrCreateMirror(BuildMirrorStore.buildKey("job", 8), "job", "buildType", buildInfo(8));

    Map<String, PipelineChainNodeMirror> nodes = new LinkedHashMap<String, PipelineChainNodeMirror>();
    nodes.put("1", new PipelineChainNodeMirror("1", "job#8:1", "BuildType_JenkinsFlow_hash_1", 101L));
    mirror.setPipelineChain(new PipelineChainMirror(
        "hash-a",
        "EXPLICIT",
        "BuildType_JenkinsFlow_hash_Top",
        200L,
        nodes,
        Arrays.asList("1"),
        Arrays.asList(101L),
        true));
    store.saveMirror(mirror);

    BuildMirrorStore reloaded = new BuildMirrorStore(null, provider);
    BuildMirror restored = reloaded.findMirror(BuildMirrorStore.buildKey("job", 8));

    assertNotNull(restored);
    assertNotNull(restored.getPipelineChain());
    assertEquals("hash-a", restored.getPipelineChain().getTopologyHash());
    assertEquals(Long.valueOf(200L), restored.getPipelineChain().getTopPromotionId());
    assertTrue(restored.getPipelineChain().matchesQueuedTopology("hash-a"));
    assertEquals(Long.valueOf(101L), restored.getPipelineChain().getNode("1").getPromotionId());
  }

  @Test
  public void graphTopologyHashCanStayStableWhileSnapshotStatusChanges() throws Exception {
    JenkinsPipelineGraph success = graph("same-hash", "SUCCESS", Collections.<String>emptyList());
    JenkinsPipelineGraph failed = graph("same-hash", "FAILURE", Collections.<String>emptyList());

    assertEquals(success.getTopologyHash(), failed.getTopologyHash());
    assertEquals("SUCCESS", success.getNodes().get(0).getStatus());
    assertEquals("FAILURE", failed.getNodes().get(0).getStatus());
  }

  @Test
  public void graphTopologyHashChangesWhenEdgesChange() {
    JenkinsPipelineGraph noParent = graph("hash-a", "SUCCESS", Collections.<String>emptyList());
    JenkinsPipelineGraph withParent = graph("hash-b", "SUCCESS", Arrays.asList("0"));

    assertTrue(!noParent.getTopologyHash().equals(withParent.getTopologyHash()));
  }

  private static JenkinsBuildInfo buildInfo(int number) {
    JsonObject json = new JsonObject();
    json.addProperty("number", number);
    json.addProperty("building", true);
    return JenkinsBuildInfo.fromJson(json);
  }

  private static JenkinsPipelineGraph graph(String hash, String status, List<String> parents) {
    return new JenkinsPipelineGraph(
        true,
        JenkinsPipelineGraph.SOURCE_WFAPI,
        Arrays.asList(new JenkinsPipelineGraphNode(
            "1", "job#7:1", "Build", status, 1000L, 10L,
            parents, Collections.<String>emptyList(), Collections.<String>emptyList())),
        hash,
        GraphConfidence.EXPLICIT,
        Collections.<String>emptyList());
  }

  private static JenkinsBridgeSettingsProvider providerWithTempStateFile() throws Exception {
    File stateFile = File.createTempFile("jenkins-bridge-store-test", ".json");
    stateFile.delete();
    stateFile.deleteOnExit();
    return providerForStateFile(stateFile.getAbsolutePath());
  }

  private static JenkinsBridgeSettingsProvider providerForStateFile(final String path) {
    return new JenkinsBridgeSettingsProvider(null) {
      @Override
      public JenkinsBridgeSettings load() {
        try {
          Constructor<JenkinsBridgeSettings> constructor = JenkinsBridgeSettings.class.getDeclaredConstructor(
              boolean.class, String.class, String.class, String.class, String.class,
              String.class, String.class, String.class, String.class,
              int.class, int.class, String.class, String.class);
          constructor.setAccessible(true);
          return constructor.newInstance(
              true, "http://jenkins", "user", "token", "job",
              "http://teamcity", "tc-user", "tc-pass", "buildType",
              10, 1, "Europe/Berlin", path);
        } catch (Exception e) {
          throw new AssertionError(e);
        }
      }
    };
  }
}
