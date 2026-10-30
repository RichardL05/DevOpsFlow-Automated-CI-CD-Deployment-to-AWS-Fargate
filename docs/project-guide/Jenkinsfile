pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
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
                    url: 'https://github.com/RichardL05/DevOpsFlow-Automated-CI-CD-Deployment-to-AWS-Fargate.git'
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
        stage('Deploy to ECS Fargate') {
            steps {
                sh '''#!/bin/bash
set -euo pipefail
export AWS_DEFAULT_REGION=ap-south-2
python3 - <<'PY'
import json
import os
import subprocess
from pathlib import Path

def aws(*args):
    result = subprocess.run(
        ["aws", *args, "--output", "json", "--no-cli-pager"],
        check=True, text=True, capture_output=True)
    return json.loads(result.stdout) if result.stdout.strip() else {}

cluster = "devopsflow-cluster"
service_name = "devopsflow-service"

def service():
    response = aws("ecs", "describe-services", "--cluster", cluster,
                   "--services", service_name)
    if response.get("failures") or len(response.get("services", [])) != 1:
        raise RuntimeError("ECS service lookup failed: " + json.dumps(response))
    return response["services"][0]

current = service()
if current["desiredCount"] != 1:
    raise RuntimeError("Expected exactly one desired task for in-memory storage")
task = aws("ecs", "describe-task-definition",
           "--task-definition", current["taskDefinition"])["taskDefinition"]
if task["family"] != "devopsflow-task":
    raise RuntimeError("Unexpected task definition family")

digest = aws("ecr", "describe-images", "--repository-name", "devopsflow",
             "--image-ids", "imageTag=build-" + os.environ["BUILD_NUMBER"]
             )["imageDetails"][0]["imageDigest"]
image = "358122644682.dkr.ecr.ap-south-2.amazonaws.com/devopsflow@" + digest
containers = [c for c in task["containerDefinitions"] if c["name"] == "devopsflow"]
if len(containers) != 1:
    raise RuntimeError("Expected one devopsflow container")
containers[0]["image"] = image

# Remove response-only fields before registering the next revision.
for field in ("taskDefinitionArn", "revision", "status", "requiresAttributes",
              "compatibilities", "registeredAt", "registeredBy", "deregisteredAt"):
    task.pop(field, None)
definition = Path("target/ecs-task-definition.json")
definition.write_text(json.dumps(task), encoding="utf-8")
new_arn = aws("ecs", "register-task-definition",
              "--cli-input-json", "file://" + str(definition)
              )["taskDefinition"]["taskDefinitionArn"]
print("Registered:", new_arn, flush=True)
aws("ecs", "update-service", "--cluster", cluster, "--service", service_name,
    "--task-definition", new_arn)
try:
    aws("ecs", "wait", "services-stable", "--cluster", cluster,
        "--services", service_name)
except subprocess.CalledProcessError:
    print(json.dumps(service().get("events", [])[:10], indent=2), flush=True)
    raise

deployed = service()
if (deployed["taskDefinition"] != new_arn
        or deployed["runningCount"] != deployed["desiredCount"]
        or deployed["pendingCount"] != 0):
    raise RuntimeError("Service did not settle on the requested revision; check ECS events")
print("ECS deployment stable:", new_arn)
print("Deployed image:", image)
task_arns = aws("ecs", "list-tasks", "--cluster", cluster,
                "--service-name", service_name, "--desired-status", "RUNNING")["taskArns"]
if not task_arns:
    raise RuntimeError("No running service task found")
response = aws("ecs", "describe-tasks", "--cluster", cluster, "--tasks", *task_arns)
if response.get("failures"):
    raise RuntimeError("Task lookup failed: " + json.dumps(response["failures"]))
matching = [t for t in response["tasks"]
            if t["taskDefinitionArn"] == new_arn and t["lastStatus"] == "RUNNING"]
if len(matching) != 1:
    raise RuntimeError("Expected exactly one running task on the new revision")
container = next(c for c in matching[0]["containers"] if c["name"] == "devopsflow")
if container.get("imageDigest") != digest:
    raise RuntimeError("Running container digest does not match published image")
addresses = [n["privateIpv4Address"] for n in container.get("networkInterfaces", [])
             if n.get("privateIpv4Address")]
if len(addresses) != 1:
    raise RuntimeError("Could not identify the task private IP")
endpoint = "http://" + addresses[0] + ":8080"
print("Checking deployed API:", endpoint, flush=True)
subprocess.run(["curl", "--fail", "--silent", "--show-error",
                "--retry", "20", "--retry-all-errors", "--retry-delay", "2",
                "--retry-max-time", "90", "--max-time", "5",
                endpoint + "/actuator/health"], check=True)
subprocess.run(["python3", "scripts/smoke_test.py", endpoint], check=True)
print("Deployed API verification passed.", flush=True)
PY
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
