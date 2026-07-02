package com.jetbrains.teamcity.jenkinsbridge.teamcity;

import jetbrains.buildServer.serverSide.RunningBuildEx;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class TeamCityArtifactPublisherTest {
  @Test
  public void publishesToResolvedRunningBuild() throws Exception {
    CapturingRunningBuild capture = new CapturingRunningBuild();
    TeamCityArtifactPublisher publisher = new TeamCityArtifactPublisher(new FixedLocator(capture.proxy()), null);

    publisher.publishArtifact(42L, "jenkins-artifacts/out.txt",
        new ByteArrayInputStream("hello".getBytes("UTF-8")));

    assertEquals("jenkins-artifacts/out.txt", capture.path);
    assertEquals("hello", capture.content);
  }

  @Test(expected = java.io.IOException.class)
  public void failsWhenBuildIsNoLongerRunning() throws Exception {
    TeamCityArtifactPublisher publisher = new TeamCityArtifactPublisher(new FixedLocator(null), null);

    publisher.publishArtifact(42L, "jenkins-artifacts/out.txt",
        new ByteArrayInputStream("hello".getBytes("UTF-8")));
  }

  private static class FixedLocator extends TeamCityRunningBuildLocator {
    private final RunningBuildEx runningBuild;

    FixedLocator(RunningBuildEx runningBuild) {
      super(null, null);
      this.runningBuild = runningBuild;
    }

    @Override
    public RunningBuildEx findRunningBuild(long id) {
      return runningBuild;
    }
  }

  private static class CapturingRunningBuild implements InvocationHandler {
    String path;
    String content;

    RunningBuildEx proxy() {
      return (RunningBuildEx) Proxy.newProxyInstance(
          RunningBuildEx.class.getClassLoader(),
          new Class<?>[]{RunningBuildEx.class},
          this);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if ("publishArtifact".equals(method.getName())
          && args != null
          && args.length == 2
          && args[0] instanceof String
          && args[1] instanceof InputStream) {
        path = (String) args[0];
        content = read((InputStream) args[1]);
        return null;
      }
      if ("toString".equals(method.getName())) {
        return "capturing-running-build";
      }
      if ("hashCode".equals(method.getName())) {
        return Integer.valueOf(1);
      }
      if ("equals".equals(method.getName())) {
        return Boolean.valueOf(proxy == args[0]);
      }
      Class<?> returnType = method.getReturnType();
      if (returnType.equals(Boolean.TYPE)) {
        return Boolean.FALSE;
      }
      if (returnType.equals(Integer.TYPE)) {
        return Integer.valueOf(0);
      }
      if (returnType.equals(Long.TYPE)) {
        return Long.valueOf(0L);
      }
      return null;
    }

    private String read(InputStream inputStream) throws Exception {
      StringBuilder result = new StringBuilder();
      int read;
      while ((read = inputStream.read()) != -1) {
        result.append((char) read);
      }
      return result.toString();
    }
  }
}
