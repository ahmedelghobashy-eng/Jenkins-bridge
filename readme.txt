
 Jenkins Bridge TeamCity server-side plugin

 This plugin mirrors one Jenkins job into one TeamCity build configuration as shallow
 agentless TeamCity builds. It polls Jenkins, creates one TeamCity build per Jenkins
 build, streams Jenkins console output into the TeamCity build log, and finishes the
 TeamCity build when Jenkins reports that the source build is complete.

 1. Configure

 The first MVP reads configuration from Java system properties first, then
 environment variables, then local defaults:

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
 Issue 'mvn package' command from the root project to build your plugin. Resulting package <artifactId>.zip will be placed in 'target' directory. 
 
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
-> maybe move shared http package under jenkins after TeamCity no longer uses REST?
-> maybe rename the polling package name to something more descriptive to show that it is the main part of the plugin?

2. Testing
   Add tests for:
   Jenkins API response parsing
   Jenkins build key generation
   mapping create/update behavior
   log offset handling
   finish date formatting
   mocked REST calls

3. Move TeamCity REST usage to TeamCity server-side API
   Remove TeamCity username/password from plugin config.
   Replace internal TeamCity REST calls with TeamCity server services.
   Keep Jenkins access over HTTP.
   After TeamCity no longer uses REST, move the shared http package under jenkins
   because HTTP will be Jenkins-only.

Future plans:

1. Save state somewhere other than JSON.
2. Improve handling for TeamCity down, Jenkins down, restarts, and recovery cases.
