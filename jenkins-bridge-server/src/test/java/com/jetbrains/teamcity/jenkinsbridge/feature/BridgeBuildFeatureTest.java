package com.jetbrains.teamcity.jenkinsbridge.feature;

import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BridgeBuildFeatureTest {
  private final BridgeBuildFeature feature = new BridgeBuildFeature(stubPluginDescriptor());

  @Test
  public void exposesStableTypeAndAgentlessFlags() {
    assertEquals("jenkinsBridge", feature.getType());
    assertEquals("Jenkins Bridge", feature.getDisplayName());
    assertFalse(feature.isMultipleFeaturesPerBuildTypeAllowed());
    assertFalse(feature.isRequiresAgent());
    assertEquals("plugins/jenkins-bridge/editJenkinsBridge.jsp", feature.getEditParametersUrl());
  }

  @Test
  public void rejectsMissingJobPath() {
    Collection<InvalidProperty> errors = process(new HashMap<String, String>());
    assertEquals(1, errors.size());
    assertEquals("jenkinsJob", errors.iterator().next().getPropertyName());
  }

  @Test
  public void acceptsJobPathAlone() {
    Map<String, String> props = new HashMap<String, String>();
    props.put("jenkinsJob", "team/pipeline");
    assertTrue(process(props).isEmpty());
  }

  @Test
  public void acceptsBlankRecentLimitButRejectsNonPositiveInteger() {
    Map<String, String> blank = new HashMap<String, String>();
    blank.put("jenkinsJob", "job");
    blank.put("recentBuildLimit", "");
    assertTrue(process(blank).isEmpty());

    Map<String, String> bad = new HashMap<String, String>();
    bad.put("jenkinsJob", "job");
    bad.put("recentBuildLimit", "abc");
    Collection<InvalidProperty> errors = process(bad);
    assertEquals(1, errors.size());
    assertEquals("recentBuildLimit", errors.iterator().next().getPropertyName());

    Map<String, String> good = new HashMap<String, String>();
    good.put("jenkinsJob", "job");
    good.put("recentBuildLimit", "5");
    assertTrue(process(good).isEmpty());
  }

  private Collection<InvalidProperty> process(Map<String, String> props) {
    PropertiesProcessor processor = feature.getParametersProcessor();
    return processor.process(props);
  }

  private static PluginDescriptor stubPluginDescriptor() {
    return new PluginDescriptor() {
      @Override
      public String getPluginResourcesPath() {
        return "plugins/jenkins-bridge/";
      }

      @Override
      public String getPluginResourcesPath(String path) {
        return "plugins/jenkins-bridge/" + path;
      }

      @Override
      public String getPluginName() {
        return "jenkins-bridge";
      }

      @Override
      public String getPluginVersion() {
        return "test";
      }

      @Override
      public String getParameterValue(String key) {
        return null;
      }

      @Override
      public java.io.File getPluginRoot() {
        return null;
      }
    };
  }
}
