package com.jetbrains.teamcity.jenkinsbridge.settings;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JenkinsJobMappingTest {
  @Test
  public void featureMappingNamespacesKeyPrefixByBuildTypeAndJob() {
    JenkinsJobMapping mapping = new JenkinsJobMapping("team/pipeline", "Proj_Mirror", "Proj / Mirror", 0, false);
    assertFalse(mapping.isLegacy());
    assertEquals("Proj_Mirror::team/pipeline", mapping.getMirrorKeyPrefix());
  }

  @Test
  public void legacyMappingKeepsBareJobKeyPrefix() {
    JenkinsJobMapping mapping = new JenkinsJobMapping("team/pipeline", "Proj_Mirror", "Proj / Mirror", 0, true);
    assertTrue(mapping.isLegacy());
    assertEquals("team/pipeline", mapping.getMirrorKeyPrefix());
  }

  @Test
  public void recentBuildLimitOverrideFallsBackToGlobalDefaultWhenUnset() {
    JenkinsJobMapping noOverride = new JenkinsJobMapping("job", "Bt", "Bt", 0, false);
    assertEquals(7, noOverride.getEffectiveRecentBuildLimit(7));

    JenkinsJobMapping withOverride = new JenkinsJobMapping("job", "Bt", "Bt", 3, false);
    assertEquals(3, withOverride.getEffectiveRecentBuildLimit(7));
  }

  @Test
  public void hasMinimumConfigurationRequiresJobAndBuildType() {
    assertTrue(new JenkinsJobMapping("job", "Bt", "Bt", 0, false).hasMinimumConfiguration());
    assertFalse(new JenkinsJobMapping("", "Bt", "Bt", 0, false).hasMinimumConfiguration());
    assertFalse(new JenkinsJobMapping("job", "", "", 0, false).hasMinimumConfiguration());

    JenkinsJobMapping missingJob = new JenkinsJobMapping("  ", "Bt", "Bt", 0, false);
    assertTrue(missingJob.describeMinimumConfigurationProblem().contains("jenkinsJob"));
  }

  @Test
  public void fromGlobalSettingsProducesLegacyMapping() throws Exception {
    JenkinsBridgeSettings settings = settings("myjob", "Bt_Target");
    JenkinsJobMapping mapping = JenkinsJobMapping.fromGlobalSettings(settings);

    assertTrue(mapping.isLegacy());
    assertEquals("myjob", mapping.getJenkinsJob());
    assertEquals("Bt_Target", mapping.getTeamCityBuildTypeExternalId());
    assertEquals("myjob", mapping.getMirrorKeyPrefix());
    assertTrue(mapping.hasMinimumConfiguration());
  }

  private static JenkinsBridgeSettings settings(String job, String buildTypeId) throws Exception {
    Constructor<JenkinsBridgeSettings> constructor = JenkinsBridgeSettings.class.getDeclaredConstructor(
        boolean.class, String.class, String.class, String.class, String.class,
        String.class, String.class, String.class, String.class,
        int.class, int.class, String.class, String.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        true, "http://jenkins", "user", "token", job,
        "http://teamcity", "tc-user", "tc-pass", buildTypeId,
        10, 1, "Europe/Berlin", "");
  }
}
