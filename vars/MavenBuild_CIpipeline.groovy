import static groovy.json.JsonOutput.*

def call(Map inputConfig) {

    println prettyPrint(toJson(inputConfig))

	pipeline {
		
		environment {
			JDK_ENV = "${inputConfig.jdkEnv}"
			MAVEN_ENV = "${inputConfig.mavenEnv}"
			MAVEN_OPTS = "${inputConfig.mavenOpts}"
			TEAMS_HOOK_NAME = "${envConfig.microsoft_teams_webhook_name}"
          	TEAMS_HOOK_URL = "${envConfig.microsoft_teams_webhook_url}"
			LC_ALL = "en_US.UTF-8"
		}

		tools {
			maven "${MAVEN_ENV}"
		}
      
		options {
    		timeout(time: 55 , unit: 'MINUTES')
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
            office365ConnectorWebhooks([[
                name: env.TEAMS_HOOK_NAME,
                notifyBackToNormal: true,
                notifyFailure: true,
                notifyRepeatedFailure: true,
                notifySuccess: true,
                notifyUnstable: true,
                url: env.TEAMS_HOOK_URL
            ]])
    	}      
		stages {
			stage('Provision Agent') {
				agent {
					label 'master'
				}
				steps {
					script {
						print '******************** Provioning Agent ****************************'
						checkout([
							$class: 'GitSCM', 
							branches: [[name: "master"]], 
							doGenerateSubmoduleConfigurations: false, 
							extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false,recursiveSubmodules: true, reference: '', trackingSubmodules: false]], 
							gitTool: 'Default',  
							submoduleCfg: [], 
							userRemoteConfigs: [[
							credentialsId: "generic-credentials-id", 
							url: "https://github.com/nvkkgithub/enterprise-ci-terraform.git"]]])
						
						sh 'cd 03-EC2'
						sh 'terraform init'
						sh 'terraform apply --var-file=jenkins-slave/terraform.tfvars'

					}
				}
			}
			stage('Code Checkout') {
				agent {
					label 'rhel-slave'
				}
				steps {
					script {
						checkout([
							$class: 'GitSCM', 
							branches: [[name: inputConfig.branch]], 
							doGenerateSubmoduleConfigurations: false, 
							extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false,recursiveSubmodules: true, reference: '', trackingSubmodules: false]], 
							gitTool: 'Default',  
							submoduleCfg: [], 
							userRemoteConfigs: [[
							credentialsId: inputConfig.git_credential_id, 
							url: inputConfig.url]]])
						
					}
				}
			}

			stage('Build and Junit') {
				agent {
					label 'rhel-slave'
				}
				steps {
					script {
						print '******************** Build and Sonar ************************************'
						script {
							sh inputConfig.app_build_cmd
						}
					}

				}
			}

			stage('Sonar Publish') {
				agent {
					label 'rhel-slave'
				}
				steps {
					script {
						print '******************** Publish Sonar ************************************'

						script {
							sh inputConfig.app_sonar_publish_cmd
						}
					}

				}
			}			

			stage('Quality Pass') {
				agent {
					label 'rhel-slave'
				}
				steps {
					script {
						print '*************************** Quality Pass: Sonar Quality ***************************************'
						timeout(time: 10, unit: 'MINUTES') {
							def qg = waitForQualityGate()
							if(qg.status != 'OK') {
								error "QUALITY GATES ERROR: Sonar Quality Gates Failed"
							}
						}
					}
				}
			}

			stage('Upload To Nexus') {
				agent {
					label 'rhel-slave'
				}
				steps {
					script {
						print '*************************** Uploading To Nexus: mvn deploy ***************************************'
						nexusPublishConfig.mavenRepoCommand = env.REPO_DOWNLOAD_MAVEN_PARAM
						nexusPublish.nexusUpload(nexusPublishConfig)
					}
				}
			}

			stage('Destroy Agent') {
				agent {
					label 'master'
				}
				steps {
					
					script {
						print '******************** Destroying Agent ****************************'
						
					}
				}
			}
		}

		post {
			always {
				script {
					print '********** Post scrips *******************'
					//cleanWs()
				}
			}
		}
	}
}
