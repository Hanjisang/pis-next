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

Set-Location (Join-Path $repositoryRoot 'backend')
Invoke-CheckedCommand { & .\mvnw.cmd -B clean verify } 'Backend verification'

Set-Location $repositoryRoot
Invoke-CheckedCommand { & npm.cmd --prefix frontend ci } 'Frontend dependency installation'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run format:check } 'Frontend format check'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run lint } 'Frontend lint'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run typecheck } 'Frontend type check'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run test:unit -- --run } 'Frontend unit tests'
Invoke-CheckedCommand { & npm.cmd --prefix frontend run build } 'Frontend build'
