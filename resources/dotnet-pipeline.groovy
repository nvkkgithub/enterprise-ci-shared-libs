import com.emirates.jenkins.shared.library.*
import static groovy.json.JsonOutput.*
    def call(Map config) {
	println prettyPrint(toJson(config))
	def checkout = new ScmCheckout()
    def restore = new Nuget()
    def build = new MSbuild()
    def sonar = new SotnetSonar()
    def quality = new SonarQualityGates()
    def zipcr = new ZipCreation()
    def nexup = new NexusUpload()
pipeline {
    options {
    timeout(time: config.timeout ?: 60 , unit: 'MINUTES')
    buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '10', numToKeepStr: '10')
    }
    agent any
    environment {
			SCM_REPO_URL = "${config.scmRepoUrl}"
			SCM_BRANCH_NAME = "${config.scmBranchName}"
            CRED_ID = "${config.credId}"
            SONAR_PATH = "${config.sonar_path}"
            SONAR_KEY = "${config.sonar_key}"
            SQL_URL = "${config.sq_url}"
            SONAR_TOKEN = "${config.sonar_token}"
            MSPATH = "${config.msPath}"
            NUGET_PATH = "${config.nuget_path}"
            PRJSLN = "${config.prjSln}"
            SOURCEBRANCH = "${config.sourceBranch}"
            CFG_DBG = "${config.cfg_dbg}"
            PLT_ANYCPU = "${config.plt_anycpu}"
            PRJNAME = "${config.prj_name}"
            PUBLISH_PATH = "${config.publish_path}"
            ZIP_LOC = "${config.zip_loc}"
            ZIPFILE = "${config.zip_file}"
            CURL_LOC = "${config.curl_loc}"
            NEX_PATH = "${config.nex_path}"
            CRED_ID_NEX = "${config.credIdNexus}"
            NEX_REPO = "${config.nex_repo}"
		}
      stages {
            stage('Checkout GIT Repo') {
                steps {
                    script {
                        def gitDetails = checkout.standard(
                            branch: "${SCM_BRANCH_NAME}",
                            credId: "${CRED_ID}",
							url: "${SCM_REPO_URL}",
                        )
                    }}}   

            stage('Nuget Restore') {
                steps {
                    script {
                       def nugetDetails = restore.nuget_step(
                            nuget_path: "${NUGET_PATH}",
                            prjSln: "${PRJSLN}",
                            prj_name: "${PRJNAME}",
                            workspace: "${workspace}",
                        )
                    }}}      
/*
            stage('Dotnet Build') {
                 steps {
                    script {
                        def msbuildDetails = build.msbuild(
                            mspath: "${MSPATH}",
                            prjSln: "${PRJSLN}",
                            workspace: "${workspace}",
                            cfg_dbg: "${CFG_DBG}",
                            prj_name: "${PRJNAME}",
                            plt_anycpu: "${PLT_ANYCPU}"
                        )
                }}} */

            stage('MS Build and Sonar Analysis') {
                 steps {
                    script {
                        withSonarQubeEnv("${config.sonar_inst}") {
                        def sonarDetails = sonar.sqanalysis(
                            sonarPath: "${SONAR_PATH}",
                            mspath: "${MSPATH}",
                            sonarKey: "${SONAR_KEY}",
                            sonarUrl: "${SQL_URL}",
                            sourceBranch: "${SOURCEBRANCH}",
                            prjSln: "${PRJSLN}",
                        )
                }}}}
/*
            stage('Sonar Quality Gate Check') {
							options {
                                timeout(time: 30, unit: 'MINUTES')
                                   }
                                steps{
                                    sleep(60)
                                    waitForQualityGate abortPipeline: true
                                }              
            }
*/
            stage('Remove File Extensions before Zip') {
                    steps {
                        script {
                            bat "cd ${workspace}\\${PUBLISH_PATH}"
                            bat "DEL /S /Q *.sln *.suo *.publishproj *.vb *.cs *.csproj *.vbproj"
                            bat "cd ${workspace}\\${PUBLISH_PATH}\\bin"
                            bat "DEL /S /Q *.pdb"
                }}}

            stage('Zip File Creation') {
                 steps {
                    script {
                        def zipcrDetails = zipcr.zipstep(
                            workspace: "${workspace}",
                            publish_path: "${PUBLISH_PATH}",
                            zip_loc: "${ZIP_LOC}",
                            zip_file: "${ZIPFILE}",
                        )
                }}}

            stage('Copy Zip to Nexus_Upload Folder') {
                    steps {
                        script {
                            bat "xcopy /K /D /H /Y ${workspace}\\${ZIPFILE} ${NEX_PATH}\\."
                }}}

            stage('Nexus Upload') {
                 steps {
                    script {
                        def nexupDetails = nexup.nexus_step(
                            curl_loc: "${CURL_LOC}",
                            nex_path: "${NEX_PATH}",
                            zip_file: "${ZIPFILE}",
                            nex_un: "${NEX_UN}",
                            nex_pwd: "${NEX_PWD}",
                            prj_name: "${PRJNAME}",
                            nex_repo: "${NEX_REPO}"
                        )
                }}}
      }}}
