#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: https://ubuntu.com/developers/docs/howto/java-setup/ and https://maven.apache.org/install.html
# Run on the Ubuntu Jenkins EC2 host: bash setup/05-install-java-maven.sh
set -euo pipefail

sudo apt update
sudo apt install -y openjdk-17-jdk maven
