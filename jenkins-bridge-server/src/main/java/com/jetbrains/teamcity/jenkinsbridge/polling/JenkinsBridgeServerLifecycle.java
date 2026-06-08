package com.jetbrains.teamcity.jenkinsbridge.polling;

import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.util.EventDispatcher;

public class JenkinsBridgeServerLifecycle {
  private final EventDispatcher<BuildServerListener> eventDispatcher;
  private final JenkinsBridgePollingService pollingService;
  private final BuildServerListener listener = new BuildServerAdapter() {
    @Override
    public void serverStartup() {
      pollingService.start();
    }

    @Override
    public void serverShutdown() {
      pollingService.stop();
    }
  };

  public JenkinsBridgeServerLifecycle(
      EventDispatcher<BuildServerListener> eventDispatcher,
      JenkinsBridgePollingService pollingService
  ) {
    this.eventDispatcher = eventDispatcher;
    this.pollingService = pollingService;
    this.eventDispatcher.addListener(listener);
  }

  public void dispose() {
    eventDispatcher.removeListener(listener);
    pollingService.stop();
  }
}
