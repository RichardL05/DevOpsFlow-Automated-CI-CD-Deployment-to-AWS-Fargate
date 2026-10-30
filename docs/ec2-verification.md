# Verify the Task Management API on EC2

This is a procedure to run, not a claim that these checks have passed.

## Build

Use an existing application checkout, preserving any uncommitted changes. This optional manual exercise is separate from the automatic Fargate pipeline. Verify `java -version`, `javac -version`, and `mvn -version` report Java 17, then run:

```bash
mvn -B clean verify
```

Record the actual commit (`git rev-parse HEAD`), test summary, and Maven result. A successful earlier starter build does not verify this branch. On failure, stop and collect the first error and the relevant report from `target/surefire-reports/`; do not skip tests to obtain a green build.

## Runtime checks

Start the jar on localhost port 8081, keeping the prior port-8080 starter separate:

```bash
java -jar target/devopsflow-0.0.1-SNAPSHOT.jar --server.port=8081
```

In a second EC2 terminal, from the same checkout:

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
python3 scripts/smoke_test.py http://127.0.0.1:8081
```

Record the real response and smoke result. No security-group port changes are needed for these localhost checks.

## Verify temporary storage after restart

1. Create a task using POST /api/tasks and confirm it is returned by GET /api/tasks.
2. Stop the application with Ctrl+C and wait for its shutdown.
3. Start the same jar again on port 8081.
4. Confirm GET /api/tasks returns `[]` before creating any new tasks.

Record the actual responses. This is the explicit restart check; the tests' fresh-repository check does not substitute for observing it.

## Completion

Review the PR and merge only after the new implementation's build, tests, smoke check, and restart behaviour are verified. Update this project's evidence record with the actual results. Never paste PATs or SSH private keys into logs or GitHub.
