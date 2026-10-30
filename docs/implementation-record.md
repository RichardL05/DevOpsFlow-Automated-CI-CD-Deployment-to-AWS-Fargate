# Verified implementation record

Date: 2026-10-30. Evidence consists of user-supplied terminal output, Jenkins results, and AWS Console screenshots. The assistant authored application code, pipeline changes, and documentation. The user performed installations, AWS resource setup, merges, and Jenkins runs.

## Application

Java 17, Spring Boot 4.1.2, and Maven implement a to-do REST API. Tasks have generated IDs, titles, optional descriptions, and TODO / IN_PROGRESS / DONE statuses. Storage is temporary and resets on restart or replacement.

Verified: 22 automated tests passed with zero failures, errors, or skips. Runtime health, CRUD, validation, and missing-task checks passed. A full application restart cleared stored tasks.

## Delivery

Jenkins runs on an Ubuntu EC2 instance using Java 21; application builds select Java 17. Docker packages the application with a non-root runtime user. ECR stores images in Hyderabad. ECS Fargate runs the application.

Observed milestones:
- Build #5: tests and JAR creation succeeded.
- Build #6: loaded the pipeline from GitHub.
- Build #7: Docker build succeeded.
- Build #8: image published to ECR.
- Build #9: automatic Fargate deployment succeeded; manual API checks passed.
- Build #10: task revision 3 deployed; automatic private-IP discovery, image digest verification, health and API smoke checks passed.
- Builds #11 and #12: triggered by GitHub pushes and finished SUCCESS.

Verified flow: GitHub push to main → Jenkins → Maven build/tests → Docker → ECR → Fargate → API checks.

## Console-oriented learning

Use AWS Console to inspect ECR images, ECS services/deployments/tasks, networking, and logs. Jenkins executes deployment commands automatically. A direct image upload to ECR does not independently trigger a deployment.

## Failure recovery

The user tested a deliberately missing image in task revision 6 while Jenkins was idle. ECS reported CannotPullContainerError. Its circuit breaker triggered rollback to the previous successful deployment.

Final service output confirmed revision 5, one running task, zero pending, and COMPLETED. The recovered API passed health, CRUD, validation, delete, and not-found checks.

A manual recovery command was suggested but execution was not evidenced. Explicit ECS circuit-breaker messages and rollback events establish automatic recovery.

This verifies image-pull/startup failure recovery. Automatic rollback after a Jenkins API-check failure is not implemented. Continuous availability was not measured.

## Troubleshooting performed

- Moved Jenkins temporary storage from RAM-backed /tmp to /var/lib/jenkins/tmp after disk-space monitoring took its node offline.
- Corrected Jenkins environment syntax and configured Java 17 for Maven builds.
- Granted the Jenkins user Docker access and supplied AWS access through the EC2 role.
- Corrected conflicting Jenkins listen-address settings.
- Retried API checks during container startup.

## Current limitations

- In-memory data is lost when the process is replaced; one desired task is used.
- No database, login, frontend, load balancer, or stable task IP.
- Jenkins is publicly accessible over HTTP on port 8080 by the user's choice.
- Jenkins has Docker-group access and root-equivalent control of its dedicated host.
- API-check failure marks the build failed but does not itself trigger rollback.
- Advanced monitoring, infrastructure as code, extra environments, and cleanup automation are deferred.
- The exact EC2 instance type and account free-tier coverage have not been independently verified.
- The deliberately invalid task revision has not been confirmed deregistered.
- This documentation cleanup has not yet run through Jenkins; no new build result is claimed.
