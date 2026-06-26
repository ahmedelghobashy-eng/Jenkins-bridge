package com.jetbrains.teamcity.jenkinsbridge.web;

import com.jetbrains.teamcity.jenkinsbridge.feature.BridgeBuildFeatureConstants;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.web.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class JenkinsBuildSummaryExtension extends BaseController {
    static final String PATH = "/jenkinsBuildSummary.html";
    private static final String PARAM_JENKINS_BUILD_URL = "jenkins.build.url";

    private final PluginDescriptor pluginDescriptor;
    private final ProjectManager projectManager;

    public JenkinsBuildSummaryExtension(
            @NotNull WebControllerManager webControllerManager,
            @NotNull PagePlaces pagePlaces,
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull ProjectManager projectManager
    ) {
        this.pluginDescriptor = pluginDescriptor;
        this.projectManager = projectManager;
        webControllerManager.registerController(PATH, this);
        new BuildSummaryPageExtension(pagePlaces);
    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response) {
        String url = jenkinsJobUrl(request);
        if (url == null) return null;
        ModelAndView mv = new ModelAndView(pluginDescriptor.getPluginResourcesPath("jenkinsBuildSummary.jsp"));
        mv.getModel().put("jenkinsUrl", url);
        return mv;
    }

    private class BuildSummaryPageExtension extends SimplePageExtension {
        BuildSummaryPageExtension(@NotNull PagePlaces pagePlaces) {
            super(pagePlaces, new PlaceId("SAKURA_BUILD_CONFIGURATION_BUILDS"),
                    pluginDescriptor.getPluginName(), PATH);
            register();
        }

        @Override
        public boolean isAvailable(@NotNull HttpServletRequest request) {
            return jenkinsJobUrl(request) != null;
        }
    }

    /**
     * Loop through all previous runs to find one with a link to Jenkins, then remove the run number from the link to get the URL of the Jenkins job.
     */
    @Nullable
    private String jenkinsJobUrl(@NotNull HttpServletRequest request) {
        SBuildType buildType = findBuildType(request);
        if (buildType == null) return null;
        if (buildType.getBuildFeaturesOfType(BridgeBuildFeatureConstants.TYPE).isEmpty()) return null;
        List<SFinishedBuild> history = buildType.getHistory();
        for (SFinishedBuild build : history) {
            String buildUrl = build.getParametersProvider().get(PARAM_JENKINS_BUILD_URL);
            if (!StringUtil.isEmpty(buildUrl)) {
                return stripRunNumber(buildUrl);
            }
        }
        return null;
    }

    @NotNull
    private static String stripRunNumber(@NotNull String buildUrl) {
        String urlNotEndingInSlash = buildUrl.endsWith("/") ? buildUrl.substring(0, buildUrl.length() - 1) : buildUrl;
        int lastSlash = urlNotEndingInSlash.lastIndexOf('/');
        return lastSlash > 0 ? urlNotEndingInSlash.substring(0, lastSlash + 1) : buildUrl;
    }

    @Nullable
    private SBuildType findBuildType(@NotNull HttpServletRequest request) {
        String id = PluginUIContext.getFromRequest(request).getBuildTypeId();
        if (StringUtil.isEmpty(id)) {
            id = request.getParameter("buildTypeId");
        }
        if (StringUtil.isEmpty(id)) return null;
        try {
            SBuildType bt = projectManager.findBuildTypeByExternalId(id);
            return bt != null ? bt : projectManager.findBuildTypeById(id);
        } catch (AccessDeniedException e) {
            return null;
        }
    }
}
