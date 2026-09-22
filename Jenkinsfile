pipeline {
    agent any

    parameters {
        choice(
            name: 'ACTION',
            choices: ['DEPLOY', 'ROLLBACK'],
            description: 'Deployment action'
        )

        string(
            name: 'VERSION',
            defaultValue: '7.9.0',
            description: 'Immutable image version'
        )

        choice(
            name: 'RUN_TESTS',
            choices: ['YES', 'NO'],
            description: 'Run Maven tests'
        )

        string(
            name: 'ROLLBACK_VERSION',
            defaultValue: '7.8.0',
            description: 'Rollback image version'
        )

        booleanParam(
            name: 'PRODUCTION_CONFIRMATION',
            defaultValue: false,
            description: 'Required for production'
        )
    }

    environment {
        NETWORK = 'orders-network'
        DB_CONTAINER = 'orders-db'
        PROXY = 'orders-proxy'
        BLUE = 'orders-blue'
        GREEN = 'orders-green'
        BLUE_PORT = '8081'
        GREEN_PORT = '8082'
        TRAFFIC_PORT = '8080'
        DB_NAME = 'ordersdb'
        DB_USER = 'orders'
        DB_PASSWORD = credentials('orders_db_password')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                bat 'git rev-parse HEAD'
                bat 'git branch --show-current'
            }
        }

        stage('Validate Version') {
            steps {
                script {
                    if (!(params.VERSION ==~ /[0-9]+\.[0-9]+\.[0-9]+/)) {
                        error('VERSION must be x.y.z')
                    }

                    if (!params.PRODUCTION_CONFIRMATION) {
                        error('Production confirmation is required')
                    }

                    echo "ACTION=${params.ACTION}"
                    echo "VERSION=${params.VERSION}"
                    echo "BLUE=${env.BLUE}:${env.BLUE_PORT}"
                    echo "GREEN=${env.GREEN}:${env.GREEN_PORT}"
                    echo "TRAFFIC=${env.TRAFFIC_PORT}"
                    echo "NETWORK=${env.NETWORK}"
                    echo "DB=${env.DB_CONTAINER}"
                }
            }
        }

        stage('Unit/Application Test') {
            when {
                expression {
                    params.ACTION == 'DEPLOY' && params.RUN_TESTS == 'YES'
                }
            }

            steps {
                bat 'mvn clean test'
            }
        }

        stage('Docker Build') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                bat 'mvn -DskipTests package'
                bat 'docker build --build-arg APP_VERSION=%VERSION% -t orders-api:%VERSION% .'
            }
        }

        stage('Docker Image Validation') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                bat 'docker image inspect orders-api:%VERSION%'
            }
        }

        stage('Prepare Infrastructure') {
            steps {
                bat 'docker network inspect %NETWORK% >nul 2>&1 || docker network create %NETWORK%'

                bat 'docker start %DB_CONTAINER% >nul 2>&1 || docker run -d --name %DB_CONTAINER% --network %NETWORK% -e POSTGRES_DB=%DB_NAME% -e POSTGRES_USER=%DB_USER% -e POSTGRES_PASSWORD=%DB_PASSWORD% -v orders-db-data:/var/lib/postgresql/data postgres:16'

                bat 'docker start %PROXY% >nul 2>&1 || docker run -d --name %PROXY% --network %NETWORK% -p %TRAFFIC_PORT%:80 -v "%WORKSPACE%\\nginx\\default.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27-alpine'
            }
        }

        stage('Start Candidate') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                script {
                    env.CANDIDATE = 'orders-green'
                    env.CANDIDATE_PORT = '8082'
                    env.CURRENT = 'orders-blue'

                    if (bat(
                        script: 'docker inspect orders-blue >nul 2>&1',
                        returnStatus: true
                    ) != 0) {
                        env.CANDIDATE = 'orders-blue'
                        env.CANDIDATE_PORT = '8081'
                        env.CURRENT = 'orders-green'
                    }

                    echo "BLUE/GREEN current=${env.CURRENT}; candidate=${env.CANDIDATE}:${env.CANDIDATE_PORT}"
                }

                bat 'docker rm -f %CANDIDATE% >nul 2>&1 || exit /b 0'

                bat 'docker run -d --name %CANDIDATE% --network %NETWORK% -p %CANDIDATE_PORT%:8080 -e APP_ENV=PRODUCTION -e APP_VERSION=%VERSION% -e SPRING_DATASOURCE_URL=jdbc:postgresql://%DB_CONTAINER%:5432/%DB_NAME% -e SPRING_DATASOURCE_USERNAME=%DB_USER% -e SPRING_DATASOURCE_PASSWORD=%DB_PASSWORD% orders-api:%VERSION%'
            }
        }

        stage('Container Validation') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                bat 'docker ps --filter "name=%CANDIDATE%"'
                bat 'docker port %CANDIDATE%'
                bat 'docker inspect %CANDIDATE% --format "{{.HostConfig.NetworkMode}}"'
            }
        }

        stage('Application Health Check') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                powershell '''
                    for ($i = 0; $i -lt 30; $i++) {
                        try {
                            $r = Invoke-WebRequest -UseBasicParsing "http://localhost:$env:CANDIDATE_PORT/health" -TimeoutSec 3

                            if ($r.StatusCode -eq 200 -and $r.Content -eq "UP") {
                                exit 0
                            }
                        }
                        catch {
                        }

                        Start-Sleep 2
                    }

                    docker logs $env:CANDIDATE
                    throw "Health check failed"
                '''
            }
        }

        stage('Integration Check') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                powershell '''
                    $r = Invoke-WebRequest -UseBasicParsing "http://localhost:$env:CANDIDATE_PORT/db-check" -TimeoutSec 5

                    if ($r.Content -notmatch "DATABASE CONNECTED") {
                        docker logs $env:CANDIDATE
                        throw "DB integration failed"
                    }
                '''
            }
        }

        stage('Traffic Switch') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                script {
                    writeFile(
                        file: 'nginx/default.conf',
                        text: """upstream orders_backend {
    server ${env.CANDIDATE}:8080;
}

server {
    listen 80;

    location / {
        proxy_pass http://orders_backend;
    }
}
"""
                    )

                    bat 'docker cp nginx\\default.conf %PROXY%:/etc/nginx/conf.d/default.conf'
                    bat 'docker exec %PROXY% nginx -t'
                    bat 'docker exec %PROXY% nginx -s reload'

                    echo "TRAFFIC SWITCHED TO ${env.CANDIDATE}"
                }
            }
        }

        stage('Old Version Cleanup') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                bat 'docker rm -f %CURRENT% >nul 2>&1 || exit /b 0'
            }
        }

        stage('Deployment Verification') {
            when {
                expression {
                    params.ACTION == 'DEPLOY'
                }
            }

            steps {
                powershell '''
                    $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/version -TimeoutSec 5

                    if ($r.Content.Trim() -ne $env:VERSION) {
                        throw "Traffic verification failed"
                    }
                '''

                bat 'docker ps'
                bat 'docker network inspect %NETWORK%'
            }
        }

       stage('Rollback') {
    when {
        expression {
            params.ACTION == 'ROLLBACK'
        }
    }

    steps {
        bat 'docker image inspect orders-api:%ROLLBACK_VERSION%'

        bat 'docker rm -f orders-blue >nul 2>&1 || exit /b 0'
        bat 'docker rm -f orders-green >nul 2>&1 || exit /b 0'

        bat 'docker run -d --name orders-blue --network %NETWORK% -p 8081:8080 -e APP_ENV=PRODUCTION -e APP_VERSION=%ROLLBACK_VERSION% -e SPRING_DATASOURCE_URL=jdbc:postgresql://%DB_CONTAINER%:5432/%DB_NAME% -e SPRING_DATASOURCE_USERNAME=%DB_USER% -e SPRING_DATASOURCE_PASSWORD=%DB_PASSWORD% orders-api:%ROLLBACK_VERSION%'

        powershell '''
            for ($i = 0; $i -lt 30; $i++) {
                try {
                    $r = Invoke-WebRequest -UseBasicParsing http://localhost:8081/health -TimeoutSec 3

                    if ($r.StatusCode -eq 200 -and $r.Content -eq "UP") {
                        exit 0
                    }
                }
                catch {
                }

                Start-Sleep 2
            }

            docker logs orders-blue
            throw "Rollback health failed"
        '''

        script {
            writeFile(
                file: 'nginx/default.conf',
                text: """upstream orders_backend {
    server orders-blue:8080;
}

server {
    listen 80;

    location / {
        proxy_pass http://orders_backend;
    }
}
"""
            )
        }

        bat 'docker cp nginx\\default.conf %PROXY%:/etc/nginx/conf.d/default.conf'
        bat 'docker exec %PROXY% nginx -t'
        bat 'docker exec %PROXY% nginx -s reload'

        echo 'FINAL RESULT: ROLLBACK'
    }
}
    }
    post {
        success {
            echo 'DEPLOYMENT RESULT: SUCCESS'
        }

        failure {
            echo 'DEPLOYMENT RESULT: FAILED'

            bat 'docker ps -a'

            bat 'docker network inspect %NETWORK% > deployment-network.txt 2>nul || exit /b 0'

            bat 'docker logs orders-blue > orders-blue.log 2>&1 || exit /b 0'

            bat 'docker logs orders-green > orders-green.log 2>&1 || exit /b 0'

            archiveArtifacts artifacts: '*.log,*.txt', allowEmptyArchive: true
        }
    }
}