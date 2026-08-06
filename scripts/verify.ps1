$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

function Invoke-CheckedCommand {
    param(
        [scriptblock]$Command,
        [string]$Description
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

$dockerCommand = (Get-Command docker.exe -ErrorAction SilentlyContinue).Source
if (-not $dockerCommand -and (Test-Path 'C:\Program Files\Docker\Docker\resources\bin\docker.exe')) {
    $dockerCommand = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
}
if (-not $dockerCommand) {
    throw 'docker.exe is not available.'
}
if (-not $env:PIS_DB_PASSWORD) {
    $env:PIS_DB_PASSWORD = 'change-me-local-only'
}

Push-Location (Join-Path $repositoryRoot 'backend')
Invoke-CheckedCommand { & .\mvnw.cmd -B -ntp clean verify } 'Backend verification'
Pop-Location

Invoke-CheckedCommand { & npm.cmd --prefix frontend ci } 'Frontend dependency installation'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run format:check } 'Frontend format check'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run lint } 'Frontend lint'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run typecheck } 'Frontend type check'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run test:unit -- --run } 'Frontend unit tests'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run build } 'Frontend build'

Invoke-CheckedCommand { & $dockerCommand version } 'Docker client and server check'
Invoke-CheckedCommand { & $dockerCommand compose version } 'Docker Compose check'
Invoke-CheckedCommand { & $dockerCommand info } 'Docker Engine check'
Invoke-CheckedCommand { & $dockerCommand run --rm hello-world } 'Linux container check'
Invoke-CheckedCommand { & $dockerCommand compose config } 'Compose configuration check'
Invoke-CheckedCommand { & $dockerCommand build -f infra/docker/backend.Dockerfile -t pis-next-backend:p15 . } 'Backend image build'
Invoke-CheckedCommand { & $dockerCommand build -f infra/docker/frontend.Dockerfile -t pis-next-frontend:p15 . } 'Frontend image build'
Invoke-CheckedCommand { & $dockerCommand compose --profile full up -d --build } 'Full-stack container startup'
Invoke-CheckedCommand { & $dockerCommand compose ps } 'Full-stack container status'

$backendHealthy = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try {
        if ((Invoke-RestMethod 'http://localhost:8080/actuator/health').status -eq 'UP') {
            $backendHealthy = $true
            break
        }
    }
    catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $backendHealthy) {
    throw 'Backend health check did not return UP.'
}
if ((Invoke-WebRequest 'http://localhost:5173' -UseBasicParsing).StatusCode -ne 200) {
    throw 'Frontend HTTP check did not return 200.'
}

Push-Location $repositoryRoot
try {
    Invoke-CheckedCommand { & .\backend\mvnw.cmd --version } 'Maven Wrapper version check'
    Invoke-CheckedCommand { & npm.cmd --prefix frontend --version } 'npm.cmd version check'
    Invoke-CheckedCommand { & $dockerCommand version } 'Final Docker version check'
    Invoke-CheckedCommand { & $dockerCommand compose version } 'Final Compose version check'
    Invoke-CheckedCommand { & $dockerCommand compose config } 'Final Compose configuration check'
}
finally {
    Pop-Location
}
