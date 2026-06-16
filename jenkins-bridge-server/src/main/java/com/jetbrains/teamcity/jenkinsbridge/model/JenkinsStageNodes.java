package com.jetbrains.teamcity.jenkinsbridge.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Step nodes of one Pipeline stage, from {@code execution/node/<stageId>/wfapi/describe}. The stage
 * node itself carries no console text; its {@code stageFlowNodes} children (echo / sh / etc.) do.
 * Only nodes that expose a {@code _links.log} href are kept, in flow order, so their logs can be
 * concatenated into the stage's console.
 */
public class JenkinsStageNodes {
  private final List<String> logNodeIds;

  private JenkinsStageNodes(List<String> logNodeIds) {
    this.logNodeIds = logNodeIds;
  }

  public static JenkinsStageNodes empty() {
    return new JenkinsStageNodes(Collections.<String>emptyList());
  }

  public static JenkinsStageNodes fromJson(JsonObject json) {
    JsonArray nodesJson = json.getAsJsonArray("stageFlowNodes");
    if (nodesJson == null || nodesJson.size() == 0) {
      return empty();
    }

    List<String> ids = new ArrayList<String>();
    for (JsonElement nodeJson : nodesJson) {
      if (nodeJson == null || !nodeJson.isJsonObject()) {
        continue;
      }
      JsonObject node = nodeJson.getAsJsonObject();
      if (!hasLogLink(node)) {
        continue;
      }
      JsonElement id = node.get("id");
      if (id != null && !id.isJsonNull()) {
        ids.add(id.getAsString());
      }
    }
    return new JenkinsStageNodes(Collections.unmodifiableList(ids));
  }

  public List<String> getLogNodeIds() {
    return logNodeIds;
  }

  private static boolean hasLogLink(JsonObject node) {
    JsonElement links = node.get("_links");
    if (links == null || !links.isJsonObject()) {
      return false;
    }
    JsonElement log = links.getAsJsonObject().get("log");
    return log != null && log.isJsonObject();
  }
}
