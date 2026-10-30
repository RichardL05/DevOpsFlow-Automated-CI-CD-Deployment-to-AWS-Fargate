# DevOps Flow: setup and delivery guide

## 1. What this project does

A change merged into GitHub main starts Jenkins. Jenkins builds a supplied Java application, runs its tests, creates a Docker image, pushes it to Amazon ECR, and deploys it to ECS Fargate. It then checks that the new application works.

This is a DevOps learning project. The Java to-do API and its tests were supplied with assistant help. The learner configured the tools and AWS resources and verified the results. The API is a sample workload; developing new application features is outside this guide.

```text
GitHub push to main
  → Jenkins on EC2
  → Maven build and tests
  → Docker image
  → Amazon ECR
  → ECS Fargate
  → Health and API checks
```

| Tool | Simple purpose |
| --- | --- |
| GitHub | Store the source, Dockerfile, and Jenkinsfile |
| Jenkins | Run the delivery steps |
| Maven | Turn Java source into a JAR and run tests |
| Docker | Put the JAR and Java runtime into an image |
| ECR | Store images |
| ECS Fargate | Run the application container |
| IAM role | Give AWS permissions without saved access keys |
| Security group | Control network access |

## 2. Environment used

Region: Hyderabad, ap-south-2. Existing repository: RichardL05/devopsflow.

| Resource | Name or setting |
| --- | --- |
| Jenkins EC2 name | devopsflow_server |
| Selected instance type | m7i-flex.large |
| Selected disk | 30 GiB gp3 |
| OS | Ubuntu 26.04 LTS, x86-64 |
| EC2 security group | devopsflow_server_sg |
| EC2 IAM role | devopsflow-ec2-role |
| ECR repository | devopsflow |
| ECS cluster | devopsflow-cluster |
| ECS service | devopsflow-service |
| Task family / container | devopsflow-task / devopsflow |
| Fargate size | 0.25 vCPU, 0.5 GB |
| Desired tasks | 1 |

The selected instance type and disk are user-provided setup choices. Terminal evidence showed about 7.6 GiB RAM and a 28 GiB root filesystem; an EC2 instance-details screenshot was not supplied to independently verify every launch setting.

This guide describes the rebuilt environment. Earlier builds and the failure-recovery exercise are historical results, not builds of this guide.

## 3. Create the Jenkins server through AWS Console

In EC2, launch Ubuntu with the settings above and the existing dedicated key pair. Choose a public subnet, enable public IPv4, and attach devopsflow-ec2-role. Use IMDSv2.

Initially allow SSH port 22 from My IP. Port 80 was included in the selected configuration, but the project does not use a web server on that port. Add TCP 8080 when Jenkins is ready. The learner chose public HTTP access for this lab; this is not a production access pattern.

Connect with EC2 Instance Connect. Commands below run in that EC2 terminal, not on your personal computer.

## 4. Install Jenkins and Java 21

We checked the [official Jenkins Ubuntu instructions](https://www.jenkins.io/doc/book/installing/linux/). The following uses that repository method. The -y option accepts package installation; it does not change the installed packages.

```bash
sudo apt update
sudo apt install -y ca-certificates wget fontconfig openjdk-21-jre
sudo install -m 0755 -d /etc/apt/keyrings
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key

echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" |
  sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update
sudo apt install -y jenkins
sudo systemctl enable --now jenkins
sudo systemctl is-active jenkins
java -version
```

Open http://YOUR_EC2_PUBLIC_IP:8080. Retrieve the setup password locally:

```bash
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
```

Paste it into Jenkins, install suggested plugins, create an administrator, and finish setup. Never save the password in this document or GitHub.

Observed: Jenkins 2.580.2, Java 21.0.12.1, service active, setup completed. Jenkins URL configuration was skipped by the user. After an EC2 stop/start, update the browser and webhook addresses if the public IP changes; an ordinary reboot normally preserves it.

## 5. Install Docker and allow Jenkins access

Use [Docker's official Ubuntu repository method](https://docs.docker.com/engine/install/ubuntu/):

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo docker run hello-world
```

The vendor package command includes optional plugins. The project pipeline uses ordinary Docker commands; no multi-container orchestration is required.

Using Docker's [group-access instructions](https://docs.docker.com/engine/install/linux-postinstall/), grant the jenkins user access and restart its service:

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
sudo -u jenkins -H docker version
```

Docker group access grants root-equivalent privileges on this dedicated learning server. Observed: Docker 29.9.2, Hello from Docker, and both Client and Server sections under the Jenkins user.

## 6. Install Java 17 and Maven

Java 21 runs Jenkins. Java 17 builds our sample application.

Sources: [Ubuntu OpenJDK installation](https://ubuntu.com/developers/docs/howto/java-setup/) and [Apache Maven installation](https://maven.apache.org/install.html).

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven
```

The following verification block chooses Java 17 only for that shell. It is project configuration, not an extra software installer.

```bash
sudo -u jenkins -H bash -c '
  cd /var/lib/jenkins
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version
  javac -version
  mvn -version
'
```

Observed: Java and javac 17.0.20.1; Maven 3.9.12 using Java 17.

The pipeline runs mvn -B -ntp clean verify. clean removes the earlier generated build files. verify runs Maven's build lifecycle and configured checks. -B and -ntp reduce interactive/progress output. The JAR is target/devopsflow-0.0.1-SNAPSHOT.jar.

## 7. Install AWS CLI and attach the role

The learner installed the Ubuntu package:

```bash
sudo apt update
sudo apt install -y awscli
sudo -u jenkins -H aws --version
```

Observed: AWS CLI 2.31.35. This was the distribution package, not AWS's own installer. AWS's [installation guide](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) notes that AWS does not maintain third-party APT packages. An earlier attempt with the AWS installer stopped because unzip was missing.

In EC2 → instance → Actions → Security → Modify IAM role, attach devopsflow-ec2-role. Verify:

```bash
sudo -u jenkins -H aws sts get-caller-identity --region ap-south-2
```

Observed ARN contained assumed-role/devopsflow-ec2-role. No aws configure or stored access keys were used.

The role's existing permissions allow ECR authentication and image push/pull, ECS service updates, task definition registration/read, task discovery, and passing the execution role to ECS. These permissions were reused from the earlier setup. Their full policies are in Appendix A.

## 8. Recreate ECR in AWS Console

ECR → Private repositories → Create repository:
- Name: devopsflow.
- Tag mutability: Immutable.
- Encryption: AES-256.
- Region: Hyderabad.

Observed screenshot confirmed these settings. Repository URI:
358122644682.dkr.ecr.ap-south-2.amazonaws.com/devopsflow

A repository URI is a storage location. An image URI selects an image, for example:
358122644682.dkr.ecr.ap-south-2.amazonaws.com/devopsflow:build-1

## 9. Connect Jenkins to GitHub and publish the first image

In Jenkins → Manage Jenkins → Credentials → System → Global credentials, add:
- Kind: Username with password.
- Username: RichardL05.
- Password: the repository-restricted GitHub token.
- ID: github-devopsflow.

Do not put the token in a repository URL or this document.

Create a Pipeline job named devopsflow-ci. During the rebuild, a temporary inline pipeline ran only Checkout, Build and Test, Build Docker Image, and Push to ECR. It used the same commands as the full Jenkinsfile in Appendix B, ending before Deploy to ECS Fargate.

This first-image step is necessary because the Fargate task cannot start without an existing image. Do not run the full deployment stage until the service exists.

Observed rebuilt build #1 finished SUCCESS and pushed build-1. If recreating this bootstrap pipeline, copy the full Jenkinsfile and remove only the entire Deploy to ECS Fargate stage before its first run.

## 10. Dockerfile used

```dockerfile
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --chown=10001:10001 target/devopsflow-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

- FROM supplies Java 17 to run the application.
- WORKDIR chooses /app inside the image.
- COPY puts Maven's JAR into that directory as app.jar.
- USER runs it without using root.
- EXPOSE documents the port; security groups still control network access.
- ENTRYPOINT starts the application.

## 11. Create ECS resources in AWS Console

Create cluster devopsflow-cluster with Fargate capacity.

Reuse devopsflow-ecs-execution-role. It trusts ecs-tasks.amazonaws.com and has AmazonECSTaskExecutionRolePolicy. This allows Fargate to download images and write logs; it is separate from Jenkins's role.

Create a task definition:
- Family: devopsflow-task.
- Fargate, Linux/X86_64, awsvpc.
- CPU: 0.25 vCPU; memory: 0.5 GB.
- Application task role: none.
- Execution role: devopsflow-ecs-execution-role.
- Container: devopsflow.
- Image: full ECR URI ending :build-1.
- Essential container: yes; port: 8080 TCP.
- Environment: SERVER_ADDRESS=0.0.0.0.
- Environment: JAVA_TOOL_OPTIONS=-Xms64m -Xmx192m.
- CloudWatch logs: group /ecs/devopsflow, region ap-south-2, stream prefix ecs. Create the log group if needed.

Task definitions are versioned; a saved definition cannot be edited in place. During the rebuild, revision 9 missed the image tag. A new revision 10 corrected it to build-1. The container-level CPU value of 0 shown in its panel is not the task-level CPU size.

Create security group devopsflow-fargate-sg in the Jenkins instance's VPC:
- TCP 8080 from devopsflow_server_sg for automated API checks.
- TCP 8080 from My IP for browser checks.

The actual rebuild screenshot showed 0.0.0.0/0 instead of My IP for browser access, making the sample API public. The narrower source above is the intended restricted setting, not a claim that the screenshot showed it.

Create service inside devopsflow-cluster:
- Name: devopsflow-service.
- Fargate On-Demand, task definition revision 10, desired tasks 1.
- Same VPC; public subnets with an Internet Gateway route.
- Public IP enabled; select devopsflow-fargate-sg only.
- No load balancer.
- Rolling deployment; enable ECS circuit breaker and rollback on failures.

Observed: service Active, 1/1 running, deployment Completed. The initial browser health check returned UP.

## 12. Use the full Jenkinsfile from GitHub

Job → Configure → Pipeline:
- Definition: Pipeline script from SCM.
- SCM: Git.
- URL: https://github.com/RichardL05/devopsflow.git.
- Credential: github-devopsflow.
- Branch: */main.
- Script path: Jenkinsfile.

Save and run once. Rebuilt build #2 deployed revision 11 and passed API checks.

The full script is in Appendix B. Its stages:
1. Checkout downloads main.
2. Build and Test creates the tested JAR.
3. Build Docker Image tags it with the Jenkins build number.
4. Push to Amazon ECR uploads it using temporary role credentials.
5. Deploy to ECS Fargate looks up the image digest, creates a task revision, updates the service, waits for stability, checks the running image, discovers its private IP, and runs API checks.

A digest identifies exact image contents. The pipeline uses it to avoid accidentally deploying a different image. These AWS CLI/Python commands run inside Jenkins; daily operation uses GitHub, Jenkins, and AWS Console.

## 13. Configure the GitHub webhook

In Jenkins job configuration, enable GitHub hook trigger for GITScm polling.

In GitHub → repository Settings → Webhooks, use:
- URL: http://YOUR_JENKINS_PUBLIC_IP:8080/github-webhook/
- Content type: application/json.
- Events: push.
- Active: enabled.

The EC2 security group must allow GitHub to reach 8080. The learner kept public IPv4 access for this lab. A successful delivery proves network reachability; a real push-triggered build proves the full trigger.

Observed: webhook successful. Merging the rebuild README update started rebuilt build #3 with Started by GitHub push by RichardL05. It deployed revision 12, passed API checks, and ended SUCCESS.

## 14. What the application check means

/actuator/health reports application health. The API also supports create, list, view, update, and delete tasks under /api/tasks.

ECS Running means the container started. Jenkins's smoke script additionally checks API behavior, including invalid input and missing tasks. It creates a temporary test task and cleans it up.

For a browser check, open the running ECS task → Networking → Public IP, then:
http://TASK_PUBLIC_IP:8080/actuator/health

The IP can change after deployment. Jenkins discovers the private IP automatically. No stable application URL or load balancer was added.

## 15. Results and troubleshooting

| Evidence | Verified result |
| --- | --- |
| Software terminal screenshots | Jenkins 2.580.2, Java 21 and 17, Maven 3.9.12, Docker 29.9.2, AWS CLI 2.31.35 |
| Rebuilt build #1 | GitHub checkout and ECR image push succeeded |
| Rebuilt build #2 | Full deployment and API checks succeeded |
| Rebuilt build #3 | GitHub-triggered build, revision 12, API PASS, SUCCESS |
| Earlier automated tests | 22 tests passed before the optional extension |
| Earlier controlled recovery test | Invalid image failed; ECS rolled back to revision 5; recovered API passed |

Current repository scope has returned to the core pipeline. The next build after removing the optional extension/history cleanup has not been supplied as verification, so this guide does not invent its result.

| Problem encountered | What resolved it |
| --- | --- |
| Earlier small server had memory pressure | User rebuilt on a larger selected instance; new output showed 7.6 GiB total |
| Docker permission denied for Jenkins | Added jenkins to Docker group and restarted Jenkins |
| Maven used Java 21 by default | Set JAVA_HOME and PATH for Java 17 in the pipeline |
| AWS credentials missing | Attached the EC2 IAM role |
| Image URI lacked build-1 | Created a corrected task definition revision |
| Initial connection failures during startup | Health checks retried until the application responded |
| Earlier Jenkins /tmp disk warning | Earlier environment used disk-based temporary storage; this was not claimed necessary on the rebuilt host |

The recovery test used the AWS CLI outside Jenkins. It proves ECS startup-failure rollback, not rollback after a Jenkins smoke-check failure. The latter marks a build failed but is not an automatic service recovery action.

## 16. Learning scope

Focus on Git, builds, containers, AWS permissions, networking, and automated delivery. The application is supplied sample code.

Temporary storage loses tasks on replacement. Public HTTP and direct task IPs are learning limitations. The project is not presented as production-ready.

This document does not contain passwords, tokens, or private keys. Screenshots supplied in the learning conversation support the results above; image files have not been uploaded into this repository.

## Appendix A. AWS permission policies used

Attach these inline policies to devopsflow-ec2-role. The role's trust relationship is for EC2. These are the project policies, not an instruction to grant administrator access.

### ECR access

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage",
        "ecr:DescribeImages",
        "ecr:DescribeRepositories"
      ],
      "Resource": "arn:aws:ecr:ap-south-2:358122644682:repository/devopsflow"
    }
  ]
}
```

### ECS deployment

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeTaskDefinition",
        "ecs:RegisterTaskDefinition"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeServices",
        "ecs:UpdateService"
      ],
      "Resource": "arn:aws:ecs:ap-south-2:358122644682:service/devopsflow-cluster/devopsflow-service"
    },
    {
      "Effect": "Allow",
      "Action": "iam:PassRole",
      "Resource": "arn:aws:iam::358122644682:role/devopsflow-ecs-execution-role",
      "Condition": {
        "StringEquals": {
          "iam:PassedToService": "ecs-tasks.amazonaws.com"
        }
      }
    }
  ]
}
```

### Task discovery

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ecs:ListTasks",
      "Resource": "*",
      "Condition": {
        "ArnEquals": {
          "ecs:cluster": "arn:aws:ecs:ap-south-2:358122644682:cluster/devopsflow-cluster"
        }
      }
    },
    {
      "Effect": "Allow",
      "Action": "ecs:DescribeTasks",
      "Resource": "arn:aws:ecs:ap-south-2:358122644682:task/devopsflow-cluster/*"
    }
  ]
}
```

The separate devopsflow-ecs-execution-role uses the AWS managed AmazonECSTaskExecutionRolePolicy and trusts ecs-tasks.amazonaws.com.

## Appendix B. Complete working Jenkinsfile

This is the repository pipeline read when this guide was prepared. It contains environment-specific names and paths.

```groovy
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
```

## Appendix C. API verification script

Jenkins calls this after deployment. You do not need to rewrite it for each build.

```python
#!/usr/bin/env python3
"""Exercise a running API using only the Python standard library."""
import json
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main():
    base = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")

    def request(method, path, body=None, expected=200):
        data = None if body is None else json.dumps(body).encode()
        headers = {} if body is None else {"Content-Type": "application/json"}
        try:
            response = urlopen(Request(base + path, data=data, headers=headers, method=method), timeout=15)
        except HTTPError as error:
            response = error
        with response:
            content = response.read().decode()
            if response.status != expected:
                raise RuntimeError(f"{method} {path}: expected {expected}, got {response.status}: {content}")
            return json.loads(content) if content else None

    if request("GET", "/actuator/health")["status"] != "UP":
        raise RuntimeError("Application health is not UP")
    task = request("POST", "/api/tasks", {"title": "Smoke test", "description": "Temporary verification task"}, 201)
    task_id = task["id"]
    try:
        if task["status"] != "TODO":
            raise RuntimeError("New task did not default to TODO")
        request("GET", f"/api/tasks/{task_id}")
        if task_id not in [item["id"] for item in request("GET", "/api/tasks")]:
            raise RuntimeError("Created task missing from list")
        updated = request("PUT", f"/api/tasks/{task_id}", {"title": "Smoke test", "status": "DONE"})
        if updated["status"] != "DONE":
            raise RuntimeError("Update did not change the task status")
        request("POST", "/api/tasks", {"title": "   "}, 400)
        request("POST", "/api/tasks", {"title": "Invalid", "status": "INVALID"}, 400)
    finally:
        request("DELETE", f"/api/tasks/{task_id}", expected=204)
    request("GET", f"/api/tasks/{task_id}", expected=404)
    request("DELETE", f"/api/tasks/{task_id}", expected=404)
    print("PASS: health, create, list, retrieve, update, validation, delete, and not-found responses")


if __name__ == "__main__":
    main()
```
