package com.jetbrains.teamcity.jenkinsbridge.mapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettings;
import com.jetbrains.teamcity.jenkinsbridge.settings.JenkinsBridgeSettingsProvider;
import jetbrains.buildServer.serverSide.ServerPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsBuildMappingStore {
  private static final Logger LOG = Logger.getLogger(JenkinsBuildMappingStore.class.getName());

  private final JenkinsBridgeSettingsProvider settingsProvider;
  private final ServerPaths serverPaths;
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private Path loadedStateFile;
  private BridgeState state;

  public JenkinsBuildMappingStore(ServerPaths serverPaths, JenkinsBridgeSettingsProvider settingsProvider) {
    this.serverPaths = serverPaths;
    this.settingsProvider = settingsProvider;
  }

  public synchronized JenkinsBuildMapping getOrCreateMapping(String jobName, JenkinsBuildInfo jenkinsInfo) throws IOException {
    ensureLoaded();

    String key = buildKey(jobName, jenkinsInfo.getNumber());
    JenkinsBuildMapping mapping = state.getBuilds().get(key);
    if (mapping == null) {
      mapping = JenkinsBuildMapping.create(key, jobName, jenkinsInfo, settings().getTeamCityBuildTypeId(), now());
      state.getBuilds().put(key, mapping);
      saveState();
      return mapping;
    }

    boolean changed = false;
    if (!nullToEmpty(jenkinsInfo.getUrl()).equals(nullToEmpty(mapping.getJenkinsBuildUrl()))) {
      mapping.setJenkinsBuildUrl(jenkinsInfo.getUrl());
      changed = true;
    }
    if (!settings().getTeamCityBuildTypeId().equals(mapping.getTeamCityBuildTypeId())) {
      mapping.setTeamCityBuildTypeId(settings().getTeamCityBuildTypeId());
      changed = true;
    }

    if (changed) {
      saveMapping(mapping);
    }

    return mapping;
  }

  public synchronized void saveMapping(JenkinsBuildMapping mapping) throws IOException {
    ensureLoaded();
    mapping.setUpdatedAt(now());
    state.getBuilds().put(mapping.getJenkinsBuildKey(), mapping);
    saveState();
  }

  public synchronized void markBuildError(JenkinsBuildMapping mapping, Exception error) {
    try {
      mapping.setState(JenkinsBuildState.FAILED_TO_SYNC);
      mapping.setLastError(error.getMessage());
      saveMapping(mapping);
    } catch (IOException saveError) {
      LOG.log(Level.WARNING, "Failed to persist Jenkins Bridge build error", saveError);
    }
  }

  public synchronized void markPollSuccess() {
    try {
      ensureLoaded();
      state.setLastPollTime(now());
      state.setLastError(null);
      saveState();
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to persist Jenkins Bridge poll status", e);
    }
  }

  public synchronized void markPollError(Exception error) {
    try {
      ensureLoaded();
      state.setLastPollTime(now());
      state.setLastError(error.getMessage());
      saveState();
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to persist Jenkins Bridge poll error", e);
    }
  }

  public Path getStateFile() {
    JenkinsBridgeSettings settings = settings();
    if (settings.hasCustomStateFile()) {
      return settings.getCustomStateFile();
    }

    return serverPaths.getPluginDataDirectory().toPath()
        .resolve("jenkins-bridge")
        .resolve("jenkins-teamcity-mapping.json");
  }

  public static String buildKey(String jobName, int buildNumber) {
    return jobName + "#" + buildNumber;
  }

  private void ensureLoaded() throws IOException {
    Path stateFile = getStateFile();
    if (state != null && stateFile.equals(loadedStateFile)) {
      return;
    }

    if (!Files.exists(stateFile)) {
      state = new BridgeState();
      state.setVersion(1);
      loadedStateFile = stateFile;
      return;
    }

    Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8);
    try {
      state = gson.fromJson(reader, BridgeState.class);
    } catch (JsonParseException e) {
      throw new IOException("Failed to parse Jenkins Bridge state file " + stateFile, e);
    } finally {
      reader.close();
    }

    if (state == null) {
      state = new BridgeState();
    }
    state.setVersion(1);
    state.getBuilds();
    loadedStateFile = stateFile;
  }

  private void saveState() throws IOException {
    Path stateFile = loadedStateFile == null ? getStateFile() : loadedStateFile;
    Path parent = stateFile.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    Path temporaryFile = stateFile.resolveSibling(stateFile.getFileName().toString() + ".tmp");
    Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8);
    try {
      gson.toJson(state, writer);
    } finally {
      writer.close();
    }

    try {
      Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String now() {
    return ZonedDateTime.now(settings().getZoneId()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private JenkinsBridgeSettings settings() {
    return settingsProvider.load();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
