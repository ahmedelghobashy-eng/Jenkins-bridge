
 Jenkins Bridge TeamCity server-side plugin

 This plugin mirrors one Jenkins job into one TeamCity build configuration as shallow
 agentless TeamCity builds. It polls Jenkins, creates one TeamCity build per Jenkins
 build, streams Jenkins console output into the TeamCity build log, and finishes the
 TeamCity build when Jenkins reports that the source build is complete.

 1. Configure

 The current MVP reads configuration from TeamCity parameters first, then Java
 system properties, then environment variables, then local defaults.

 The plugin first reads jenkins.bridge.teamCityBuildTypeId from the root project
 parameters, system properties, environment variables, or the local default.
 After it finds that build configuration, the rest of the settings are read in
 this order:

 1. Target build configuration parameter
 2. Root project parameter
 3. Java system property
 4. Environment variable
 5. Local default

 jenkins.bridge.enabled / JENKINS_BRIDGE_ENABLED
 jenkins.bridge.jenkinsUrl / JENKINS_URL
 jenkins.bridge.jenkinsUser / JENKINS_USER
 jenkins.bridge.jenkinsToken / JENKINS_TOKEN
 jenkins.bridge.jenkinsJob / JENKINS_JOB
 jenkins.bridge.teamCityUrl / TEAMCITY_URL
 jenkins.bridge.teamCityUser / TEAMCITY_USER
 jenkins.bridge.teamCityPassword / TEAMCITY_PASSWORD
 jenkins.bridge.teamCityBuildTypeId / TEAMCITY_BUILD_TYPE_ID
 jenkins.bridge.pollSeconds / BRIDGE_POLL_SECONDS
 jenkins.bridge.recentBuildLimit / RECENT_BUILDS_LIMIT
 jenkins.bridge.timeZone / TIMEZONE
 jenkins.bridge.stateFile / BRIDGE_STATE_FILE

 If a TeamCity parameter value references another parameter, such as
 %another.param%, the plugin resolves it through TeamCity's value resolver.

 Local defaults match the Python prototype:

 Jenkins URL: http://localhost:8080
 Jenkins user: Ahmed
 Jenkins job: tc-test
 TeamCity URL: http://localhost:8111/bs/httpAuth
 TeamCity user: Ahmed
 TeamCity password: test
 TeamCity build configuration id: TestTc_JenkinsTcTest
 Poll interval: 10 seconds
 Recent build limit: 1
 Time zone: Europe/Berlin

 'Recent build limit' is the cold-start backfill depth: on the first poll for a
 job the bridge starts from (latest build - recentBuildLimit), so it mirrors only
 the most recent build(s) instead of replaying the whole Jenkins history. After
 that, polling is incremental by build number (every build after the last one is
 picked up), so this value no longer bounds steady-state discovery.

 The default state file is stored in TeamCity plugin data:

 <TEAMCITY_DATA_PATH>/system/pluginData/jenkins-bridge/jenkins-teamcity-mapping.json

 2. Build
 Issue this command from the root project:

 mvn package

 This builds against the local TeamCity EAP Maven artifacts (TeamCity
 2026.2-SNAPSHOT). The build no longer needs explicit flags: teamcity-version
 defaults to 2026.2-SNAPSHOT, and the 'local-teamcity-eap' profile is active by
 default so the local EAP repository is always on the classpath. The older
 'mvn -Plocal-teamcity-eap -Dteamcity-version=2026.2-SNAPSHOT package' command
 still works and is now equivalent.

 The TeamCity 2026.2-SNAPSHOT artifacts must be present in the local EAP repo
 at '${user.home}/.m2/repository/TeamCity'. They are internal EAP artifacts and
 are not published to the public JetBrains repository.

 The latest package is written to 'target/jenkins-bridge.zip'. The build also
 keeps a timestamped Git-SHA archive copy in the target directory, for example
 'target/jenkins-bridge-20260608123456-2d8bab4.zip'.
 
 3. Install
 To install the plugin, put zip archive to 'plugins' dir under TeamCity data directory and restart the server.

 Paused and long-running builds

 A Jenkins build that is paused - either paused by a user (Pipeline
 Pause/Resume) or waiting at an 'input' step - still reports itself as building
 over the REST API (building=true, no result). Jenkins does not expose "paused"
 as a distinct state (see JENKINS-56556), so the bridge cannot tell a paused
 build apart from one that is actively running.

 The bridge therefore keeps the mirrored TeamCity build in the running state for
 the whole pause. It does not prematurely finish it, it keeps mirroring other
 and newer Jenkins builds in the meantime, and it finishes the TeamCity build
 with the correct result once Jenkins resumes (success/failure) or the build is
 aborted (cancelled). The pause itself is not labelled in TeamCity - the build
 simply shows as running.


TODO:

Current scope:
0.create a git repo.
1. Proper project structure - done
   Code is split into clear packages:
   jenkins
   teamcity
   mapping
   polling
   settings
   model
   http
-> keep shared http package while both Jenkins and TeamCity use REST/HTTP.
-> maybe rename the polling package name to something more descriptive to show that it is the main part of the plugin?

2. Testing
   Add tests for:
   Jenkins API response parsing
   Jenkins build key generation
   mapping create/update behavior
   log offset handling
   finish date formatting
   mocked REST calls

3. Keep TeamCity REST usage behind a TeamCityClient - done
   Keep TeamCity username/password in plugin config.
   Keep Jenkins access over HTTP.
   TeamCity REST is now active only for restore lookup by jenkins.build.key.
   Queue creation, runningData/start, log append, and finish are handled through
   isolated server-side adapters. Deprecated REST mutation methods remain in
   TeamCityClient only as temporary fallback code while runtime testing continues.
   Some adapters use TeamCity internal server classes because the matching REST
   endpoints delegate to internal code.

Future plans:

1. Save state somewhere other than JSON.
2. Improve handling for TeamCity down, Jenkins down, restarts, and recovery cases.
3. Continue TeamCity server-side API migration in slices:
   finishDate - implemented
   log - implemented
   runningData - implemented
   buildQueue - implemented
   build restore lookup - shelved for more research
