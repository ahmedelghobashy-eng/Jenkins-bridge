package com.jetbrains.teamcity.jenkinsbridge.persistence;

/**
 * Per-stage sync watermark for one Pipeline stage of a {@link BuildMirror}. Tracks whether the
 * stage's TeamCity build-step block has been opened/closed and how much of its console text has
 * already been mirrored, so live polling never re-emits a block or duplicates log lines.
 */
public class StageMirror {
  private String name;
  private String status;
  private boolean blockOpened;
  // Number of characters of the (console-note-stripped) stage log already mirrored.
  private long logOffset;
  private boolean blockClosed;

  // Gson needs a no-arg constructor.
  public StageMirror() {
  }

  public StageMirror(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isBlockOpened() {
    return blockOpened;
  }

  public void setBlockOpened(boolean blockOpened) {
    this.blockOpened = blockOpened;
  }

  public long getLogOffset() {
    return logOffset;
  }

  public void setLogOffset(long logOffset) {
    this.logOffset = logOffset;
  }

  public boolean isBlockClosed() {
    return blockClosed;
  }

  public void setBlockClosed(boolean blockClosed) {
    this.blockClosed = blockClosed;
  }
}
