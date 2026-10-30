#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: https://www.jenkins.io/doc/book/installing/linux/#unlocking-jenkins
# Run on the Ubuntu Jenkins EC2 host: bash setup/02-unlock-jenkins.sh
set -euo pipefail

# Shows a secret locally. Do not save or share the output.
set +x
sudo cat /var/lib/jenkins/secrets/initialAdminPassword
