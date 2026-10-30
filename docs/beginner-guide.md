# Beginner guide: follow one change from GitHub to Fargate

## What you are learning


## Your normal workflow

1. In GitHub, create a feature branch, edit a file, and open a pull request to main.
2. Review the change, then merge. A push to main notifies Jenkins through the configured webhook.
3. In Jenkins, open devopsflow-ci and the new build. A webhook build should say it was started by a GitHub push.
4. Watch the stages below. Do not repeatedly press Build Now while a build is queued.
5. In AWS Console, inspect ECR and ECS to see the result.

| Stage in Jenkins | Meaning | What to look for |
| --- | --- | --- |
| Checkout | Download application source from main | No GitHub authentication error |
| Build and Test | Maven compiles Java and runs tests | Successful tests and JAR creation |
| Build Docker Image | Package the JAR | Image tagged build-N |
| Push to Amazon ECR | Upload the image | Tag and image digest printed |
| Deploy to ECS Fargate | Register revision, deploy, and verify API | Requested revision stable, digest matches, API checks PASS |

Jenkins runs the commands inside these stages. You normally watch them rather than copy commands into a terminal.

## View the image in AWS Console

Select Hyderabad (ap-south-2). Open Amazon ECR → Private repositories → devopsflow → Images.

Find the image tagged build-N for your Jenkins build. A tag is a readable version label; the digest identifies the exact image contents. Jenkins deploys by digest so it can check that the running image is the one it published.

Pushing a different image manually into ECR does not launch this pipeline. The automatic trigger is a GitHub push, followed by a Jenkins build, image push, and ECS service update.

## View the deployment in AWS Console

Open ECS → Clusters → devopsflow-cluster → Services → devopsflow-service.

- Deployments shows progress and recent failures.
- Tasks shows the running container task.
- Task definition identifies the version of the container configuration.
- Logs shows application output when CloudWatch logging is configured.
- Events explains scheduling, image-pull failures, and deployment completion.

Wait for a completed deployment and the desired one running task. ECS Running alone does not prove the API works; Jenkins also runs health and CRUD checks.

## Open the API in your browser

Open the running task → Networking and copy its current public IPv4 address.

Visit `http://<current-task-public-ip>:8080/actuator/health`. A healthy application returns status UP. To view tasks, visit `http://<current-task-public-ip>:8080/api/tasks`.

Use the current task IP after deployment; an old IP may stop working. Browser access needs the Fargate security group to allow port 8080 from your current public IP. Jenkins checks over the private IP and needs port 8080 allowed from the Jenkins EC2 security group.

A browser address bar sends a GET request. Jenkins's smoke script sends create, update, delete, and invalid-input requests as well.

## First practice exercise


Even documentation changes currently redeploy the application. All stored tasks are temporary and disappear when the task is replaced.

## When something fails

| Symptom | Where to look first |
| --- | --- |
| No Jenkins build after merging | GitHub repository Settings → Webhooks → Recent Deliveries; then Jenkins trigger configuration |
| Build queued indefinitely | Jenkins Manage Jenkins → Nodes; inspect offline reason or busy executor |
| Java test failure | Jenkins Build and Test console output and test results |
| ECR permission error | Jenkins console and IAM permissions on devopsflow-ec2-role |
| ECS task stopped | ECS task Stopped reason, service Events, and application Logs |
| Browser cannot connect | Current task IP and security-group rules |
| ECS stable but API checks failed | Jenkins API check error, private network access, and application Logs |

A missing or broken image can trigger ECS circuit-breaker rollback to a successful deployment. This was tested. API smoke-check failure in Jenkins does not itself trigger rollback. Do not repeat deliberate failure exercises during the beginner walkthrough.

## Existing setup and boundaries

The resources already exist. Use the console to inspect them; do not recreate them for every build. Jenkins stays on EC2 and the application stays on Fargate.


Jenkins currently has public HTTP access on port 8080 by the user's choice. Never put passwords, tokens, or private keys into commits, screenshots, or learning notes.

This guide documents the established workflow. A fresh run after these documentation edits must still be observed; it is not recorded as completed in advance.
