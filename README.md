# DevOps Flow

A hands-on learning and portfolio project: build a Java Task Management REST API and deliver it through a Jira-integrated CI/CD pipeline on AWS.

## Current status

Repository setup is in progress under [DEVOPS-13](https://richardrd8682.atlassian.net/browse/DEVOPS-13), within [DEVOPS-7](https://richardrd8682.atlassian.net/browse/DEVOPS-7).

Verified:
- Jira space: **DevOps Flow**, key **DEVOPS**, with a board and backlog.
- Initial requirements and architecture planning completed in DEVOPS-5.
- Six implementation epics are recorded in Jira.
- This private GitHub repository exists with a `main` branch and Maven `.gitignore`.

The application, Jenkins pipeline, ECR repository, EC2 hosting, and Jira delivery integrations have **not** been implemented or verified for this project.

## Agreed application scope

Use Java 17, Spring Boot, and Maven to build a small to-do list backend:
- Create, list, retrieve, update, and delete tasks.
- Provide an application health endpoint.
- Task fields: generated ID, required nonblank title, optional description, and status.
- Allowed statuses: `TODO`, `IN_PROGRESS`, `DONE`; new tasks default to `TODO`.
- Return an appropriate error for invalid input or a nonexistent task.
- Use temporary in-memory storage: tasks disappear when the application restarts.

Persistent storage, login, and a web interface are deferred.

## Planned delivery flow

```text
Jira requirement
    -> GitHub feature branch and pull request
    -> merge into main
    -> GitHub webhook triggers Jenkins
    -> Maven build and automated tests
    -> Docker image
    -> Amazon ECR
    -> Docker container on AWS EC2
    -> deployed application health check
```

A build or test failure must stop subsequent delivery stages. Jenkins executes delivery; Jira tracks the work and will receive development/delivery information through supported integrations configured later. AWS CodeBuild is not a dependency.

## Branching approach

- `main`: reviewed project baseline.
- `feature/DEVOPS-<number>-short-description`: application or pipeline changes.
- `bugfix/DEVOPS-<number>-short-description`: defect fixes.
- `docs/DEVOPS-<number>-short-description`: documentation changes.

Start a branch from `main`, make a focused change, open a pull request, review its evidence, and merge only after the task's acceptance criteria are met. Include the Jira key in branch names, commit messages, and pull request titles.

The repository's initial README and ignore rules were bootstrapped on `main`. This documentation is the first example of the proposed branch-and-pull-request workflow.

These are working conventions, **not enforced branch protection**. Automated checks and GitHub-to-Jira linking are not yet configured.

## Implementation milestones

| Jira epic | Completion goal |
| --- | --- |
| DEVOPS-7 | Repository setup and verified GitHub/Jira traceability |
| DEVOPS-8 | Working, tested Task Management API |
| DEVOPS-9 | Webhook-triggered Jenkins builds and tests |
| DEVOPS-10 | Tested Docker image and verified ECR push |
| DEVOPS-11 | Verified EC2 deployment and basic failure handling |
| DEVOPS-12 | Verified Jira build and deployment reporting |

Monitoring with Prometheus/Grafana, logging, Terraform, and other advanced enhancements are deferred until the foundational pipeline works.

Before provisioning AWS resources, review current pricing and eligibility, potential charges, IAM permissions, and network access; obtain approval. Do not assume credits cover all charges.

## Evidence and documentation

See [the implementation record](docs/implementation-record.md). Record only observed work, distinguish planned features from verified results, and identify whether the user or an assistant performed an action. This is a learning project, not professional employment experience.
