package com.jetbrains.teamcity.jenkinsbridge.settings;

/**
 * One Jenkins-job -> TeamCity-build-config mapping the poller should mirror.
 *
 * <p>The Jenkins connection (URL/user/token) is <b>global</b> (see {@link JenkinsBridgeSettings});
 * a mapping only names the Jenkins job and the target TeamCity build config (the config that hosts
 * the Jenkins Bridge build feature). A mapping is either feature-derived or the single legacy
 * fallback synthesized from global settings when no feature exists.
 */
public final class JenkinsJobMapping {
  private final String jenkinsJob;
  private final String teamCityBuildTypeExternalId;
  private final String teamCityBuildTypeName;
  // 0 means "no per-mapping override; use the global recentBuildLimit".
  private final int recentBuildLimitOverride;
  private final boolean legacy;

  public JenkinsJobMapping(
      String jenkinsJob,
      String teamCityBuildTypeExternalId,
      String teamCityBuildTypeName,
      int recentBuildLimitOverride,
      boolean legacy
  ) {
    this.jenkinsJob = JenkinsBridgeSettings.nullToEmpty(jenkinsJob).trim();
    this.teamCityBuildTypeExternalId = JenkinsBridgeSettings.nullToEmpty(teamCityBuildTypeExternalId).trim();
    this.teamCityBuildTypeName = JenkinsBridgeSettings.nullToEmpty(teamCityBuildTypeName).trim();
    this.recentBuildLimitOverride = Math.max(0, recentBuildLimitOverride);
    this.legacy = legacy;
  }

  /** The single legacy mapping derived from global settings (used when no build feature exists). */
  public static JenkinsJobMapping fromGlobalSettings(JenkinsBridgeSettings settings) {
    return new JenkinsJobMapping(
        settings.getJenkinsJob(),
        settings.getTeamCityBuildTypeId(),
        settings.getTeamCityBuildTypeId(),
        0,
        true
    );
  }

  public String getJenkinsJob() {
    return jenkinsJob;
  }

  public String getTeamCityBuildTypeExternalId() {
    return teamCityBuildTypeExternalId;
  }

  public String getTeamCityBuildTypeName() {
    return JenkinsBridgeSettings.isNotBlank(teamCityBuildTypeName)
        ? teamCityBuildTypeName : teamCityBuildTypeExternalId;
  }

  public boolean isLegacy() {
    return legacy;
  }

  /**
   * Prefix that namespaces this mapping's persisted mirrors and its polling watermark.
   *
   * <p>Feature mappings use {@code <externalId>::<job>} so two configs mirroring the same Jenkins
   * job don't collide. The legacy mapping keeps the bare job name to stay byte-compatible with
   * pre-existing on-disk state and the {@code jenkins.build.key} of already-created builds.
   */
  public String getMirrorKeyPrefix() {
    return legacy ? jenkinsJob : teamCityBuildTypeExternalId + "::" + jenkinsJob;
  }

  public int getEffectiveRecentBuildLimit(int globalDefault) {
    return recentBuildLimitOverride > 0 ? recentBuildLimitOverride : globalDefault;
  }

  public boolean hasMinimumConfiguration() {
    return JenkinsBridgeSettings.isNotBlank(jenkinsJob)
        && JenkinsBridgeSettings.isNotBlank(teamCityBuildTypeExternalId);
  }

  public String describeMinimumConfigurationProblem() {
    if (hasMinimumConfiguration()) {
      return "";
    }
    StringBuilder result = new StringBuilder("Jenkins Bridge mapping is missing configuration:");
    if (!JenkinsBridgeSettings.isNotBlank(jenkinsJob)) {
      result.append(" jenkinsJob");
    }
    if (!JenkinsBridgeSettings.isNotBlank(teamCityBuildTypeExternalId)) {
      result.append(" teamCityBuildType");
    }
    return result.toString();
  }

  public String describeForLog() {
    return (legacy ? "legacy" : "feature") + " mapping job=" + jenkinsJob
        + " -> buildType=" + teamCityBuildTypeExternalId
        + (recentBuildLimitOverride > 0 ? " (recentBuildLimit=" + recentBuildLimitOverride + ")" : "");
  }
}
