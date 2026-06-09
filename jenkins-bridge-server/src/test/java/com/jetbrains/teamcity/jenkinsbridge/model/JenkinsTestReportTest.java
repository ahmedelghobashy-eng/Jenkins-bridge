package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JenkinsTestReportTest {
  private final JsonParser parser = new JsonParser();

  @Test
  public void parsesSuitesAndCasesFromJenkinsTestReport() {
    JenkinsTestReport report = JenkinsTestReport.fromJson(parser.parse("{"
        + "\"suites\":[{"
        + "\"name\":\"suite.one\","
        + "\"cases\":["
        + "{\"className\":\"com.acme.FastTest\",\"name\":\"passes\",\"status\":\"PASSED\",\"duration\":1.234},"
        + "{\"className\":\"com.acme.FastTest\",\"name\":\"fails\",\"status\":\"REGRESSION\",\"duration\":0.5,"
        + "\"errorDetails\":\"expected true\",\"errorStackTrace\":\"stack\","
        + "\"stdout\":\"hello\",\"stderr\":\"warn\"},"
        + "{\"name\":\"is skipped\",\"status\":\"SKIPPED\",\"skippedMessage\":\"disabled\"}"
        + "]"
        + "}]"
        + "}").getAsJsonObject());

    assertEquals(1, report.getSuites().size());
    assertEquals(3, report.getTestCount());

    JenkinsTestSuite suite = report.getSuites().get(0);
    assertEquals("suite.one", suite.getName());

    JenkinsTestCase passed = suite.getCases().get(0);
    assertEquals("com.acme.FastTest.passes", passed.getTeamCityTestName());
    assertEquals(1234, passed.getDurationMillis());
    assertFalse(passed.isFailed());
    assertFalse(passed.isIgnored());

    JenkinsTestCase failed = suite.getCases().get(1);
    assertEquals("com.acme.FastTest.fails", failed.getTeamCityTestName());
    assertEquals(500, failed.getDurationMillis());
    assertTrue(failed.isFailed());
    assertEquals("expected true", failed.getErrorDetails());
    assertEquals("stack", failed.getErrorStackTrace());
    assertTrue(failed.hasStdout());
    assertTrue(failed.hasStderr());

    JenkinsTestCase skipped = suite.getCases().get(2);
    assertEquals("is skipped", skipped.getTeamCityTestName());
    assertTrue(skipped.isIgnored());
    assertEquals("disabled", skipped.getSkippedMessage());
  }

  @Test
  public void handlesMissingFieldsAsEmptyPassedTest() {
    JenkinsTestReport report = JenkinsTestReport.fromJson(parser.parse("{\"suites\":[{\"cases\":[{}]}]}").getAsJsonObject());

    JenkinsTestSuite suite = report.getSuites().get(0);
    JenkinsTestCase testCase = suite.getCases().get(0);

    assertEquals("Jenkins tests", suite.getName());
    assertEquals("Unnamed Jenkins test", testCase.getTeamCityTestName());
    assertFalse(testCase.isFailed());
    assertFalse(testCase.isIgnored());
    assertEquals(0, testCase.getDurationMillis());
  }
}
