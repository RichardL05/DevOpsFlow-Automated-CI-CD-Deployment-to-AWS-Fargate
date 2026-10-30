# DevOps Flow

A hands-on learning and portfolio project: a Java Task Management REST API with a planned Jira-integrated delivery pipeline on AWS.

## Current status

- Jira space **DevOps Flow**, key **DEVOPS**, board, backlog, and implementation epics exist.
- Requirements planning in DEVOPS-5 and repository setup in DEVOPS-13 are complete.
- Documentation PR #1 was merged into `main`.
- GitHub for Atlassian is connected with only `RichardL05/devopsflow` selected for repository permissions. Jira's DEVOPS-13 Development panel was observed showing 2 branches, 3 commits, and 1 merged pull request.
- The user created an Ubuntu 26.04.1 LTS x86-64 EC2 development machine. Screenshots verified OpenJDK and javac 17.0.20.1, Maven 3.9.12 using Java 17, and Git 2.53.0.
- The user's initial EC2 starter build returned `BUILD SUCCESS` and its health endpoint returned `UP`.
- **This branch adds the complete CRUD implementation and tests. Its Java build and tests still require execution on EC2.** The earlier starter result is not evidence that this implementation passes.
- Jenkins, Docker packaging, ECR, automated deployment, and Jira build/deployment reporting are pending. A development EC2 host is not an automated deployment pipeline.

## Requirements

- Java **17 JDK**, Maven **3.6.3+**, and Git.
- Spring Boot **4.1.2**, pinned in `pom.xml`.
- Python 3 only if running the optional smoke-test script.

## Build and run on EC2

To preserve the existing starter checkout, clone this feature branch into a separate directory:

```bash
mkdir -p ~/projects
cd ~/projects
git clone --branch feature/DEVOPS-8-task-api https://github.com/RichardL05/devopsflow.git devopsflow-api
cd devopsflow-api
mvn -B clean verify
```

If `devopsflow-api` already exists, inspect it first instead of overwriting it. Run the build only after cloning succeeds. For private-repository HTTPS access, enter the GitHub username and fine-grained token at Git's prompts. Do not embed the token in commands or the URL. No Maven Wrapper is required; use the installed `mvn`.

After `BUILD SUCCESS`, start the executable jar in the foreground:

```bash
java -jar target/devopsflow-0.0.1-SNAPSHOT.jar --server.port=8081
```

Port **8081** avoids the earlier starter still running on 8080. In another EC2 terminal:

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
python3 scripts/smoke_test.py http://127.0.0.1:8081
```

The health response must contain `"status":"UP"`. The smoke script prints `PASS` only after its HTTP checks succeed, creates a temporary task, and deletes that task. These are expected outcomes, not recorded test results.

The application binds to **127.0.0.1 by default** and requires no public inbound application port for these checks. Stop a foreground application using Ctrl+C. For a future container deployment, set `SERVER_ADDRESS=0.0.0.0` inside the container and separately review the host's network exposure. This learning API has no authentication.

## API contract

| Method | Path | Success | Behaviour |
| --- | --- | --- | --- |
| POST | `/api/tasks` | 201 + Location header | Create a task; generated ID; default status TODO |
| GET | `/api/tasks` | 200 | List tasks sorted by ID; empty array when none exist |
| GET | `/api/tasks/{id}` | 200 | Retrieve a task |
| PUT | `/api/tasks/{id}` | 200 | Replace title, description, and status; preserve ID |
| DELETE | `/api/tasks/{id}` | 204 | Delete a task; no response body |
| GET | `/actuator/health` | 200 when healthy | Application health without internal details |

- `title` is required and must contain a non-whitespace character; surrounding whitespace is stripped.
- `description` is optional and may be null. Omitting it in a PUT clears the description.
- Allowed status strings are exactly `TODO`, `IN_PROGRESS`, and `DONE`.
- POST defaults an omitted or null status to `TODO`; an explicit valid status is accepted.
- PUT requires both a nonblank title and a non-null valid status. PATCH is not implemented.
- Invalid JSON, invalid statuses (including numeric values), invalid IDs, and validation errors return 400.
- Retrieve, update, and delete of a missing task return 404 with `Task not found` details.
- Errors use Spring's RFC 9457 Problem Details (`application/problem+json`). Validation failures include an `errors` field map.
- Health is the only exposed Actuator endpoint. Login, database persistence, pagination, and a web interface are deferred.

Examples (run inside EC2 while the application is running):

```bash
curl -i -X POST http://127.0.0.1:8081/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Learn Docker","description":"Build my first image"}'

curl -i http://127.0.0.1:8081/api/tasks
```

Use the ID from the creation response for GET, PUT, and DELETE; do not assume a fixed ID.

## Application structure and storage

```text
HTTP request -> TaskController -> TaskService -> InMemoryTaskRepository
                                     |
                          immutable Task records

Validation/error handling: CreateTaskRequest, UpdateTaskRequest, ApiExceptionHandler
Health: Spring Boot Actuator
```

The controller translates HTTP requests, the service applies task behaviour, and the repository keeps tasks in a concurrent map. Atomic ID generation avoids duplicate IDs during concurrent creation. Updating an existing task uses an atomic map operation so it cannot recreate an already deleted task.

Storage is deliberately temporary: **restarting the application loses every task and resets the ID sequence**. Each application process has its own independent list. Do not use this implementation as persistent production storage.

## Tests

Run `mvn -B clean verify`. Maven's normal test phase includes:

- `TaskServiceTest`: defaults, generated IDs, updates, deletion, missing-task handling, fresh-repository behaviour, and concurrent creation.
- `TaskApiIntegrationTest`: starts the actual application on a random localhost port and checks the complete HTTP CRUD lifecycle, Location header, ordering, allowed statuses, bad input, invalid-update atomicity, optional descriptions, missing tasks, and restricted health exposure.

The random test port avoids conflicting with the manually started application. Tests remain **unexecuted for this new branch until an actual result is recorded**. A unit test with a fresh repository is distinct from a verified full application restart.

## Git and Jira workflow

- `main`: reviewed baseline. The application change is awaiting build evidence and pull request review.
- `feature/DEVOPS-<number>-description`: application/pipeline changes.
- `bugfix/DEVOPS-<number>-description`: fixes.
- `docs/DEVOPS-<number>-description`: documentation.

Include the Jira key in branches, commits, and pull request titles. This application branch references **DEVOPS-8**, the existing application epic. GitHub-to-Jira development linking has been verified; enforced branch protection and automated checks have not been configured. Do not mark the epic Done solely because code has been generated or pushed.

## Planned delivery milestones

| Epic | Completion goal |
| --- | --- |
| DEVOPS-7 | GitHub repository and Jira traceability |
| DEVOPS-8 | Verified Task Management API |
| DEVOPS-9 | GitHub webhook -> Jenkins checkout -> Maven build and tests |
| DEVOPS-10 | Docker image build, container test, and verified ECR push |
| DEVOPS-11 | Verified container deployment on approved EC2 infrastructure |
| DEVOPS-12 | Verified Jira build and deployment reporting |

Later delivery stages stop on build/test failure and include a deployed health check. Jenkins is the planned automation engine; AWS CodeBuild is not a dependency. Prometheus/Grafana, Terraform, additional environments, and advanced security/rollback work are deferred.

See [the implementation record](docs/implementation-record.md) for observed work and pending verification. This is a learning project, not professional employment experience.
