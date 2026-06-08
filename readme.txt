
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

 The default state file is stored in TeamCity plugin data:

 <TEAMCITY_DATA_PATH>/system/pluginData/jenkins-bridge/jenkins-teamcity-mapping.json

 2. Build
 Issue this command from the root project to build your plugin against the local
 TeamCity EAP Maven artifacts:

 mvn -Plocal-teamcity-eap -Dteamcity-version=2026.2-SNAPSHOT package

 Plain 'mvn package' currently tries to resolve TeamCity 2026.2 release
 artifacts, which are not available in the configured repositories yet.

 The latest package is written to 'target/jenkins-bridge.zip'. The build also
 keeps a timestamped Git-SHA archive copy in the target directory, for example
 'target/jenkins-bridge-20260608123456-2d8bab4.zip'.
 
 3. Install
 To install the plugin, put zip archive to 'plugins' dir under TeamCity data directory and restart the server.

 
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
   Keep remaining TeamCity REST access over HTTP while migration is incremental.
   TeamCity build finish has started moving to server-side API through an isolated
   TeamCityBuildFinisher adapter. This adapter currently uses TeamCity internal
   server classes because the matching REST endpoint delegates to internal code.

Future plans:

1. Save state somewhere other than JSON.
2. Improve handling for TeamCity down, Jenkins down, restarts, and recovery cases.
3. Continue TeamCity server-side API migration in slices:
   finishDate - in progress
   log
   runningData
   buildQueue
   build restore lookup
