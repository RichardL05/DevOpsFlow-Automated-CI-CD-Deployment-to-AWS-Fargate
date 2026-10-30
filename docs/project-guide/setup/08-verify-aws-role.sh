#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: https://docs.aws.amazon.com/cli/latest/reference/sts/get-caller-identity.html
# Run on the Ubuntu Jenkins EC2 host: bash setup/08-verify-aws-role.sh
set -euo pipefail

# Attach the EC2 IAM role first. Override AWS_REGION for another region.
sudo -u jenkins -H aws sts get-caller-identity --region "${AWS_REGION:-ap-south-2}"
