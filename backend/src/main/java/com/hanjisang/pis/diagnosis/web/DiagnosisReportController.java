package com.hanjisang.pis.diagnosis.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.AssignmentCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.CommandResult;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.CorrectionCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.CreateReportCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.CreateTaskCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.FollowUpCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.FollowUpDecisionCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.GenerateContentCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.ReviewCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.ReviewDecisionCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.RevisionApprovalCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.RevisionCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.ReportDraftCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.SaveDraftCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.SignCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.SubmitDiagnosisCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.VersionCommand;
import com.hanjisang.pis.diagnosis.application.DiagnosisReportApplicationService.WithdrawalExecutionCommand;

@RestController
@RequestMapping("/api/p19")
public class DiagnosisReportController {

    private final DiagnosisReportApplicationService service;

    public DiagnosisReportController(DiagnosisReportApplicationService service) { this.service = service; }

    @GetMapping("/diagnosis-queue") public Object diagnosisQueue(@RequestParam(required = false) String state) { return service.taskQueue(state); }
    @GetMapping("/diagnosis-tasks/{taskId}") public Object task(@PathVariable UUID taskId) { return service.task(taskId).orElseThrow(); }
    @GetMapping("/report-queue") public Object reportQueue(@RequestParam(required = false) String state) { return service.reportQueue(state); }
    @GetMapping("/reports/{reportId}") public Object report(@PathVariable UUID reportId) { return service.report(reportId).orElseThrow(); }
    @GetMapping("/report-content-versions/{contentId}") public Object content(@PathVariable UUID contentId) { return service.content(contentId).orElseThrow(); }

    @PostMapping("/diagnosis-tasks") @ResponseStatus(HttpStatus.CREATED) public CommandResult createTask(@RequestBody CreateTaskRequest request) { return service.createTask(new CreateTaskCommand(request.caseId(), request.modalityCode(), request.categoryCode(), request.priorityCode(), request.dataScopeCode(), request.idempotencyKey())); }
    @PostMapping("/diagnosis-tasks/{taskId}/assign") public CommandResult assign(@PathVariable UUID taskId, @RequestBody AssignmentRequest request) { return service.assign(taskId, new AssignmentCommand(request.actorRef(), request.expectedVersion(), request.idempotencyKey(), request.reasonText())); }
    @PostMapping("/diagnosis-tasks/{taskId}/takeover") public CommandResult takeover(@PathVariable UUID taskId, @RequestBody VersionRequest request) { return service.takeover(taskId, new VersionCommand(request.expectedVersion(), request.idempotencyKey(), request.reasonText())); }
    @PostMapping("/diagnosis-tasks/{taskId}/handoff") public CommandResult handoff(@PathVariable UUID taskId, @RequestBody AssignmentRequest request) { return service.handoff(taskId, new AssignmentCommand(request.actorRef(), request.expectedVersion(), request.idempotencyKey(), request.reasonText())); }
    @PostMapping("/diagnosis-tasks/{taskId}/draft") public CommandResult saveDraft(@PathVariable UUID taskId, @RequestBody DraftRequest request) { return service.saveDraft(taskId, new SaveDraftCommand(request.grossDescriptionReference(), request.microscopicDescription(), request.diagnosisConclusion(), request.supplementaryNote(), request.structuredItems(), request.terminologyReference(), request.expectedVersion(), request.idempotencyKey())); }
    @PostMapping("/diagnosis-tasks/{taskId}/submit-initial") public CommandResult submitInitial(@PathVariable UUID taskId, @RequestBody VersionRequest request) { return service.submitInitial(taskId, new SubmitDiagnosisCommand(request.expectedVersion(), request.idempotencyKey())); }
    @PostMapping("/diagnosis-tasks/{taskId}/follow-ups") public CommandResult followUp(@PathVariable UUID taskId, @RequestBody FollowUpRequest request) { return service.createFollowUp(taskId, new FollowUpCommand(request.targetOpinionVersionId(), request.followUpOpinion(), request.consistencyCode(), request.returnReason(), request.adoptionRecommendation(), request.idempotencyKey())); }
    @PostMapping("/diagnosis-tasks/{taskId}/follow-ups/{followUpId}/accept") public CommandResult acceptFollowUp(@PathVariable UUID taskId, @PathVariable UUID followUpId, @RequestBody VersionRequest request) { return service.acceptFollowUp(taskId, followUpId, new FollowUpDecisionCommand(request.expectedVersion(), request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/diagnosis-tasks/{taskId}/close") public CommandResult close(@PathVariable UUID taskId, @RequestBody VersionRequest request) { return service.closeTask(taskId, new VersionCommand(request.expectedVersion(), request.idempotencyKey(), request.reasonText())); }
    @PostMapping("/diagnosis-tasks/{taskId}/reports") @ResponseStatus(HttpStatus.CREATED) public CommandResult createReport(@PathVariable UUID taskId, @RequestBody CreateReportRequest request) { return service.createReport(taskId, new CreateReportCommand(request.diagnosisVersionId(), request.reportTypeCode(), request.idempotencyKey())); }
    @PostMapping("/reports/{reportId}/draft") public CommandResult updateDraft(@PathVariable UUID reportId, @RequestBody DraftReportRequest request) { return service.updateReportDraft(reportId, new ReportDraftCommand(request.clinicalInformation(), request.specimenInformation(), request.grossDescription(), request.microscopicDescription(), request.diagnosisConclusion(), request.supplementaryNote(), request.technicalResultReferenceSummary(), request.expectedVersion(), request.idempotencyKey())); }
    @PostMapping("/reports/{reportId}/content-versions") public CommandResult generateContent(@PathVariable UUID reportId, @RequestBody ContentRequest request) { return service.generateContent(reportId, new GenerateContentCommand(request.patientSnapshot(), request.encounterSnapshot(), request.caseNoSnapshot(), request.specimenMaterialSummary(), request.clinicalInformation(), request.specimenInformation(), request.grossDescription(), request.microscopicDescription(), request.diagnosisConclusion(), request.supplementaryNote(), request.technicalResultReferenceSummary(), request.templateLogicVersion(), request.diagnosisVersionId(), request.idempotencyKey())); }
    @PostMapping("/report-content-versions/{contentId}/submit-review") public CommandResult submitReview(@PathVariable UUID contentId, @RequestBody ReviewRequest request) { return service.submitReview(contentId, new ReviewCommand(request.reviewerActorRef(), request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/report-content-versions/{contentId}/review") public CommandResult review(@PathVariable UUID contentId, @RequestBody ReviewDecisionRequest request) { return service.approveReview(contentId, new ReviewDecisionCommand(request.reviewerActorRef(), request.decisionCode(), request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/report-content-versions/{contentId}/sign") public CommandResult sign(@PathVariable UUID contentId, @RequestBody SignRequest request) { return service.sign(contentId, new SignCommand(request.reviewerActorRef(), request.expectedReportVersion(), request.idempotencyKey())); }
    @PostMapping("/report-content-versions/{contentId}/resign") public CommandResult resign(@PathVariable UUID contentId, @RequestBody SignRequest request) { return service.resign(contentId, new SignCommand(request.reviewerActorRef(), request.expectedReportVersion(), request.idempotencyKey())); }
    @PostMapping("/reports/{reportId}/supplements") public CommandResult supplement(@PathVariable UUID reportId, @RequestBody RevisionRequest request) { return service.requestSupplement(reportId, new RevisionCommand(request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/supplements/{supplementId}/content-versions") public CommandResult submitSupplement(@PathVariable UUID supplementId, @RequestBody ContentRequest request) { return service.submitSupplement(supplementId, new GenerateContentCommand(request.patientSnapshot(), request.encounterSnapshot(), request.caseNoSnapshot(), request.specimenMaterialSummary(), request.clinicalInformation(), request.specimenInformation(), request.grossDescription(), request.microscopicDescription(), request.diagnosisConclusion(), request.supplementaryNote(), request.technicalResultReferenceSummary(), request.templateLogicVersion(), request.diagnosisVersionId(), request.idempotencyKey())); }
    @PostMapping("/reports/{reportId}/corrections") public CommandResult correction(@PathVariable UUID reportId, @RequestBody CorrectionRequest request) { return service.requestCorrection(reportId, new CorrectionCommand(request.errorTypeCode(), request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/corrections/{correctionId}/approve") public CommandResult approveCorrection(@PathVariable UUID correctionId, @RequestBody RevisionApprovalRequest request) { return service.approveCorrection(correctionId, new RevisionApprovalCommand(request.reviewerActorRef(), request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/corrections/{correctionId}/content-versions") public CommandResult submitCorrection(@PathVariable UUID correctionId, @RequestBody ContentRequest request) { return service.submitCorrection(correctionId, new GenerateContentCommand(request.patientSnapshot(), request.encounterSnapshot(), request.caseNoSnapshot(), request.specimenMaterialSummary(), request.clinicalInformation(), request.specimenInformation(), request.grossDescription(), request.microscopicDescription(), request.diagnosisConclusion(), request.supplementaryNote(), request.technicalResultReferenceSummary(), request.templateLogicVersion(), request.diagnosisVersionId(), request.idempotencyKey())); }
    @PostMapping("/corrections/{correctionId}/content-versions/{contentId}/sign") public CommandResult signCorrection(@PathVariable UUID correctionId, @PathVariable UUID contentId, @RequestBody SignRequest request) { return service.signCorrection(contentId, new SignCommand(request.reviewerActorRef(), request.expectedReportVersion(), request.idempotencyKey())); }
    @PostMapping("/reports/{reportId}/withdrawals") public CommandResult withdrawal(@PathVariable UUID reportId, @RequestBody RevisionRequest request) { return service.requestWithdrawal(reportId, new RevisionCommand(request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/withdrawals/{withdrawalId}/approve") public CommandResult approveWithdrawal(@PathVariable UUID withdrawalId, @RequestBody RevisionApprovalRequest request) { return service.approveWithdrawal(withdrawalId, new RevisionApprovalCommand(request.reviewerActorRef(), request.reasonText(), request.idempotencyKey())); }
    @PostMapping("/withdrawals/{withdrawalId}/execute") public CommandResult executeWithdrawal(@PathVariable UUID withdrawalId, @RequestBody WithdrawalExecutionRequest request) { return service.executeWithdrawal(withdrawalId, new WithdrawalExecutionCommand(request.reasonText(), request.idempotencyKey())); }

    public record CreateTaskRequest(UUID caseId, String modalityCode, String categoryCode, String priorityCode, String dataScopeCode, String idempotencyKey) { }
    public record AssignmentRequest(String actorRef, long expectedVersion, String idempotencyKey, String reasonText) { }
    public record VersionRequest(long expectedVersion, String idempotencyKey, String reasonText) { }
    public record DraftRequest(String grossDescriptionReference, String microscopicDescription, String diagnosisConclusion, String supplementaryNote, String structuredItems, String terminologyReference, long expectedVersion, String idempotencyKey) { }
    public record FollowUpRequest(UUID targetOpinionVersionId, String followUpOpinion, String consistencyCode, String returnReason, String adoptionRecommendation, String idempotencyKey) { }
    public record CreateReportRequest(UUID diagnosisVersionId, String reportTypeCode, String idempotencyKey) { }
    public record DraftReportRequest(String clinicalInformation, String specimenInformation, String grossDescription, String microscopicDescription, String diagnosisConclusion, String supplementaryNote, String technicalResultReferenceSummary, long expectedVersion, String idempotencyKey) { }
    public record ContentRequest(String patientSnapshot, String encounterSnapshot, String caseNoSnapshot, String specimenMaterialSummary, String clinicalInformation, String specimenInformation, String grossDescription, String microscopicDescription, String diagnosisConclusion, String supplementaryNote, String technicalResultReferenceSummary, String templateLogicVersion, UUID diagnosisVersionId, String idempotencyKey) { }
    public record ReviewRequest(String reviewerActorRef, String reasonText, String idempotencyKey) { }
    public record ReviewDecisionRequest(String reviewerActorRef, String decisionCode, String reasonText, String idempotencyKey) { }
    public record SignRequest(String reviewerActorRef, long expectedReportVersion, String idempotencyKey) { }
    public record RevisionRequest(String reasonText, String idempotencyKey) { }
    public record CorrectionRequest(String errorTypeCode, String reasonText, String idempotencyKey) { }
    public record RevisionApprovalRequest(String reviewerActorRef, String reasonText, String idempotencyKey) { }
    public record WithdrawalExecutionRequest(String reasonText, String idempotencyKey) { }
}
