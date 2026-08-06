$ErrorActionPreference = 'Stop'

function Invoke-JsonPost {
    param([string]$Path, [hashtable]$Body, [int]$ExpectedStatus = 200)
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/p19$Path" -Method Post -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 8) -UseBasicParsing
    if ($response.StatusCode -ne $ExpectedStatus) { throw "P19 $Path returned $($response.StatusCode)." }
    return ($response.Content | ConvertFrom-Json)
}

$caseId = [guid]::NewGuid().ToString()
$task = Invoke-JsonPost '/diagnosis-tasks' @{ caseId = $caseId; modalityCode = 'HISTOLOGY'; categoryCode = 'INITIAL'; priorityCode = 'ROUTINE'; dataScopeCode = 'PATHOLOGY'; idempotencyKey = "p19-smoke-create-$caseId" } 201
$taken = Invoke-JsonPost "/diagnosis-tasks/$($task.objectId)/takeover" @{ expectedVersion = 0; idempotencyKey = "p19-smoke-takeover-$caseId"; reasonText = 'P19 smoke takeover' }
Invoke-JsonPost "/diagnosis-tasks/$($task.objectId)/draft" @{ microscopicDescription = 'P19 smoke microscopic description'; diagnosisConclusion = 'P19 smoke synthetic conclusion'; expectedVersion = $taken.concurrencyVersion; idempotencyKey = "p19-smoke-draft-$caseId" } | Out-Null
$diagnosis = Invoke-JsonPost "/diagnosis-tasks/$($task.objectId)/submit-initial" @{ expectedVersion = $taken.concurrencyVersion; idempotencyKey = "p19-smoke-initial-$caseId" }
$report = Invoke-JsonPost "/diagnosis-tasks/$($task.objectId)/reports" @{ diagnosisVersionId = $diagnosis.relatedObjectId; reportTypeCode = 'HISTOPATHOLOGY'; idempotencyKey = "p19-smoke-report-$caseId" } 201
$content = Invoke-JsonPost "/reports/$($report.objectId)/content-versions" @{ patientSnapshot = 'synthetic patient snapshot'; encounterSnapshot = 'synthetic encounter snapshot'; caseNoSnapshot = $caseId; specimenMaterialSummary = 'synthetic specimen material summary'; diagnosisVersionId = $diagnosis.relatedObjectId; diagnosisConclusion = 'P19 smoke synthetic conclusion'; idempotencyKey = "p19-smoke-content-$caseId" }
$review = Invoke-JsonPost "/report-content-versions/$($content.relatedObjectId)/submit-review" @{ reviewerActorRef = 'p19-smoke-independent-reviewer'; reasonText = 'P19 smoke independent review'; idempotencyKey = "p19-smoke-review-$caseId" }
Invoke-JsonPost "/report-content-versions/$($content.relatedObjectId)/review" @{ reviewerActorRef = 'p19-smoke-independent-reviewer'; decisionCode = 'APPROVED'; reasonText = 'P19 smoke review approved'; idempotencyKey = "p19-smoke-approve-$caseId" } | Out-Null
$signed = Invoke-JsonPost "/report-content-versions/$($content.relatedObjectId)/sign" @{ reviewerActorRef = 'p19-smoke-independent-reviewer'; expectedReportVersion = $content.concurrencyVersion; idempotencyKey = "p19-smoke-sign-$caseId" }
if ($signed.stateCode -ne 'SIGNED') { throw 'P19 normal chain did not produce SIGNED.' }

try {
    Invoke-JsonPost "/report-content-versions/$($content.relatedObjectId)/review" @{ reviewerActorRef = 'p15-local-registration-actor'; decisionCode = 'APPROVED'; reasonText = 'same actor must fail'; idempotencyKey = "p19-smoke-sod-$caseId" } | Out-Null
    throw 'P19 same-actor review was accepted.'
}
catch {
    if ($_.Exception.Message -notmatch '403|422') { throw }
}

Write-Output "P19 smoke passed: $($task.objectId) -> $($diagnosis.relatedObjectId) -> $($content.relatedObjectId) -> $($signed.objectId)"
