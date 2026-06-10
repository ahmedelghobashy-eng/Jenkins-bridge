package com.jetbrains.teamcity.jenkinsbridge.persistence;

/**
 * Lifecycle of mirroring one Jenkins build into TeamCity.
 *
 * Persisted by name in the bridge state file (Gson serializes the enum constant name), so the
 * constant names are part of the on-disk format and must not be renamed without a migration.
 */
public enum SyncState {
  DISCOVERED,
  TEAMCITY_CREATED,
  RUNNING_SENT,
  LOG_SYNCING,
  TEAMCITY_FINISHED,
  FAILED_TO_SYNC
}
