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
        stage('Build') {
            steps {
                script {
                    enhancedTasks.timedGradleTask("clean deployNodes")
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