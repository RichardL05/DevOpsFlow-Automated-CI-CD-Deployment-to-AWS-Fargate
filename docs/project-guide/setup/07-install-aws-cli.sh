#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html
# Run on the Ubuntu Jenkins EC2 host: bash setup/07-install-aws-cli.sh
set -euo pipefail

# Reproduces the Ubuntu APT method used in this project, not the AWS vendor installer.
# AWS does not maintain this third-party package. Check that aws --version reports v2.
sudo apt update
sudo apt install -y awscli
sudo -u jenkins -H aws --version
