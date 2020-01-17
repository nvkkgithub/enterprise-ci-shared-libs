package com.vk.jenkins.pipeline.steps.ci

def buildAndJunit(Map params) {
    
    sh 'mvn -e ' + params.command
}

return this