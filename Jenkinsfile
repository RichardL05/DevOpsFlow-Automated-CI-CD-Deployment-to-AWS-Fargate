pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "/usr/lib/jvm/java-17-openjdk-amd64/bin:${env.PATH}"
        MAVEN_OPTS = '-Xmx256m'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-devopsflow',
                    url: 'https://github.com/RichardL05/devopsflow.git'
            }
        }

        stage('Build and Test') {
            steps {
                sh 'mvn -B -ntp clean verify'
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml',
                  allowEmptyResults: true
        }
        success {
            archiveArtifacts artifacts: 'target/devopsflow-0.0.1-SNAPSHOT.jar',
                             fingerprint: true
        }
    }
}
