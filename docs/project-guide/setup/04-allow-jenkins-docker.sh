#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: https://docs.docker.com/engine/install/linux-postinstall/
# Run on the Ubuntu Jenkins EC2 host: bash setup/04-allow-jenkins-docker.sh
set -euo pipefail

# Run between builds: this restarts Jenkins. Docker group access is privileged.
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
sudo -u jenkins -H docker version
