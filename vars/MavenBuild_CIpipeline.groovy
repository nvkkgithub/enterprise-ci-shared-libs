import static groovy.json.JsonOutput.*

def call(Map inputConfig) {

    println prettyPrint(toJson(inputConfig))
	def jenkins_master = "master"
	def jenkins_slave = "rhel-jenkins-slave"
	def skip_dynamic_provision = false

	if(!inputConfig.provisionDynamicAgent) {
		jenkins_slave = "rhel-static-jenkins-slave"
		skip_dynamic_provision = true
	}

	pipeline {
		agent {
			label jenkins_master
		}

		environment {
			MAVEN_ENV = "${inputConfig.mavenEnv}"
			MAVEN_OPTS = "-Xmx1g -XX:MaxPermSize=512m"
			TEAMS_HOOK_NAME = "${inputConfig.microsoft_teams_webhook_name}"
          	TEAMS_HOOK_URL = "${inputConfig.microsoft_teams_webhook_url}"
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
			stage('Provision EC2 Agent') {
				when {
					expression {
						return !skip_dynamic_provision
					}
				}
				agent {
					label jenkins_master
				}
				steps {
					script {
						print '******************** Provioning Agent ****************************'
						checkout([
							$class: 'GitSCM', 
							branches: [[name: "master"]], 
							doGenerateSubmoduleConfigurations: false, 
							extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false, 
											recursiveSubmodules: true, reference: '', trackingSubmodules: false]], 
							gitTool: 'Default',  
							submoduleCfg: [], 
							userRemoteConfigs: [[
							credentialsId: "git-user-id", 
							url: "git@github.com:nvkkgithub/enterprise-ci-iac.git"]]])
						
						withCredentials([[
							$class: 'AmazonWebServicesCredentialsBinding', 
							accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
							credentialsId: 'terraform-provioner-id', 
							secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
							]]) {
								sh 'make jenkins-slave/create'
						}

					}
				}
			}
			stage('Code Checkout') {
				agent {
					label jenkins_slave
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
							credentialsId: "git-user-id", 
							url: inputConfig.url]]])
						
					}
				}
			}

			stage('Build and Junit') {
				agent {
					label jenkins_slave
				}
				steps {
					script {
						script {
							print '******************** Build and Sonar ************************************'
							sh inputConfig.app_build_cmd
						}
					}

				}
			}

			stage('Sonar Publish') {
				agent {
					label jenkins_slave
				}
				steps {
					script {
						script {
							print '******************** Publish Sonar ************************************'
							withCredentials(
								[[
									$class: 'UsernamePasswordMultiBinding', 
									credentialsId: 'sonar7-publish-cred',
									usernameVariable: 'sonarlogin', passwordVariable: 'sonarpassword']]) {
								
								sh inputConfig.app_sonar_publish_cmd + ' -Dsonar.login=${sonarlogin} -Dsonar.password=${sonarpassword}'
							}
							
						}
					}

				}
			}			

			stage('Quality Pass') {
				agent {
					label jenkins_slave
				}
				steps {
					script {
						print '*************************** Quality Pass: Sonar Quality ***************************************'
						// timeout(time: 10, unit: 'MINUTES') {
						// 	def qg = waitForQualityGate()
						// 	if(qg.status != 'OK') {
						// 		error "QUALITY GATES ERROR: Sonar Quality Gates Failed"
						// 	}
						// }
					}
				}
			}

			stage('Upload To Nexus') {
				agent {
					label jenkins_slave
				}
				steps {
					script {
						print '*************************** Uploading To Nexus: mvn deploy ***************************************'
					}
				}
			}

			stage('Destroy EC2 Agent') {
				when {
					expression {
						return !skip_dynamic_provision
					}
				}
				agent {
					label jenkins_master
				}
				steps {
					
					script {
						print '******************** Destroying Agent ****************************'
						withCredentials([[
							$class: 'AmazonWebServicesCredentialsBinding', 
							accessKeyVariable: 'AWS_ACCESS_KEY_ID', 
							credentialsId: 'terraform-provioner-id', 
							secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
							]]) {
								sh 'make jenkins-slave/destroy'
						}
					}
				}
			}
		}

		post {
			always {
				script {
					print '********** Post scrips *******************'
					//cleanWs()
					logstashSend failBuild: true, maxLines: 99999
					try{
						print 'Cleaning up offline nodes'
						for (aSlave in hudson.model.Hudson.instance.slaves) {
                            if (aSlave.getComputer() != null && aSlave.getComputer().isOffline()) {
                                aSlave.getComputer().setTemporarilyOffline(true,null);
                        		println('Name: ' + aSlave.name);
                                aSlave.getComputer().doDoDelete();
                            }
                        }
					} catch(e) {
						print e.toString()  
					}
				}
			}
		}
	}
}
