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
        stage('Deploy and Verify') {
            steps {
                sh '''#!/bin/bash
set -euo pipefail
IMAGE="358122644682.dkr.ecr.ap-south-2.amazonaws.com/devopsflow:build-${BUILD_NUMBER}"
docker image inspect "$IMAGE" >/dev/null

# Keep one previous container for rollback.
if docker container inspect devopsflow-app-previous >/dev/null 2>&1; then
    docker rm devopsflow-app-previous
fi
had_previous=false
if docker container inspect devopsflow-app >/dev/null 2>&1; then
    docker stop devopsflow-app
    docker rename devopsflow-app devopsflow-app-previous
    had_previous=true
fi

rollback() {
    result=$?
    if [ "$result" -ne 0 ]; then
        docker logs --tail 60 devopsflow-app || true
        docker rm -f devopsflow-app || true
        if [ "$had_previous" = true ]; then
            docker rename devopsflow-app-previous devopsflow-app
            docker start devopsflow-app
            echo "Restored previous container; deployment failed."
        fi
    fi
}
trap rollback EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker run -d --pull never --name devopsflow-app \
    --restart unless-stopped --memory 384m \
    -e JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m" \
    -e SERVER_ADDRESS=0.0.0.0 \
    -p 127.0.0.1:8083:8080 "$IMAGE"

curl --fail --silent --show-error \
    --retry 20 --retry-all-errors --retry-delay 2 \
    --retry-max-time 90 --max-time 5 \
    http://127.0.0.1:8083/actuator/health
python3 scripts/smoke_test.py http://127.0.0.1:8083
echo "Deployment and API verification passed: $IMAGE"
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
