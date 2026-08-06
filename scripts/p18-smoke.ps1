$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

if (-not $env:PIS_DB_PASSWORD) {
    $env:PIS_DB_PASSWORD = 'change-me-local-only'
}

$dockerCommand = (Get-Command docker.exe -ErrorAction SilentlyContinue).Source
if (-not $dockerCommand -and (Test-Path 'C:\Program Files\Docker\Docker\resources\bin\docker.exe')) {
    $dockerCommand = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
}
if (-not $dockerCommand) {
    throw 'docker.exe is not available.'
}

function Invoke-P18 {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Body
    )
    $json = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 12 -Compress }
    try {
        if ($null -eq $json) {
            return Invoke-RestMethod -Method $Method -Uri "http://localhost:8080/api/p18$Path"
        }
        return Invoke-RestMethod -Method $Method -Uri "http://localhost:8080/api/p18$Path" -ContentType 'application/json' -Body $json
    } catch {
        throw "P18 HTTP $Method $Path failed: $($_.Exception.Message)"
    }
}

function Invoke-SyntheticSql {
    param([string]$Sql)
    & $dockerCommand compose exec -T -e "PGPASSWORD=$env:PIS_DB_PASSWORD" postgres psql -U pis -d pis -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'Synthetic P18 SQL failed.'
    }
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

$targetRow = & $dockerCommand compose exec -T -e "PGPASSWORD=$env:PIS_DB_PASSWORD" postgres psql -U pis -d pis -At -c "SELECT f.id || '|' || b.case_id FROM pis.p17_actual_block_formation f JOIN pis.tissue_block b ON b.id = f.tissue_block_id WHERE f.current_valid = TRUE AND b.organization_reference = 'LOCAL_HOSPITAL' ORDER BY f.formed_at DESC LIMIT 1"
$targetRow = ($targetRow | Select-Object -Last 1).Trim()
if ([string]::IsNullOrWhiteSpace($targetRow) -or -not $targetRow.Contains('|')) {
    throw 'No valid synthetic P17 actual block is available for P18 smoke.'
}
$targetParts = $targetRow.Split('|')
$formationId = [guid]$targetParts[0]
$caseId = [guid]$targetParts[1]
$suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)

$plannedOutput = @{
    sequenceNo = 1
    outputKindCode = 'PLANNED_SLIDE'
    slidePurposeCode = 'DEEP_SECTION'
    plannedLayerReference = 'synthetic-layer'
    plannedQuantity = 1
    plannedUsageCode = 'DIAGNOSTIC_SUPPORT'
    plannedLabelQuantity = 1
    executionNote = 'SYNTHETIC DEV NON-CLINICAL planned output only'
    idempotencyKey = "p18-output-$suffix"
}
$project = @{
    projectCode = 'P18-SYNTHETIC-DEEP-SECTION'
    versionLabel = 'SYNTHETIC-1'
    projectTypeCode = 'DEEP_SECTION'
    actualBlockFormationId = $formationId
    usageCode = 'DIAGNOSTIC_SUPPORT'
    priorityCode = 'ROUTINE'
    reasonText = 'SYNTHETIC DEV NON-CLINICAL technical order smoke'
    plannedOutputs = @($plannedOutput)
}
$order = Invoke-P18 'Post' '/orders' @{
    caseId = $caseId
    orderKindCode = 'TECHNICAL_ORDER'
    priorityCode = 'ROUTINE'
    reasonText = 'SYNTHETIC DEV NON-CLINICAL P18 smoke'
    representedActorRef = 'p15-local-registration-actor'
    projects = @($project)
    idempotencyKey = "p18-create-$suffix"
}
$replay = Invoke-P18 'Post' '/orders' @{
    caseId = $caseId
    orderKindCode = 'TECHNICAL_ORDER'
    priorityCode = 'ROUTINE'
    reasonText = 'SYNTHETIC DEV NON-CLINICAL P18 smoke'
    representedActorRef = 'p15-local-registration-actor'
    projects = @($project)
    idempotencyKey = "p18-create-$suffix"
}
Assert-Equal $replay.duplicate $true 'P18 duplicate create idempotency'
$projectResult = $order.projects[0]
$order = Invoke-P18 'Post' "/orders/$($order.orderId)/submit" @{ expectedVersion = $order.concurrencyVersion; idempotencyKey = "p18-submit-$suffix" }
$projectResult = Invoke-P18 'Post' "/projects/$($projectResult.projectId)/review" @{ decisionCode = 'APPROVED'; reasonText = 'synthetic review'; expectedVersion = $order.projects[0].concurrencyVersion; idempotencyKey = "p18-review-$suffix" }
$projectResult = Invoke-P18 'Post' "/projects/$($projectResult.projectId)/receive" @{ expectedVersion = $projectResult.concurrencyVersion; idempotencyKey = "p18-receive-$suffix" }
$projectResult = Invoke-P18 'Post' "/projects/$($projectResult.projectId)/assign" @{ assignedActorRef = 'p15-local-registration-actor'; reasonText = 'synthetic task assignment'; expectedVersion = $projectResult.concurrencyVersion; idempotencyKey = "p18-assign-$suffix" }
$projectResult = Invoke-P18 'Post' "/projects/$($projectResult.projectId)/execution-handoff" @{ expectedVersion = $projectResult.concurrencyVersion; idempotencyKey = "p18-handoff-$suffix" }
$projectResult = Invoke-P18 'Post' "/projects/$($projectResult.projectId)/result-reference" @{
    resultReferenceKindCode = 'NORMALIZED_BOUNDARY_REFERENCE'
    resultIdentity = "SYNTHETIC-DEV-NON-CLINICAL-$suffix"
    resultDigest = "synthetic-result-digest-$suffix"
    resultEnvironmentCode = 'SYNTHETIC'
    note = 'SYNTHETIC DEV NON-CLINICAL normalized boundary reference; no physical execution asserted'
    expectedVersion = $projectResult.concurrencyVersion
    idempotencyKey = "p18-result-$suffix"
}
$projectResult = Invoke-P18 'Post' "/projects/$($projectResult.projectId)/close" @{ expectedVersion = $projectResult.concurrencyVersion; idempotencyKey = "p18-close-$suffix" }
Assert-Equal $projectResult.resultStateCode 'CLOSED' 'P18 normal project close'
$closedOrder = Invoke-P18 'Get' "/orders/$($order.orderId)" $null
Assert-Equal $closedOrder.stateCode 'COMPLETED' 'P18 derived order completion'

$invalidCase = [guid]::NewGuid()
try {
    Invoke-P18 'Post' '/orders' @{
        caseId = $invalidCase
        orderKindCode = 'TECHNICAL_ORDER'
        priorityCode = 'ROUTINE'
        reasonText = 'SYNTHETIC invalid cross-case target'
        projects = @($project)
        idempotencyKey = "p18-invalid-case-$suffix"
    } | Out-Null
    throw 'P18 invalid cross-case target was not rejected.'
} catch {
    if ($_.Exception.Message -notmatch '400|422|P12-ERR-036|P12-ERR-035') {
        throw
    }
}

$cancelProject = @{
    projectCode = 'P18-SYNTHETIC-IHC'
    versionLabel = 'SYNTHETIC-1'
    projectTypeCode = 'IHC'
    actualBlockFormationId = $formationId
    usageCode = 'DIAGNOSTIC_SUPPORT'
    priorityCode = 'ROUTINE'
    reasonText = 'SYNTHETIC cancellation path'
    plannedOutputs = @(@{
        sequenceNo = 1; outputKindCode = 'PLANNED_TECHNICAL_OUTPUT'; slidePurposeCode = 'IHC'; plannedQuantity = 1;
        plannedUsageCode = 'DIAGNOSTIC_SUPPORT'; plannedLabelQuantity = 1; executionNote = 'synthetic cancellation'
    })
}
$cancelOrder = Invoke-P18 'Post' '/orders' @{
    caseId = $caseId; orderKindCode = 'TECHNICAL_ORDER'; priorityCode = 'ROUTINE'; reasonText = 'SYNTHETIC cancellation';
    projects = @($cancelProject); idempotencyKey = "p18-cancel-create-$suffix"
}
$cancelled = Invoke-P18 'Post' "/projects/$($cancelOrder.projects[0].projectId)/cancel" @{
    cancellationKindCode = 'FULL_CANCEL'; reasonText = 'synthetic cancellation before execution'; impactSummary = 'no downstream fact';
    expectedVersion = $cancelOrder.projects[0].concurrencyVersion; idempotencyKey = "p18-cancel-$suffix"
}
Assert-Equal $cancelled.taskStateCode 'P08-SM-007-ST-04' 'P18 cancellation state'
Assert-Equal (Invoke-P18 'Get' "/orders/$($cancelOrder.orderId)" $null).stateCode 'CANCELLED' 'P18 cancelled order state'

Write-Output "P18 normal and exception smoke passed: order=$($order.orderId), project=$($projectResult.projectId), cancellation=$($cancelOrder.orderId)"
