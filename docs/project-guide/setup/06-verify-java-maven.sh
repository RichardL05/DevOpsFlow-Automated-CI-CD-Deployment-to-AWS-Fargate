#!/usr/bin/env bash
# DevOpsFlow | Use at the matching step in the project document.
# Source: Project configuration: select Java 17 for the Jenkins user shell.
# Run on the Ubuntu Jenkins EC2 host: bash setup/06-verify-java-maven.sh
set -euo pipefail

sudo -u jenkins -H bash -c '
  cd /var/lib/jenkins
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version
  javac -version
  mvn -version
'
