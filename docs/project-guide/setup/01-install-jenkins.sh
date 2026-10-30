#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: https://www.jenkins.io/doc/book/installing/linux/
# Run on the Ubuntu Jenkins EC2 host: bash setup/01-install-jenkins.sh
set -euo pipefail

# git, python3 and curl are additional pipeline prerequisites.
sudo apt update
sudo apt install -y ca-certificates wget fontconfig openjdk-21-jre git python3 curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo wget -O /etc/apt/keyrings/jenkins-keyring.asc \
  https://pkg.jenkins.io/debian-stable/jenkins.io-2026.key

echo "deb [signed-by=/etc/apt/keyrings/jenkins-keyring.asc] https://pkg.jenkins.io/debian-stable binary/" |
  sudo tee /etc/apt/sources.list.d/jenkins.list > /dev/null

sudo apt update
sudo apt install -y jenkins
sudo systemctl enable --now jenkins
sudo systemctl is-active jenkins
java -version
