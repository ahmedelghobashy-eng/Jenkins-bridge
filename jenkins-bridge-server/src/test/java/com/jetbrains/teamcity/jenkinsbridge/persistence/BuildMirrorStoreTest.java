package com.jetbrains.teamcity.jenkinsbridge.persistence;

import com.google.gson.JsonObject;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

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

    store.getOrCreateMirror("job", buildInfo(1));
    BuildMirror finished = store.getOrCreateMirror("job", buildInfo(2));
    finished.setSyncState(SyncState.TEAMCITY_FINISHED);
    store.saveMirror(finished);

    List<BuildMirror> active = store.getActiveMirrors("job");
    assertEquals(1, active.size());
    assertEquals(1, active.get(0).getJenkinsBuildNumber());
  }

  @Test
  public void findMirrorReturnsNullWhenAbsent() throws Exception {
    BuildMirrorStore store = new BuildMirrorStore(null, providerWithTempStateFile());
    store.getOrCreateMirror("job", buildInfo(1));

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

  private static JenkinsBuildInfo buildInfo(int number) {
    JsonObject json = new JsonObject();
    json.addProperty("number", number);
    json.addProperty("building", true);
    return JenkinsBuildInfo.fromJson(json);
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
