pipeline {
    agent any
    stages {
        stage('Git Checkout') {
            steps {
                gitCheckout([
                        branch: "master",
                        url: "https://github.com/dgounaris/cordapp-wallets-example"
                ])
            }
        }
        stage('Test with coverage') {
            steps {
                script {
                    enhancedTasks.timedGradleTask("clean jacocoRootReport")
                }
            }
        }
        stage('Analysis') {
            steps {
                jacoco(
                    execPattern: '**/build/jacoco/*.exec',
                    classPattern: '**/build/classes',
                    sourcePattern: '**/src/main/kotlin',
                    sourceInclusionPattern: '**/*.kt',
                    exclusionPattern: '**/src/test'
                )
                withSonarQubeEnv('local sq') {
                    script {
                        enhancedTasks.timedGradleTask("sonarqube")
                    }
                }
            }
        }
        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    script {
                        try {
                            def qg = waitForQualityGate();
                            if (qg.status != 'OK') {
                                error "Pipeline aborted due to quality gate failure: ${qg.status}"
                            }
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            println('No sonarqube webhook response within timeout. Please check the webhook configuration in sonarqube.')
                            // continue the pipeline
                        }
                    }
                }
            }
        }
        stage('Build') {
            steps {
                script {
                    enhancedTasks.timedGradleTask("deployNodes")
                }
            }
        }
    }
    post {
        always {
            sh 'echo "Pipeline finished. Reporting..."'
        }
    }
}