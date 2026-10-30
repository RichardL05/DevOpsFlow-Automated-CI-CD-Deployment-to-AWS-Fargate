#!/usr/bin/env python3
"""Host-side ECS discovery: uses the EC2 role, not stored AWS keys."""
import json
import os
import subprocess
import tempfile
from pathlib import Path

TARGET = Path("/var/lib/devopsflow-monitoring/targets/fargate.json")

def aws(*args):
    result = subprocess.run(
        ["aws", "--region", "ap-south-2", *args, "--output", "json", "--no-cli-pager"],
        check=True, capture_output=True, text=True, timeout=45)
    return json.loads(result.stdout)

def main():
    arns = aws("ecs", "list-tasks", "--cluster", "devopsflow-cluster",
               "--service-name", "devopsflow-service",
               "--desired-status", "RUNNING")["taskArns"]
    groups = []
    if arns:
        response = aws("ecs", "describe-tasks", "--cluster", "devopsflow-cluster",
                       "--tasks", *arns)
        if response.get("failures"):
            raise RuntimeError("ECS describe-tasks reported failures")
        for task in response["tasks"]:
            if task["lastStatus"] != "RUNNING":
                continue
            for container in task["containers"]:
                if container["name"] != "devopsflow":
                    continue
                for network in container.get("networkInterfaces", []):
                    address = network.get("privateIpv4Address")
                    if address:
                        groups.append({"targets": [address + ":8080"],
                                       "labels": {"service": "devopsflow",
                                                  "task": task["taskArn"].rsplit("/", 1)[-1]}})
    # Atomic replacement avoids partially written discovery JSON.
    # Errors leave the last good file intact; check timer logs for failures.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", dir=TARGET.parent,
                                         delete=False, encoding="utf-8") as output:
            temporary = output.name
            json.dump(groups, output)
        os.chmod(temporary, 0o644)
        os.replace(temporary, TARGET)
        temporary = None
    finally:
        if temporary:
            os.unlink(temporary)
    print("Updated discovery:", len(groups), "running container targets", flush=True)

if __name__ == "__main__":
    main()
