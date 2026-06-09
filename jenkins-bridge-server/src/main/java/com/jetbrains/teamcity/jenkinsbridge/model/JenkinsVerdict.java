package com.jetbrains.teamcity.jenkinsbridge.model;

import java.util.Locale;

/**
 * TeamCity-facing interpretation of a Jenkins build result.
 *
 * Jenkins reports SUCCESS, FAILURE, UNSTABLE, ABORTED, or NOT_BUILT (and {@code null} while the
 * build is still running). TeamCity only distinguishes success / failure / interrupted, so the
 * Jenkins values collapse onto those buckets when the mirrored build is finished.
 */
public enum JenkinsVerdict {
  SUCCESS,
  FAILURE,
  UNSTABLE,
  ABORTED,
  NOT_BUILT,
  UNKNOWN;

  /**
   * Translates a raw Jenkins {@code result} string into a verdict. Null, blank, or unrecognized
   * values map to {@link #UNKNOWN} so the caller can surface them as a build problem rather than
   * silently treating the build as successful.
   */
  public static JenkinsVerdict fromResult(String jenkinsResult) {
    if (jenkinsResult == null) {
      return UNKNOWN;
    }

    String normalized = jenkinsResult.trim().toUpperCase(Locale.ROOT);
    if (normalized.length() == 0) {
      return UNKNOWN;
    }

    switch (normalized) {
      case "SUCCESS":
        return SUCCESS;
      case "FAILURE":
        return FAILURE;
      case "UNSTABLE":
        return UNSTABLE;
      case "ABORTED":
        return ABORTED;
      case "NOT_BUILT":
        return NOT_BUILT;
      default:
        return UNKNOWN;
    }
  }
}
