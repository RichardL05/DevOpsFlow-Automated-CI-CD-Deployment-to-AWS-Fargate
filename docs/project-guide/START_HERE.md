# DevOpsFlow — Automated CI/CD Deployment to AWS Fargate

Start with the PDF in this folder. Its step numbers match the files below.

## Folder layout

- setup/: the eight installation and verification shell scripts, steps 4–7.
- policies/: five JSON templates for AWS IAM, steps 7 and 11.
- Jenkinsfile.bootstrap: first-image pipeline, step 9.
- Dockerfile: image recipe; put in your application repository root, step 10.
- Jenkinsfile: full pipeline; put in your application repository root, step 12.
- scripts/smoke_test.py: API verification; keep this relative path, step 14.
- templates/: alternative pipeline copies with account, region, and GitHub URL placeholders.

The root pipeline files retain the project's known account, region, repository, credential ID, Java path and resource names. For another environment use templates/ and replace YOUR_ACCOUNT_ID, YOUR_REGION, YOUR_GITHUB_USER, and YOUR_REPOSITORY before copying a pipeline to the repository root. Review retained resource names too. The JSON policies also need YOUR_ACCOUNT_ID and YOUR_REGION replaced where present. Trust policies contain service principals and need no account placeholder.

## Run the setup files

Transfer the setup folder to the Ubuntu EC2 host. From the folder containing setup, run one file at its matching document step, for example:

    bash setup/01-install-jenkins.sh

Run 01 and 02 at step 4, complete Jenkins web setup, then 03 and 04 at step 5, 05 and 06 at step 6, and 07 and 08 at step 7. Attach the IAM role before 08. Scripts use sudo when needed. They are Bash files with Linux line endings; do not run them in Windows PowerShell. No chmod is required when invoking bash explicitly. set -euo pipefail stops on errors or unset variables rather than continuing a partial installation.

Do not run every installation file again on an already configured server just to verify this pack. File 04 restarts Jenkins, so run it only between builds. File 02 displays the initial setup password locally. Never commit that password, tokens, or private keys.

## Use the policy files

In AWS IAM, use ec2-trust.json as the Jenkins EC2 role's trust relationship. Add ecr-push-pull.json, ecs-deployment.json and ecs-task-discovery.json as inline permission policies on that role. Attach the role to EC2. Use ecs-execution-trust.json for the Fargate execution role, then attach the AWS-managed AmazonECSTaskExecutionRolePolicy. See document steps 7 and 11. JSON files are not executable scripts.

## Bootstrap and full pipeline

Use Jenkinsfile.bootstrap as an inline Pipeline script until the first ECR image exists. Create the ECS service in the console using that image. Then switch the same job to Pipeline script from SCM, path Jenkinsfile. Keep the job's build counter advancing: immutable ECR tags cannot be overwritten. Jenkins obtains AWS credentials from the EC2 role and GitHub access from the saved github-devopsflow credential.

The pack contains delivery files, not the complete Java application. Use them with the existing devopsflow application repository; pom.xml, source, tests, and .dockerignore remain in that repository. Copy Dockerfile and scripts/smoke_test.py into their documented paths before running the pipeline.

## Verify the deployed API

From a checkout on the Jenkins EC2 host, run:

    python3 scripts/smoke_test.py http://TASK_PRIVATE_IP:8080

Replace TASK_PRIVATE_IP with the current running task address. Jenkins discovers it automatically in the full pipeline. This creates and deletes a temporary test task. It does not prove uptime or load capacity. Task data is temporary and is cleared when a container is replaced.

## Installation sources and project adaptations

- Jenkins LTS and Java 21: https://www.jenkins.io/doc/book/installing/linux/
- Docker Ubuntu repository: https://docs.docker.com/engine/install/ubuntu/
- Jenkins user's Docker group access: https://docs.docker.com/engine/install/linux-postinstall/
- Java: https://ubuntu.com/developers/docs/howto/java-setup/
- Maven: https://maven.apache.org/install.html
- AWS CLI vendor guidance: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html

The scripts package these installation steps with project checks and comments. They are not verbatim vendor scripts. Jenkins setup additionally installs Git, Python 3, and curl because the pipeline needs them. Java/Maven and AWS CLI use Ubuntu packages as in the recorded setup. AWS does not maintain the APT AWS CLI package. Versions available on a later run can differ from the historical screenshots. There is no Docker Compose or monitoring setup in this pack.

## Verification boundary

See VALIDATION.txt for local file checks. The original root pipeline, Dockerfile, and API checker match the previously documented baseline. The newly packaged setup scripts and reusable templates have not been rerun on EC2. A fresh Jenkins build after repository cleanup still needs to be run and recorded with EC2 and Fargate active. Preparing these files did not change AWS infrastructure. The latest live pipeline run remains pending.
