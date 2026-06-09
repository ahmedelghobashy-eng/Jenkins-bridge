package com.jetbrains.teamcity.jenkinsbridge.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JenkinsVerdictTest {
  @Test
  public void mapsKnownJenkinsResults() {
    assertEquals(JenkinsVerdict.SUCCESS, JenkinsVerdict.fromResult("SUCCESS"));
    assertEquals(JenkinsVerdict.FAILURE, JenkinsVerdict.fromResult("FAILURE"));
    assertEquals(JenkinsVerdict.UNSTABLE, JenkinsVerdict.fromResult("UNSTABLE"));
    assertEquals(JenkinsVerdict.ABORTED, JenkinsVerdict.fromResult("ABORTED"));
    assertEquals(JenkinsVerdict.NOT_BUILT, JenkinsVerdict.fromResult("NOT_BUILT"));
  }

  @Test
  public void normalizesCaseAndWhitespace() {
    assertEquals(JenkinsVerdict.SUCCESS, JenkinsVerdict.fromResult(" success "));
    assertEquals(JenkinsVerdict.UNSTABLE, JenkinsVerdict.fromResult("Unstable"));
    assertEquals(JenkinsVerdict.NOT_BUILT, JenkinsVerdict.fromResult("not_built"));
  }

  @Test
  public void unknownForNullBlankOrUnrecognized() {
    assertEquals(JenkinsVerdict.UNKNOWN, JenkinsVerdict.fromResult(null));
    assertEquals(JenkinsVerdict.UNKNOWN, JenkinsVerdict.fromResult(""));
    assertEquals(JenkinsVerdict.UNKNOWN, JenkinsVerdict.fromResult("   "));
    assertEquals(JenkinsVerdict.UNKNOWN, JenkinsVerdict.fromResult("WEIRD_STATE"));
  }
}
