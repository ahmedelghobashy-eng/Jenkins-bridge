package com.jetbrains.teamcity.jenkinsbridge.mapping;

import com.google.gson.JsonObject;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JenkinsBuildMappingStoreTest {
  @Test
  public void lastSeenBuildNumberPersistsAndIsMonotonic() throws Exception {
    JenkinsBridgeSettingsProvider provider = providerWithTempStateFile();
    JenkinsBuildMappingStore store = new JenkinsBuildMappingStore(null, provider);

    assertEquals(0, store.getLastSeenBuildNumber("job"));
    store.setLastSeenBuildNumber("job", 42);
    assertEquals(42, store.getLastSeenBuildNumber("job"));

    // A fresh store instance must read the watermark back from disk.
    JenkinsBuildMappingStore reloaded = new JenkinsBuildMappingStore(null, provider);
    assertEquals(42, reloaded.getLastSeenBuildNumber("job"));

    // Lower values are ignored (watermark only moves forward).
    reloaded.setLastSeenBuildNumber("job", 10);
    assertEquals(42, reloaded.getLastSeenBuildNumber("job"));
  }

  @Test
  public void getActiveMappingsExcludesFinishedBuilds() throws Exception {
    JenkinsBuildMappingStore store = new JenkinsBuildMappingStore(null, providerWithTempStateFile());

    store.getOrCreateMapping("job", buildInfo(1));
    JenkinsBuildMapping finished = store.getOrCreateMapping("job", buildInfo(2));
    finished.setState(JenkinsBuildState.TEAMCITY_FINISHED);
    store.saveMapping(finished);

    List<JenkinsBuildMapping> active = store.getActiveMappings("job");
    assertEquals(1, active.size());
    assertEquals(1, active.get(0).getJenkinsBuildNumber());
  }

  @Test
  public void findMappingReturnsNullWhenAbsent() throws Exception {
    JenkinsBuildMappingStore store = new JenkinsBuildMappingStore(null, providerWithTempStateFile());
    store.getOrCreateMapping("job", buildInfo(1));

    assertNotNull(store.findMapping(JenkinsBuildMappingStore.buildKey("job", 1)));
    assertNull(store.findMapping(JenkinsBuildMappingStore.buildKey("job", 999)));
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
    final String path = stateFile.getAbsolutePath();

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
