package com.jetbrains.teamcity.jenkinsbridge.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BridgeHttpClient {
  public String get(String url, String user, String password, String accept) throws BridgeHttpException {
    return request("GET", url, user, password, null, null, accept);
  }

  public String post(String url, String user, String password, String body, String contentType, String accept) throws BridgeHttpException {
    return request("POST", url, user, password, body, contentType, accept);
  }

  public String put(String url, String user, String password, String body, String contentType, String accept) throws BridgeHttpException {
    return request("PUT", url, user, password, body, contentType, accept);
  }
// Sends an HTTP request and reads the response and returns String response body
  private String request(
      String method,
      String url,
      String user,
      String password,
      String body,
      String contentType,
      String accept
  ) throws BridgeHttpException {
    HttpURLConnection connection = null;

    try {
      connection = (HttpURLConnection)new URL(url).openConnection();
      connection.setRequestMethod(method);
      connection.setConnectTimeout(30000);
      connection.setReadTimeout(30000);

      // TODO do we need the is not blank?
      if (isNotBlank(user)) {
        String token = user + ":" + nullToEmpty(password);
        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encoded);
      }

      if (isNotBlank(accept)) {
        connection.setRequestProperty("Accept", accept);
      }

      if (body != null) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        if (isNotBlank(contentType)) {
          connection.setRequestProperty("Content-Type", contentType);
        }
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        OutputStream outputStream = connection.getOutputStream();
        try {
          outputStream.write(bytes);
        } finally {
          outputStream.close();
        }
      }

      int status = connection.getResponseCode();
      String responseBody = readResponseBody(connection, status);

      // Do we need this?
      if (status < 200 || status >= 300) {
        throw new BridgeHttpException(method, url, status, responseBody);
      }

      return responseBody;
    } catch (IOException e) {
      throw new BridgeHttpException(method, url, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private String readResponseBody(HttpURLConnection connection, int status) throws IOException {
    InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
    if (stream == null) {
      return "";
    }

    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = stream.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    } finally {
      stream.close();
    }
  }

  private boolean isNotBlank(String value) {
    return value != null && value.trim().length() > 0;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
