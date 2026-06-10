package com.jetbrains.teamcity.jenkinsbridge.http;

import java.util.Map;
import java.util.TreeMap;

/**
 * A minimal HTTP response that also exposes headers, used where callers need response metadata
 * (for example Jenkins progressive log fetching, which returns the next byte offset and a
 * "more data" flag in {@code X-Text-Size} / {@code X-More-Data} headers).
 */
public class BridgeHttpResponse {
  private final int statusCode;
  private final String body;
  private final Map<String, String> headers;

  public BridgeHttpResponse(int statusCode, String body, Map<String, String> headers) {
    this.statusCode = statusCode;
    this.body = body == null ? "" : body;
    // HTTP header names are case-insensitive; store them that way so lookups don't depend on casing.
    Map<String, String> caseInsensitive = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    if (headers != null) {
      caseInsensitive.putAll(headers);
    }
    this.headers = caseInsensitive;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }

  public String getHeader(String name) {
    return name == null ? null : headers.get(name);
  }
}
