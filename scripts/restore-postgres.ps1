param(
    [string]$DbHost = $env:PIS_DB_HOST,
    [int]$DbPort = $(if ($env:PIS_DB_PORT) { [int]$env:PIS_DB_PORT } else { 5432 }),
    [string]$DbName = $env:PIS_DB_NAME,
    [string]$DbUser = $env:PIS_DB_USER,
    [Parameter(Mandatory = $true)][string]$InputFile,
    [string]$PgRestorePath = 'pg_restore',
    [switch]$ConfirmRestore
)

$ErrorActionPreference = 'Stop'

if (-not $ConfirmRestore) {
    throw 'Restore changes the target database. Re-run with -ConfirmRestore after verifying the exact target.'
}
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

$resolvedInput = [System.IO.Path]::GetFullPath($InputFile)
if (-not (Test-Path -LiteralPath $resolvedInput -PathType Leaf)) {
    throw "Backup file does not exist: $resolvedInput"
}
$checksumFile = "$resolvedInput.sha256"
if (-not (Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
    throw "Checksum file is required before restore: $checksumFile"
}
$expectedDigest = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
$actualDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedInput).Hash.ToLowerInvariant()
if ($expectedDigest -ne $actualDigest) {
    throw 'Backup checksum mismatch. Restore is blocked.'
}

$previousPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $env:PIS_DB_PASSWORD
    & $PgRestorePath --host=$DbHost --port=$DbPort --username=$DbUser --dbname=$DbName `
        --clean --if-exists --no-owner --no-privileges --exit-on-error --single-transaction $resolvedInput
    if ($LASTEXITCODE -ne 0) {
        throw "pg_restore failed with exit code $LASTEXITCODE."
    }
}
finally {
    $env:PGPASSWORD = $previousPgPassword
}

Write-Output "Restore completed for database '$DbName' from: $resolvedInput"
