package com.jetbrains.teamcity.jenkinsbridge.mapping;

public final class JenkinsBuildState {
  public static final String DISCOVERED = "DISCOVERED";
  public static final String TEAMCITY_CREATED = "TEAMCITY_CREATED";
  public static final String RUNNING_SENT = "RUNNING_SENT";
  public static final String LOG_SYNCING = "LOG_SYNCING";
  public static final String TEAMCITY_FINISHED = "TEAMCITY_FINISHED";
  public static final String FAILED_TO_SYNC = "FAILED_TO_SYNC";

  private JenkinsBuildState() {
  }
}
