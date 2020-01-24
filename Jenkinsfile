pipeline {
    agent any
    stages {
        stage('Delete old') {
            steps {
                script {
                    def job = Jenkins.instance.getItemByFullName(env.JOB_NAME)
                    for (build in job.builds) {
                        if (!build.isBuilding()) {
                            continue;
                        }

                        if (env.BUILD_NUMBER.toInteger() == build.getNumber().toInteger()) {
                            continue
                        }

                        println "Killing task = ${build}"
                        def cause = { "interrupted by build #${build.getId()}" as String } as CauseOfInterruption
                        build.getExecutor().interrupt(Result.ABORTED, cause)
                    }
                }
            }
        }
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