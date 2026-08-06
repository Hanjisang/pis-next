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

function Invoke-SyntheticSql {
    param([string]$Sql)
    & $dockerCommand compose exec -T -e "PGPASSWORD=$env:PIS_DB_PASSWORD" postgres psql -U pis -d pis -v ON_ERROR_STOP=1 -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw 'Synthetic P17 seed SQL failed.'
    }
}

function Invoke-P17 {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Body
    )
    $json = if ($null -eq $Body) { $null } else { $Body | ConvertTo-Json -Depth 10 -Compress }
    try {
        $response = if ($null -eq $json) {
            Invoke-RestMethod -Method $Method -Uri "http://localhost:8080/api/p17$Path"
        } else {
            Invoke-RestMethod -Method $Method -Uri "http://localhost:8080/api/p17$Path" -ContentType 'application/json' -Body $json
        }
        return $response
    } catch {
        throw "P17 HTTP $Method $Path failed: $($_.Exception.Message)"
    }
}

function New-SyntheticFixture {
    $suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
    $caseId = [guid]::NewGuid()
    $patientReferenceId = [guid]::NewGuid()
    $snapshotId = [guid]::NewGuid()
    $requestId = [guid]::NewGuid()
    $specimenId = [guid]::NewGuid()
    $grossingBatchId = [guid]::NewGuid()
    $blockId = [guid]::NewGuid()
    $boxId = [guid]::NewGuid()
    $caseNo = "SMOKE-P17-CASE-$suffix"
    $specimenNo = "SMOKE-P17-SP-$suffix"
    $grossingNo = "SMOKE-P17-GROSS-$suffix"
    $blockNo = "SMOKE-P17-BLOCK-$suffix"
    $boxNo = "SMOKE-P17-BOX-$suffix"
    $sql = @"
INSERT INTO pis.patient_context_reference(id, source_system_code, external_patient_id, patient_namespace_code,
    reference_state_code, created_at, created_by_ref)
VALUES ('$patientReferenceId', 'P17-SMOKE', 'PATIENT-$suffix', 'SYNTHETIC', 'ACTIVE', CURRENT_TIMESTAMP, 'p17-smoke');
INSERT INTO pis.patient_visit_snapshot(id, patient_reference_id, visit_reference_id, snapshot_version_no,
    snapshot_digest, created_at, created_by_ref)
VALUES ('$snapshotId', '$patientReferenceId', NULL, 1, 'SMOKE-P17-SNAPSHOT-$suffix', CURRENT_TIMESTAMP, 'p17-smoke');
INSERT INTO pis.pathology_request(id, source_system_code, application_no, application_lifecycle_state_code,
    patient_reference_id, visit_reference_id, request_received_at, request_channel_code, request_content_text,
    pathology_modality_code, record_version_no, concurrency_version, source_message_identity, source_message_digest,
    manual_reason, created_at, created_by_ref)
VALUES ('$requestId', 'P17-SMOKE', 'SMOKE-P17-REQ-$suffix', 'P08-REQ-001-ST-04', '$patientReferenceId', NULL,
    CURRENT_TIMESTAMP, 'SYNTHETIC', 'P17 synthetic request', 'HISTOLOGY', 1, 1, 'SMOKE-P17-MSG-$suffix',
    'SMOKE-P17-DIGEST-$suffix', 'P17 smoke fixture', CURRENT_TIMESTAMP, 'p17-smoke');
INSERT INTO pis.pathology_case(id, case_no, case_lifecycle_state_code, request_id, patient_visit_snapshot_id,
    pathology_modality_code, case_source_code, case_established_at, case_effective_at, record_version_no,
    concurrency_version, created_at, created_by_ref, organization_reference)
VALUES ('$caseId', '$caseNo', 'P08-CASE-001-ST-03', '$requestId', '$snapshotId', 'HISTOLOGY', 'SYNTHETIC',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1, CURRENT_TIMESTAMP, 'p17-smoke', 'LOCAL_HOSPITAL');
INSERT INTO pis.specimen(id, case_id, specimen_no, specimen_kind_code, specimen_source_code, collection_site_text,
    collection_method_code, specimen_lifecycle_state_code, record_version_no, concurrency_version,
    organization_reference, created_at, created_by_ref)
VALUES ('$specimenId', '$caseId', '$specimenNo', 'TISSUE', 'LOCAL', 'synthetic site', 'SURGICAL',
    'P08-SM-003-ST-03', 1, 1, 'LOCAL_HOSPITAL', CURRENT_TIMESTAMP, 'p17-smoke');
INSERT INTO pis.grossing_batch(id, batch_no, organization_reference, task_state_code, batch_state_code,
    assigned_actor_ref, actual_actor_ref, handed_off_at, record_version_no, concurrency_version, created_at, created_by_ref)
VALUES ('$grossingBatchId', '$grossingNo', 'LOCAL_HOSPITAL', 'P16-TASK-COMPLETED', 'P16-GROSSING-HANDED-OFF',
    'p15-local-registration-actor', 'p15-local-registration-actor', CURRENT_TIMESTAMP, 1, 1, CURRENT_TIMESTAMP, 'p17-smoke');
INSERT INTO pis.tissue_block(id, case_id, specimen_id, batch_id, block_no, block_kind_code, source_material_kind_code,
    block_lifecycle_state_code, physical_formed_at, tissue_box_identity_id, record_version_no, concurrency_version,
    organization_reference, created_at, created_by_ref)
VALUES ('$blockId', '$caseId', '$specimenId', '$grossingBatchId', '$blockNo', 'ROUTINE', 'TISSUE',
    'P08-SM-004-ST-02', NULL, '$boxId', 1, 0, 'LOCAL_HOSPITAL', CURRENT_TIMESTAMP, 'p17-smoke');
INSERT INTO pis.tissue_box_identity(id, block_id, tissue_box_no, box_state_code, organization_reference,
    assigned_at, created_at, created_by_ref)
VALUES ('$boxId', '$blockId', '$boxNo', 'P16-TISSUE-BOX-ACTIVE', 'LOCAL_HOSPITAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'p17-smoke');
"@
    Invoke-SyntheticSql $sql
    return @{ blockId = $blockId; suffix = $suffix }
}

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) {
        throw "$Message. Expected '$Expected', got '$Actual'."
    }
}

$fixture = New-SyntheticFixture
$suffix = $fixture.suffix
$blockId = $fixture.blockId
$task = Invoke-P17 'Post' '/processing-tasks' @{ tissueBlockId = $blockId; idempotencyKey = "smoke-task-$suffix" }
$task = Invoke-P17 'Post' "/processing-tasks/$($task.taskId)/takeover" @{ expectedVersion = $task.concurrencyVersion; idempotencyKey = "smoke-takeover-$suffix" }
$batch = Invoke-P17 'Post' "/processing-tasks/$($task.taskId)/batches" @{
    programCode = 'P17-SYNTHETIC-REFERENCE'; versionLabel = 'SYNTHETIC-1'; executionMode = 'HUMAN'; idempotencyKey = "smoke-batch-$suffix"
}
$member = Invoke-P17 'Post' "/processing-batches/$($batch.batchId)/members" @{ tissueBlockId = $blockId; idempotencyKey = "smoke-member-$suffix" }
$run = Invoke-P17 'Post' "/processing-batches/$($batch.batchId)/start" @{ expectedVersion = $batch.concurrencyVersion; idempotencyKey = "smoke-start-$suffix" }
Invoke-P17 'Post' '/processing-runs/raw-results' @{
    runId = $run.runId; externalMessageId = "smoke-message-$suffix"; payloadDigest = "smoke-digest-$suffix";
    rawStateCode = 'P17-RAW-COMPLETE'; payloadReference = 'synthetic://p17/smoke'; idempotencyKey = "smoke-raw-$suffix"
} | Out-Null
$result = Invoke-P17 'Post' '/processing-runs/results' @{
    runId = $run.runId; memberId = $member.memberId; resultStateCode = 'P17-RESULT-VALIDATED'; canEnterEmbedding = $true;
    summary = 'synthetic smoke validated result'; expectedMemberVersion = $member.concurrencyVersion; idempotencyKey = "smoke-result-$suffix"
}
$batch = Invoke-P17 'Get' "/processing-batches/$($batch.batchId)" $null
$batch = Invoke-P17 'Post' "/processing-batches/$($batch.batchId)/complete" @{ expectedVersion = $batch.concurrencyVersion; idempotencyKey = "smoke-complete-batch-$suffix" }
$embedding = Invoke-P17 'Post' '/embedding-tasks' @{ tissueBlockId = $blockId; processingResultId = $result.resultId; idempotencyKey = "smoke-embedding-$suffix" }
$embedding = Invoke-P17 'Post' "/embedding-tasks/$($embedding.taskId)/takeover" @{ expectedVersion = $embedding.concurrencyVersion; idempotencyKey = "smoke-embedding-takeover-$suffix" }
$embedding = Invoke-P17 'Post' "/embedding-tasks/$($embedding.taskId)/start" @{ expectedVersion = $embedding.concurrencyVersion; idempotencyKey = "smoke-embedding-start-$suffix" }
$embedding = Invoke-P17 'Post' "/embedding-tasks/$($embedding.taskId)/requirements" @{
    requirementSnapshot = 'P17-SYNTHETIC-EMBEDDING-REQUIREMENTS'; orientationReference = 'synthetic orientation';
    expectedVersion = $embedding.concurrencyVersion; idempotencyKey = "smoke-requirements-$suffix"
}
$formation = Invoke-P17 'Post' "/embedding-tasks/$($embedding.taskId)/complete" @{
    expectedTaskVersion = $embedding.concurrencyVersion; expectedBlockVersion = 0; idempotencyKey = "smoke-complete-embedding-$suffix"
}
Assert-Equal $formation.stateCode 'P17-ACTUAL-BLOCK-ACTIVE' 'Normal P17 formation state'
Assert-Equal $formation.currentValid $true 'Normal P17 formation validity'

$failureFixture = New-SyntheticFixture
$failureSuffix = $failureFixture.suffix
$failureBlockId = $failureFixture.blockId
$failureTask = Invoke-P17 'Post' '/processing-tasks' @{ tissueBlockId = $failureBlockId; idempotencyKey = "smoke-failure-task-$failureSuffix" }
$failureTask = Invoke-P17 'Post' "/processing-tasks/$($failureTask.taskId)/takeover" @{ expectedVersion = $failureTask.concurrencyVersion; idempotencyKey = "smoke-failure-takeover-$failureSuffix" }
$failureBatch = Invoke-P17 'Post' "/processing-tasks/$($failureTask.taskId)/batches" @{
    programCode = 'P17-SYNTHETIC-REFERENCE'; versionLabel = 'SYNTHETIC-1'; executionMode = 'HUMAN'; idempotencyKey = "smoke-failure-batch-$failureSuffix"
}
$failureMember = Invoke-P17 'Post' "/processing-batches/$($failureBatch.batchId)/members" @{ tissueBlockId = $failureBlockId; idempotencyKey = "smoke-failure-member-$failureSuffix" }
Invoke-P17 'Post' "/processing-batches/$($failureBatch.batchId)/start" @{ expectedVersion = $failureBatch.concurrencyVersion; idempotencyKey = "smoke-failure-start-$failureSuffix" } | Out-Null
$failureBatch = Invoke-P17 'Get' "/processing-batches/$($failureBatch.batchId)" $null
$failureBatch = Invoke-P17 'Post' "/processing-batches/$($failureBatch.batchId)/interrupt" @{ expectedVersion = $failureBatch.concurrencyVersion; reason = 'synthetic interruption'; idempotencyKey = "smoke-interrupt-$failureSuffix" }
$exceptionId = & $dockerCommand compose exec -T -e "PGPASSWORD=$env:PIS_DB_PASSWORD" postgres psql -U pis -d pis -At -c "SELECT id FROM pis.p17_processing_exception WHERE batch_id = '$($failureBatch.batchId)' ORDER BY created_at DESC LIMIT 1"
$exceptionId = ($exceptionId | Select-Object -Last 1).Trim()
Invoke-P17 'Post' '/processing-members/impact' @{
    memberId = $failureMember.memberId; impactStateCode = 'P17-IMPACT-REPROCESS'; canContinue = $false; requiresReprocess = $true;
    isolationRequired = $true; reason = 'synthetic isolation'; idempotencyKey = "smoke-impact-$failureSuffix"
} | Out-Null
Invoke-P17 'Post' '/processing-exceptions/recovery' @{
    exceptionId = $exceptionId; recoveryKindCode = 'P17-RECOVERY-REPROCESS'; reason = 'synthetic recovery'; idempotencyKey = "smoke-recovery-$failureSuffix"
} | Out-Null
$replacement = Invoke-P17 'Post' '/processing-members/reprocess' @{
    memberId = $failureMember.memberId; reason = 'synthetic reprocess'; idempotencyKey = "smoke-reprocess-$failureSuffix"
}
Assert-Equal $replacement.stateCode 'P17-PROCESSING-TASK-PLANNED' 'Failure recovery replacement task state'
Write-Output "P17 normal and failure-recovery smoke passed: formation=$($formation.formationId), replacementTask=$($replacement.taskId)"
