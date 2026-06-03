package com.jetbrains.teamcity.jenkinsbridge.http;

public class BridgeHttpException extends Exception {
  private final String method;
  private final String url;
  private final int statusCode;
  private final String responseBody;

  public BridgeHttpException(String method, String url, int statusCode, String responseBody) {
    super(method + " " + url + " failed with HTTP " + statusCode + ": " + responseBody);
    this.method = method;
    this.url = url;
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public BridgeHttpException(String method, String url, Throwable cause) {
    super(method + " " + url + " failed: " + cause.getMessage(), cause);
    this.method = method;
    this.url = url;
    this.statusCode = -1;
    this.responseBody = cause.getMessage();
  }

  public String getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getResponseBody() {
    return responseBody;
  }
}
