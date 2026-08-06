$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

if (-not $env:PIS_DB_PASSWORD) {
    throw 'PIS_DB_PASSWORD must be set for local Compose execution.'
}

& docker compose up --build
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose failed with exit code $LASTEXITCODE."
}
