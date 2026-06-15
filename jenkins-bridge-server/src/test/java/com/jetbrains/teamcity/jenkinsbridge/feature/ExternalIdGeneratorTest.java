package com.jetbrains.teamcity.jenkinsbridge.feature;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExternalIdGeneratorTest {
  @Test
  public void sanitizeKeepsWordCharsCollapsesOthersTrimsEnds() {
    assertEquals("team_my_pipeline", ExternalIdGenerator.sanitize("team/my-pipeline"));
    assertEquals("a_b", ExternalIdGenerator.sanitize("a // b"));
    assertEquals("abc", ExternalIdGenerator.sanitize("--abc--"));
    assertEquals("", ExternalIdGenerator.sanitize(null));
  }

  @Test
  public void baseExternalIdPrefixesProjectAndEnsuresLetterStart() {
    assertEquals("Proj_team_job", ExternalIdGenerator.baseExternalId("Proj", "team/job"));
    assertTrue(ExternalIdGenerator.baseExternalId("123", "9job").matches("[A-Za-z].*"));
  }

  @Test
  public void resolveUniqueAppendsSuffixOnCollision() {
    Set<String> taken = new HashSet<String>();
    taken.add("Base");
    taken.add("Base_1");

    assertEquals("Base_2", ExternalIdGenerator.resolveUnique("Base", taken::contains));
    assertEquals("Fresh", ExternalIdGenerator.resolveUnique("Fresh", taken::contains));
  }
}
