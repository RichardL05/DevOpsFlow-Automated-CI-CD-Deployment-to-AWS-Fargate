#!/usr/bin/env python3
"""Exercise a running API using only the Python standard library."""
import json
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main():
    base = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080").rstrip("/")

    print(f"Checking API: {base}", flush=True)

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
            print(f"PASS: {method} {path} returned HTTP {response.status}", flush=True)
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
