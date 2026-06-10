package com.jetbrains.teamcity.jenkinsbridge.model;

/**
 * A slice of a Jenkins build's console log returned by the progressive log API
 * ({@code /logText/progressiveText?start=<byteOffset>}).
 *
 * {@link #getNextStart()} is the byte offset to request next (Jenkins' {@code X-Text-Size}); it is
 * passed back as {@code start} on the following poll so only new bytes are fetched.
 * {@link #hasMoreData()} reflects Jenkins' {@code X-More-Data} header (the build may still produce
 * more output).
 */
public class JenkinsLogChunk {
  private final String text;
  private final long nextStart;
  private final boolean hasMoreData;

  public JenkinsLogChunk(String text, long nextStart, boolean hasMoreData) {
    this.text = text == null ? "" : text;
    this.nextStart = nextStart;
    this.hasMoreData = hasMoreData;
  }

  public String getText() {
    return text;
  }

  public long getNextStart() {
    return nextStart;
  }

  public boolean hasMoreData() {
    return hasMoreData;
  }
}
