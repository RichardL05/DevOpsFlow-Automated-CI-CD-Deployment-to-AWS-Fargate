# Prometheus and Grafana monitoring

Status: configuration prepared; live monitoring is not yet verified.

## Official sources

- [Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus Docker installation](https://prometheus.io/docs/prometheus/latest/installation/)
- [Prometheus file discovery](https://prometheus.io/docs/guides/file-sd/)
- [Grafana Docker installation](https://grafana.com/docs/grafana/latest/setup-grafana/installation/docker/)
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)

The project combines these documented mechanisms into Compose files, a host-side discovery script, and a systemd timer. These project scripts are not verbatim vendor installation commands. Images are pinned to Prometheus 3.15.1 and Grafana OSS 13.3.0, checked against official release pages on 2026-10-30.

## Architecture

Fargate API /actuator/prometheus → Prometheus on Jenkins EC2 → Grafana dashboard.

A host-side timer runs as jenkins every 30 seconds. It uses the existing EC2 role and AWS CLI to discover running service task private IPs and atomically updates Prometheus file discovery. No AWS keys are stored or passed to monitoring containers. Existing ECS ListTasks and DescribeTasks permissions suffice.

Scrapes occur every 15 seconds. Discovery follows deployments without manually changing IPs. A deployment can briefly show old/new targets. Discovery errors retain the last target file and appear in systemd logs.

## Before installation

Merge this change and observe a successful Jenkins deployment. The new Java integration test must pass; expect one additional test beyond the earlier 22.

Check free -h and df -h / on EC2. Allow approximately 1.3 GiB for configured container memory limits plus spare memory for Jenkins builds. Prometheus keeps seven days of metrics with a 2 GB block-retention target; WAL/head data can add disk usage. These are limits, not measured usage.

Metrics share application port 8080. Restrict Fargate inbound 8080 to the Jenkins EC2 security group and your current IP; the previous all-IPv4 rule also exposes metrics publicly. Allow Grafana port 3000 on the EC2 security group from My IP only. Leave 9090 closed externally; Prometheus binds to host localhost.

## Install on the existing EC2 server

The successful Jenkins checkout contains this directory:

```bash
free -h
df -h /
sudo bash /var/lib/jenkins/workspace/devopsflow-ci/monitoring/install.sh
```

The installer copies configuration to /opt/devopsflow-monitoring, creates persistent Docker volumes, provisions a datasource/dashboard, generates a Grafana admin password in a root-readable .env, and starts the discovery timer. It validates Prometheus configuration using the image's promtool before starting services. It does not change AWS resources or IAM.

Do not edit the Jenkins workspace while a build is running. The installed copy remains independent of later Jenkins clean builds.

Retrieve the password locally, never in chat or screenshots:
```bash
sudo cat /opt/devopsflow-monitoring/.env
```

Visit http://YOUR_EC2_PUBLIC_IP:3000, user admin. Open Dashboards → DevOps Flow → DevOps Flow — Fargate API. The datasource is preconfigured.

## Verification

```bash
sudo systemctl status devopsflow-discovery.timer --no-pager
sudo journalctl -u devopsflow-discovery.service -n 20 --no-pager
curl -fsS http://127.0.0.1:9090/-/ready
curl -fsSG http://127.0.0.1:9090/api/v1/query --data-urlencode 'query=up{job="devopsflow"}'
curl -fsS http://127.0.0.1:3000/api/health
```

Require at least one application target with up=1; Prometheus being ready alone is not success. In Grafana, verify JVM heap and CPU panels have data. Generate API requests by opening the task's /api/tasks address a few times; request-rate/latency panels need traffic and multiple scrapes. An empty 5xx series can mean no server errors have occurred; it is not evidence of a failed datasource.

Take screenshots of targets/query results and the populated dashboard, excluding credentials. After the next normal deployment, verify the target changes and up returns to 1. Only then record live monitoring/discovery as verified.

## Troubleshooting and stop

If scraping fails, check /actuator/prometheus on the task, discovery logs, and port 8080 from the EC2 security group. If no targets appear, inspect IAM errors in discovery logs. If Grafana shows no data, check Prometheus query results first.

```bash
sudo docker compose -f /opt/devopsflow-monitoring/compose.yaml --env-file /opt/devopsflow-monitoring/.env logs --tail 50
sudo systemctl disable --now devopsflow-discovery.timer
sudo docker compose -f /opt/devopsflow-monitoring/compose.yaml --env-file /opt/devopsflow-monitoring/.env down
```

Stopping preserves named volumes; no volume deletion is performed. This setup monitors the Java application and Prometheus itself, not host-level EC2 metrics. Alert routing and high availability are outside this extension.
