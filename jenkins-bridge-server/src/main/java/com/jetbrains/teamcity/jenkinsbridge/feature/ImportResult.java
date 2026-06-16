package com.jetbrains.teamcity.jenkinsbridge.feature;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of an import run, grouped into created / skipped / failed. Serialized to JSON (Gson,
 * field-based) for the import controller's response.
 */
public class ImportResult {
  public static final class Entry {
    public final String jenkinsJob;
    // externalId of the created config, or the reason for skipped/failed.
    public final String detail;

    public Entry(String jenkinsJob, String detail) {
      this.jenkinsJob = jenkinsJob;
      this.detail = detail;
    }
  }

  private final List<Entry> created = new ArrayList<Entry>();
  private final List<Entry> skipped = new ArrayList<Entry>();
  private final List<Entry> failed = new ArrayList<Entry>();

  public void addCreated(String jenkinsJob, String externalId) {
    created.add(new Entry(jenkinsJob, externalId));
  }

  public void addSkipped(String jenkinsJob, String reason) {
    skipped.add(new Entry(jenkinsJob, reason));
  }

  public void addFailed(String jenkinsJob, String reason) {
    failed.add(new Entry(jenkinsJob, reason));
  }

  public List<Entry> getCreated() {
    return created;
  }

  public List<Entry> getSkipped() {
    return skipped;
  }

  public List<Entry> getFailed() {
    return failed;
  }
}
