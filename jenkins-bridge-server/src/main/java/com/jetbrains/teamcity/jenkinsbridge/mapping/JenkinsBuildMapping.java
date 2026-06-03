package com.jetbrains.teamcity.jenkinsbridge.mapping;

import com.jetbrains.teamcity.jenkinsbridge.model.JenkinsBuildInfo;

public class JenkinsBuildMapping {
  private String jenkinsBuildKey;
  private String jenkinsJob;
  private int jenkinsBuildNumber;
  private String jenkinsBuildUrl;
  private Long teamCityBuildId;
  private String teamCityBuildTypeId;
  private String state;
  private int lastLogOffset;
  private boolean metadataLogSent;
  private boolean summaryLogSent;
  private String jenkinsResult;
  private String teamCityFinishDate;
  private String lastError;
  private String createdAt;
  private String updatedAt;

  public static JenkinsBuildMapping create(
      String key,
      String job,
      JenkinsBuildInfo jenkinsInfo,
      String teamCityBuildTypeId,
      String now
  ) {
    JenkinsBuildMapping mapping = new JenkinsBuildMapping();
    mapping.jenkinsBuildKey = key;
    mapping.jenkinsJob = job;
    mapping.jenkinsBuildNumber = jenkinsInfo.getNumber();
    mapping.jenkinsBuildUrl = jenkinsInfo.getUrl();
    mapping.teamCityBuildTypeId = teamCityBuildTypeId;
    mapping.state = JenkinsBuildState.DISCOVERED;
    mapping.lastLogOffset = 0;
    mapping.metadataLogSent = false;
    mapping.summaryLogSent = false;
    mapping.createdAt = now;
    mapping.updatedAt = now;
    return mapping;
  }

  public String getJenkinsBuildKey() {
    return jenkinsBuildKey;
  }

  public String getJenkinsJob() {
    return jenkinsJob;
  }

  public int getJenkinsBuildNumber() {
    return jenkinsBuildNumber;
  }

  public String getJenkinsBuildUrl() {
    return jenkinsBuildUrl;
  }

  public void setJenkinsBuildUrl(String jenkinsBuildUrl) {
    this.jenkinsBuildUrl = jenkinsBuildUrl;
  }

  public Long getTeamCityBuildId() {
    return teamCityBuildId;
  }

  public void setTeamCityBuildId(Long teamCityBuildId) {
    this.teamCityBuildId = teamCityBuildId;
  }

  public String getTeamCityBuildTypeId() {
    return teamCityBuildTypeId;
  }

  public void setTeamCityBuildTypeId(String teamCityBuildTypeId) {
    this.teamCityBuildTypeId = teamCityBuildTypeId;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public int getLastLogOffset() {
    return lastLogOffset;
  }

  public void setLastLogOffset(int lastLogOffset) {
    this.lastLogOffset = lastLogOffset;
  }

  public boolean isMetadataLogSent() {
    return metadataLogSent;
  }

  public void setMetadataLogSent(boolean metadataLogSent) {
    this.metadataLogSent = metadataLogSent;
  }

  public boolean isSummaryLogSent() {
    return summaryLogSent;
  }

  public void setSummaryLogSent(boolean summaryLogSent) {
    this.summaryLogSent = summaryLogSent;
  }

  public String getJenkinsResult() {
    return jenkinsResult;
  }

  public void setJenkinsResult(String jenkinsResult) {
    this.jenkinsResult = jenkinsResult;
  }

  public String getTeamCityFinishDate() {
    return teamCityFinishDate;
  }

  public void setTeamCityFinishDate(String teamCityFinishDate) {
    this.teamCityFinishDate = teamCityFinishDate;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
  }
}
