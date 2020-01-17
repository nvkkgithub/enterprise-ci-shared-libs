## Jenkins Shared Libraries
This is a groovy project and contains shared libraries those will be re-used by individual applications/projects in Emirates Group to minimise CI effort in building Pipeline.
These libraries developed considering various flavours of application source code type i.e., Java, .NET, NodeJS, AngularJS, ReactJS. 

## Folder Structure 
Pipeline folders can referred from below directory
* src/com/emirates/jenkins/pipeline/steps/ci : All common steps defined required for Continous Integration
* src/com/emirates/jenkins/pipeline/steps/cd :  All common steps defined required for Continous Delivery / Deployment


Each file represents individual process in Pipeline.
* vars/<file_name>:
Each file represents typeof the pipeline. eg: JavaCIpipeline.groovy - for Java pipeline.

Each application shall either import complete pipeline as a service or individual shared library to achieve the purpose of CI/CD process.

## Available Libraries
Following are various shared libraries available in this project.

* Standard_JavaCI_pipeline
* Standard_DotNetCI_pipeline

## Standard_JavaCI_pipeline: How to Integrate
Inputs

Example Usage
```
// Import pipeline libraries
@Library('jenkins-library@master') _

// Initialize parameters
def stepsConfig = [
	skipBuildAndTest: false,
	skipSonarPublish: false,
	skipQualityGates: false,
	skipNexusUpload: false,
]
def envConfig = [
	slaveAgentType : "", // rhel-jenkins-slave OR SlaveWindows 
	jdkEnv : "", // JDK8 or JDK7 or jdk8-windows-slave
	mavenEnv : "", // MAVEN3.6 or MAVEN3.5 or MAVEN2.2
	nodeJsEnv : "", // NODE10
	nodeJsPackages: "", // if-applicable-space-seperated-node-js-packages-to-run
	mavenOpts : "-Xmx1g -XX:MaxPermSize=512m",
	microsoft_teams_webhook_name: "<application-ci-identification>",
	microsoft_teams_webhook_url: "<micro-soft-teams-notification-url>"
]
def scmConfig = [
	branch : "develop",
	git_credential_id : "git-ek", // default to 'git-ek'
	url : "<git-url>",
	pr_id: "${params.PULL_REQUEST_ID}",
	from_branch: "${params.PULL_REQUEST_FROM_BRANCH}"
]
def buildAndTestConfig = [
	build_command: "", // clean install
	skip_test_cases : false,
	maven_settings_file : "settings.xml", // check-in the setting.xml file into GIT.
	pom_file_location : "pom.xml",
	profile_name : "",
	pre_build_commands : "", // custom-commands-before the build triggers
	post_build_commands : ""
]
def sonarConfig = [
	sonarInstance: "",  // ekgsonarserver4 or ekgsonarserver7
	sonarHostUrl: "",  // http://sonardashboard.emirates.com or http://sonarqube.emirates.com
	mavenSettingsFile: "settings.xml",
	pom_file_location : "pom.xml",
	sonarPluginCommand: "sonar:sonar",
	sendSonarDbCredentials: false,
	sonarProjectKey: "",
	sonarAuthToken: ""
]
def qualityGateConfig = [
	waitTime: 15,
	waitTimeType: "MINUTES",
    qGateSleepInSeconds: 120,
	qualityFailMsg: "Quality Gates Failed"
]
def nexusPublishConfig = [
	nexusServerID: "nexus-snapshots",
	layout: "default", 
	artefactsReleaseURL: "<nexus-publish-Url",
	nexusCredentialId : "nexus",
	mavenSettingsFile: "settings.xml",
	pom_files_locations: ["pom.xml"]
]

// Invoke CI pipeline
Standard_JavaCI_pipeline(
	stepsConfig, envConfig, scmConfig, 
	buildAndTestConfig, sonarConfig, 
	qualityGateConfig, nexusPublishConfig
)

```