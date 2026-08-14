package com.hanjisang.pis.v2.diagnosis.web;

import java.util.UUID;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.AssignDiagnosisCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.AutoAssignDiagnosisCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.CompleteResponsibilityCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.CreateAssignmentRuleCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.CreateTemplateCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.CreateTemplateVersionCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.ReassignDiagnosisCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.SaveDiagnosisCommand;
import com.hanjisang.pis.v2.diagnosis.application.V2DiagnosisApplicationService.UpdateAssignmentRuleCommand;
import com.hanjisang.pis.v2.diagnosis.domain.ResponsibilityRole;

@RestController
@RequestMapping("/api/v2")
public class V2DiagnosisController {

    private final V2DiagnosisApplicationService service;

    public V2DiagnosisController(V2DiagnosisApplicationService service) {
        this.service = service;
    }

    @PostMapping("/diagnoses/claim")
    public V2DiagnosisApplicationService.AssignmentResult claim(@RequestBody ClaimRequest request) {
        return service.claimDiagnosis(request.caseId(), request.idempotencyKey());
    }

    @PostMapping("/diagnoses/self-claim")
    public V2DiagnosisApplicationService.AssignmentResult selfClaim(@RequestBody ClaimRequest request) {
        return service.claimDiagnosis(request.caseId(), request.idempotencyKey());
    }

    @PostMapping("/diagnoses/assign")
    public V2DiagnosisApplicationService.AssignmentResult assign(@RequestBody AssignRequest request) {
        return service.assignDiagnosis(new AssignDiagnosisCommand(request.caseId(), request.doctorId(), request.reason(),
                request.idempotencyKey()));
    }

    @PostMapping("/diagnoses/auto-assign")
    public V2DiagnosisApplicationService.AutoAssignmentResult autoAssign(@RequestBody ClaimRequest request) {
        return service.autoAssignDiagnosis(new AutoAssignDiagnosisCommand(request.caseId(), request.idempotencyKey()));
    }

    @PostMapping("/diagnoses/reassign")
    public V2DiagnosisApplicationService.AssignmentResult reassign(@RequestBody ReassignRequest request) {
        return service.reassignDiagnosis(new ReassignDiagnosisCommand(request.caseId(), request.doctorId(),
                request.reason(), request.idempotencyKey()));
    }

    @PutMapping("/diagnoses/{diagnosisId}/content")
    public V2DiagnosisApplicationService.DiagnosisResult save(@PathVariable UUID diagnosisId,
            @RequestBody SaveRequest request) {
        return service.saveDiagnosis(diagnosisId, new SaveDiagnosisCommand(request.structuredData(),
                request.microscopicDescription(), request.diagnosisText(), request.comment(), request.expectedVersion(),
                request.idempotencyKey()));
    }

    @PostMapping("/diagnoses/{diagnosisId}/complete-initial")
    public V2DiagnosisApplicationService.ResponsibilityCompletionResult completeInitial(@PathVariable UUID diagnosisId,
            @RequestBody CompleteRequest request) {
        return service.completeInitialDiagnosis(diagnosisId, command(request));
    }

    @PostMapping("/diagnoses/{diagnosisId}/complete-review")
    public V2DiagnosisApplicationService.ResponsibilityCompletionResult completeReview(@PathVariable UUID diagnosisId,
            @RequestBody CompleteRequest request) {
        return service.completeReviewDiagnosis(diagnosisId, command(request));
    }

    @PostMapping("/diagnoses/{diagnosisId}/complete-audit")
    public V2DiagnosisApplicationService.ResponsibilityCompletionResult completeAudit(@PathVariable UUID diagnosisId,
            @RequestBody CompleteRequest request) {
        return service.completeAuditDiagnosis(diagnosisId, command(request));
    }

    @PostMapping("/diagnosis-templates")
    public V2DiagnosisApplicationService.TemplateResult createTemplate(@RequestBody CreateTemplateRequest request) {
        return service.createTemplate(new CreateTemplateCommand(request.code(), request.name(), request.businessTypeId(),
                request.scope(), request.idempotencyKey()));
    }

    @PostMapping("/diagnosis-templates/{templateId}/versions")
    public V2DiagnosisApplicationService.TemplateVersionResult createTemplateVersion(@PathVariable UUID templateId,
            @RequestBody CreateTemplateVersionRequest request) {
        return service.createTemplateVersion(new CreateTemplateVersionCommand(templateId, request.schemaDefinition(),
                request.idempotencyKey()));
    }

    @PostMapping("/diagnosis-template-versions/{versionId}/publish")
    public V2DiagnosisApplicationService.TemplateVersionResult publishTemplateVersion(@PathVariable UUID versionId,
            @RequestBody IdempotencyRequest request) {
        return service.publishTemplateVersion(versionId, request.idempotencyKey());
    }

    @GetMapping("/assignment-rules")
    public List<V2DiagnosisApplicationService.AssignmentRuleView> assignmentRules() {
        return service.assignmentRules();
    }

    @PostMapping("/assignment-rules")
    public V2DiagnosisApplicationService.AssignmentRuleView createAssignmentRule(
            @RequestBody AssignmentRuleRequest request) {
        return service.createAssignmentRule(new CreateAssignmentRuleCommand(request.campus(),
                request.businessTypeCode(), request.department(), request.site(), request.diagnosisGroup(),
                request.doctorId(), request.priority(), request.dailyCaseLimit(), request.enabled(),
                request.idempotencyKey()));
    }

    @PutMapping("/assignment-rules/{ruleId}")
    public V2DiagnosisApplicationService.AssignmentRuleView updateAssignmentRule(@PathVariable UUID ruleId,
            @RequestBody UpdateAssignmentRuleRequest request) {
        return service.updateAssignmentRule(ruleId, new UpdateAssignmentRuleCommand(request.campus(),
                request.businessTypeCode(), request.department(), request.site(), request.diagnosisGroup(),
                request.doctorId(), request.priority(), request.dailyCaseLimit(), request.enabled(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @GetMapping("/diagnosis-workspaces/{caseId}")
    public V2DiagnosisApplicationService.DiagnosisWorkspaceResult workspace(@PathVariable UUID caseId) {
        return service.workspace(caseId);
    }

    @GetMapping("/diagnosis-workspaces/frozen-rounds/{roundId}")
    public V2DiagnosisApplicationService.DiagnosisWorkspaceResult frozenRoundWorkspace(@PathVariable UUID roundId) {
        return service.frozenRoundWorkspace(roundId);
    }

    @GetMapping("/diagnosis-workspaces/public-pool")
    public List<V2DiagnosisApplicationService.PublicPoolEntry> publicPool() {
        return service.publicPool();
    }

    private static CompleteResponsibilityCommand command(CompleteRequest request) {
        return new CompleteResponsibilityCommand(request.responsibilityId(), request.responsibilityExpectedVersion(),
                request.structuredData(), request.microscopicDescription(), request.diagnosisText(), request.comment(),
                request.diagnosisExpectedVersion(), request.nextRole(), request.nextDoctorId(), request.nextReason(),
                request.idempotencyKey());
    }

    public record ClaimRequest(UUID caseId, String idempotencyKey) { }
    public record AssignRequest(UUID caseId, String doctorId, String reason, String idempotencyKey) { }
    public record ReassignRequest(UUID caseId, String doctorId, String reason, String idempotencyKey) { }
    public record SaveRequest(String structuredData, String microscopicDescription, String diagnosisText,
            String comment, long expectedVersion, String idempotencyKey) { }
    public record CompleteRequest(UUID responsibilityId, long responsibilityExpectedVersion, String structuredData,
            String microscopicDescription, String diagnosisText, String comment, long diagnosisExpectedVersion,
            ResponsibilityRole nextRole, String nextDoctorId, String nextReason, String idempotencyKey) { }
    public record CreateTemplateRequest(String code, String name, UUID businessTypeId, String scope,
            String idempotencyKey) { }
    public record CreateTemplateVersionRequest(String schemaDefinition, String idempotencyKey) { }
    public record AssignmentRuleRequest(String campus, String businessTypeCode, String department, String site,
            String diagnosisGroup, String doctorId, int priority, int dailyCaseLimit, boolean enabled,
            String idempotencyKey) { }
    public record UpdateAssignmentRuleRequest(String campus, String businessTypeCode, String department, String site,
            String diagnosisGroup, String doctorId, int priority, int dailyCaseLimit, boolean enabled,
            long expectedVersion, String idempotencyKey) { }
    public record IdempotencyRequest(String idempotencyKey) { }
}
