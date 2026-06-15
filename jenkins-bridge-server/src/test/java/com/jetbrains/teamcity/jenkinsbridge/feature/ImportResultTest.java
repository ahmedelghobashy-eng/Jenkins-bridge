package com.jetbrains.teamcity.jenkinsbridge.feature;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ImportResultTest {
  @Test
  public void accumulatesAndSerializesToJson() {
    ImportResult result = new ImportResult();
    result.addCreated("team/a", "Proj_team_a");
    result.addSkipped("team/b", "already imported");
    result.addFailed("team/c", "duplicate id");

    assertEquals(1, result.getCreated().size());
    assertEquals(1, result.getSkipped().size());
    assertEquals(1, result.getFailed().size());
    assertEquals("Proj_team_a", result.getCreated().get(0).detail);

    String json = new Gson().toJson(result);
    assertTrue(json.contains("created"));
    assertTrue(json.contains("skipped"));
    assertTrue(json.contains("failed"));
    assertTrue(json.contains("team/a"));
  }
}
