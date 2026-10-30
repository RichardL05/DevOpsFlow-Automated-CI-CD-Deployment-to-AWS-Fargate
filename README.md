# DevOps Flow — Beginner CI/CD on AWS Fargate

A learning project that automatically delivers a simple Java to-do API to AWS. Use the AWS Console to set up and inspect resources; Jenkins runs the deployment commands for you.

## How it works

**Push to GitHub main → Jenkins → Maven build and tests → Docker image → Amazon ECR → ECS Fargate → automatic API checks**


| Tool | Its job |
| --- | --- |
| GitHub | Stores code and notifies Jenkins about pushes |
| Maven | Builds the Java application and runs tests |
| Jenkins | Runs the steps in Jenkinsfile automatically |
| Docker | Packages the application |
| ECR | Stores versioned images |
| ECS Fargate | Runs the application without managing application servers |
| AWS Console | Lets you view images, deployments, tasks, networking, and logs |

## Start here

Follow the [beginner console guide](docs/beginner-guide.md). Learn the existing stages before adding features.

For a normal change:
1. Edit the code in a feature branch and open a pull request to main.
2. Review and merge the pull request.
3. Watch the automatically triggered Jenkins build.
4. Inspect the new image in ECR and the deployment in ECS Console.
5. Confirm Jenkins reports successful API checks.

Jenkins deploys the image it builds and publishes, using its exact digest. **Uploading an image directly to ECR does not trigger deployment in this setup.** No EventBridge image-upload trigger has been configured.

Every push to main, including documentation changes, currently runs the full pipeline and replaces the application task. Temporary task data is lost when the application process is replaced.

## What is already verified

- Rebuilt environment: Jenkins build #2 loaded the Jenkinsfile from GitHub, deployed `devopsflow-task:11` to Fargate, and passed automatic health and API smoke checks. Rebuilt Jenkins build #3 was triggered by a GitHub push, deployed task revision 12, passed API checks, and finished SUCCESS.
- 22 Java automated tests passed; runtime CRUD, validation, and health checks passed.
- GitHub-triggered Jenkins builds #11 and #12 finished SUCCESS.
- Jenkins built images, published to ECR, deployed to Fargate, and checked the running API automatically.
- A deliberately missing image caused ECS startup failures; ECS automatically rolled back from task revision 6 to revision 5.
- The recovered application passed API smoke checks.

These are observed results, not a guarantee that future builds pass. See the [implementation record](docs/implementation-record.md) for evidence and historical details.

## Current learning environment

- Region: Hyderabad (`ap-south-2`)
- Repository: `RichardL05/devopsflow`; branch: `main`
- Jenkins credential ID: `github-devopsflow`
- ECR repository: `devopsflow`; image tags: `build-<Jenkins build number>`
- ECS cluster: `devopsflow-cluster`
- ECS service: `devopsflow-service`
- Task definition family: `devopsflow-task`; container: `devopsflow`
- Container port: 8080; one desired task
- Java 17 / Spring Boot 4.1.2 application; Jenkins runtime uses Java 21
- Application storage is in memory. No database, login, or frontend.

The Jenkinsfile contains settings for this existing AWS account and environment. Reusing the project in another account requires changing those settings and configuring its resources and permissions first.

## Build locally or on the development host (optional)

Requires Java 17 JDK, Maven, and Git. Run from a checkout of main:

```bash
mvn -B -ntp clean verify
java -jar target/devopsflow-0.0.1-SNAPSHOT.jar --server.port=8081
```

The application binds to localhost by default. Container deployments set `SERVER_ADDRESS=0.0.0.0`. The existing pipeline runs the build and checks automatically, so these manual commands are optional learning exercises.

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


## Scope and limitations

- Use AWS Console for resource setup, status, logs, and troubleshooting. AWS CLI and Python inside Jenkins are automation implementation details; manual CLI use is not the normal learning workflow.
- ECS startup-failure rollback was tested. A failing Jenkins API check marks the build failed but does not automatically roll back the service.
- No load balancer or stable application URL: task public IPs may change after deployment.
- Jenkins uses public HTTP on port 8080, open to all IPv4 addresses by the user's choice; authentication remains required. The API has no authentication. This is a learning setup.
