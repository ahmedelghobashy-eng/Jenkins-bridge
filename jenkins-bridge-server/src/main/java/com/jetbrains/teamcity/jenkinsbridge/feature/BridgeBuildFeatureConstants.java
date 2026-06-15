package com.jetbrains.teamcity.jenkinsbridge.feature;

/**
 * Stable identifiers for the "Jenkins Bridge" build feature. Shared by the feature definition, its
 * edit JSP, the parameters processor, and the mirrored-job discovery. {@link #TYPE} and the parameter
 * keys are persisted in build-config settings, so they must not change without a migration.
 */
public final class BridgeBuildFeatureConstants {
  public static final String TYPE = "jenkinsBridge";

  /** Required. Jenkins job path (folders separated by {@code /}), e.g. {@code team/my-pipeline}. */
  public static final String PARAM_JENKINS_JOB = "jenkinsJob";

  /** Optional. Per-config cold-start backfill depth; blank falls back to the global default. */
  public static final String PARAM_RECENT_LIMIT = "recentBuildLimit";

  private BridgeBuildFeatureConstants() {
  }
}
