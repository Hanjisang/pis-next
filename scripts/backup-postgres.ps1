param(
    [string]$DbHost = $env:PIS_DB_HOST,
    [int]$DbPort = $(if ($env:PIS_DB_PORT) { [int]$env:PIS_DB_PORT } else { 5432 }),
    [string]$DbName = $env:PIS_DB_NAME,
    [string]$DbUser = $env:PIS_DB_USER,
    [Parameter(Mandatory = $true)][string]$OutputFile,
    [string]$PgDumpPath = 'pg_dump',
    [switch]$AllowOverwrite
)

$ErrorActionPreference = 'Stop'

foreach ($required in @{
        DbHost = $DbHost
        DbName = $DbName
        DbUser = $DbUser
        PIS_DB_PASSWORD = $env:PIS_DB_PASSWORD
    }.GetEnumerator()) {
    if ([string]::IsNullOrWhiteSpace([string]$required.Value)) {
        throw "$($required.Key) must be set."
    }
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputFile)
if ((Test-Path -LiteralPath $resolvedOutput) -and -not $AllowOverwrite) {
    throw "Backup target already exists. Use -AllowOverwrite only after confirming the exact path: $resolvedOutput"
}
$outputDirectory = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$previousPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $env:PIS_DB_PASSWORD
    & $PgDumpPath --host=$DbHost --port=$DbPort --username=$DbUser --dbname=$DbName `
        --format=custom --no-owner --no-privileges --file=$resolvedOutput
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump failed with exit code $LASTEXITCODE."
    }
}
finally {
    $env:PGPASSWORD = $previousPgPassword
}

$digest = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedOutput).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$resolvedOutput.sha256" -Value "$digest  $([System.IO.Path]::GetFileName($resolvedOutput))" `
    -Encoding ascii -NoNewline
Write-Output "Backup created: $resolvedOutput"
Write-Output "SHA-256: $digest"
