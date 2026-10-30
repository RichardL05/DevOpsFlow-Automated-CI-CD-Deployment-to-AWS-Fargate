pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "/usr/lib/jvm/java-17-openjdk-amd64/bin:${env.PATH}"
        MAVEN_OPTS = '-Xmx256m'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-devopsflow',
                    url: 'https://github.com/RichardL05/devopsflow.git'
            }
        }

        stage('Build and Test') {
            steps {
                sh 'mvn -B -ntp clean verify'
            }
        }
        stage('Build Docker Image') {
            steps {
                sh 'docker build -t "devopsflow:build-${BUILD_NUMBER}" .'
                sh 'docker image inspect "devopsflow:build-${BUILD_NUMBER}" --format "{{.Id}}"'
            }
        }
        stage('Push to Amazon ECR') {
            steps {
                sh '''#!/bin/bash
set -euo pipefail
set +x
export AWS_DEFAULT_REGION=ap-south-2
REGISTRY=358122644682.dkr.ecr.ap-south-2.amazonaws.com
IMAGE="$REGISTRY/devopsflow:build-${BUILD_NUMBER}"

# Keep the short-lived registry credential outside the workspace.
export DOCKER_CONFIG
DOCKER_CONFIG=$(mktemp -d)
trap 'rm -rf -- "$DOCKER_CONFIG"' EXIT

aws ecr get-login-password |
    docker login --username AWS --password-stdin "$REGISTRY"
docker tag "devopsflow:build-${BUILD_NUMBER}" "$IMAGE"
docker push "$IMAGE"
aws ecr describe-images \
    --repository-name devopsflow \
    --image-ids imageTag="build-${BUILD_NUMBER}" \
    --query 'imageDetails[0].{Tags:imageTags,Digest:imageDigest}' \
    --output json
'''
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml',
                  allowEmptyResults: true
        }
        success {
            archiveArtifacts artifacts: 'target/devopsflow-0.0.1-SNAPSHOT.jar',
                             fingerprint: true
        }
    }
}
