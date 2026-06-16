package com.jetbrains.teamcity.jenkinsbridge.feature;

import java.util.function.Predicate;

/**
 * Builds legal, deterministic TeamCity build-configuration external ids from a target project id and
 * a Jenkins job path. External ids must be {@code [A-Za-z0-9_]} and start with a letter; collisions
 * are resolved with a numeric suffix. Pure (no TeamCity dependencies) so it is unit-testable; the
 * caller supplies the existence check via a {@link Predicate}.
 */
public final class ExternalIdGenerator {
  private ExternalIdGenerator() {
  }

  /** Keeps [A-Za-z0-9_], collapses runs of other chars to a single '_', no leading/trailing '_'. */
  public static String sanitize(String raw) {
    if (raw == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(raw.length());
    boolean pendingUnderscore = false;
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (isWordChar(ch)) {
        if (pendingUnderscore && sb.length() > 0) {
          sb.append('_');
        }
        sb.append(ch);
        pendingUnderscore = false;
      } else {
        pendingUnderscore = true;
      }
    }
    return sb.toString();
  }

  public static String baseExternalId(String projectExternalId, String jenkinsFullName) {
    String project = sanitize(projectExternalId);
    String job = sanitize(jenkinsFullName);
    String base = project.length() == 0 ? job : project + "_" + job;
    if (base.length() == 0) {
      base = "JenkinsJob";
    }
    char first = base.charAt(0);
    if (!isLetter(first)) {
      base = "J" + base;
    }
    return base;
  }

  public static String withSuffix(String base, int n) {
    return n <= 0 ? base : base + "_" + n;
  }

  /** Returns {@code base}, or {@code base_1}, {@code base_2}, ... until {@code exists} is false. */
  public static String resolveUnique(String base, Predicate<String> exists) {
    for (int n = 0; ; n++) {
      String candidate = withSuffix(base, n);
      if (!exists.test(candidate)) {
        return candidate;
      }
    }
  }

  private static boolean isWordChar(char ch) {
    return isLetter(ch) || (ch >= '0' && ch <= '9') || ch == '_';
  }

  private static boolean isLetter(char ch) {
    return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
  }
}
