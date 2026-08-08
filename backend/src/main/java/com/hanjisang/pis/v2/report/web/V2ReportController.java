package com.hanjisang.pis.v2.report.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.report.application.V2ReportApplicationService;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService.CreateTemplateCommand;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService.CreateTemplateVersionCommand;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService.SignOutCommand;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService.SupplementalCommand;
import com.hanjisang.pis.v2.report.application.V2ReportApplicationService.WithdrawCommand;

@RestController
@RequestMapping("/api/v2")
public class V2ReportController {

    private final V2ReportApplicationService service;

    public V2ReportController(V2ReportApplicationService service) {
        this.service = service;
    }

    @GetMapping("/diagnoses/{diagnosisId}/report-preview")
    public V2ReportApplicationService.PreviewResult preview(@PathVariable UUID diagnosisId,
            @RequestParam(required = false) UUID templateVersionId) {
        return service.preview(diagnosisId, templateVersionId);
    }

    @PostMapping("/diagnoses/{diagnosisId}/sign-out")
    public V2ReportApplicationService.ReportResult signOut(@PathVariable UUID diagnosisId,
            @RequestBody SignOutRequest request) {
        return service.signOut(diagnosisId, new SignOutCommand(request.templateVersionId(), request.idempotencyKey()));
    }

    @PostMapping("/reports/{reportId}/withdraw")
    public V2ReportApplicationService.ReportResult withdraw(@PathVariable UUID reportId,
            @RequestBody WithdrawRequest request) {
        return service.withdraw(reportId, new WithdrawCommand(request.reason(), request.idempotencyKey()));
    }

    @PostMapping("/diagnoses/{diagnosisId}/supplemental")
    public V2ReportApplicationService.ReportResult supplement(@PathVariable UUID diagnosisId,
            @RequestBody SupplementalRequest request) {
        return service.supplement(diagnosisId, new SupplementalCommand(request.priorReportId(),
                request.templateVersionId(), request.content(), request.idempotencyKey()));
    }

    @GetMapping("/reports/{reportId}")
    public V2ReportApplicationService.ReportResult get(@PathVariable UUID reportId) {
        return service.get(reportId);
    }

    @GetMapping("/cases/{caseId}/reports")
    public List<V2ReportApplicationService.ReportResult> history(@PathVariable UUID caseId) {
        return service.history(caseId);
    }

    @GetMapping("/cases/{caseId}/reports/effective")
    public List<V2ReportApplicationService.ReportResult> effective(@PathVariable UUID caseId) {
        return service.effective(caseId);
    }

    @GetMapping(value = "/reports/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID reportId) {
        V2ReportApplicationService.PdfResult result = service.pdf(reportId);
        return ResponseEntity.ok().header("Content-Disposition", "inline; filename=\"" + result.reportNo() + ".pdf\"")
                .header("X-PIS-V2-Report-Pdf-Reference", result.fileReference())
                .header("X-PIS-V2-Report-Pdf-Hash", result.contentHash()).body(result.content());
    }

    @PostMapping("/report-templates")
    public V2ReportApplicationService.TemplateResult createTemplate(@RequestBody CreateTemplateRequest request) {
        return service.createTemplate(new CreateTemplateCommand(request.code(), request.name(), request.businessTypeId()));
    }

    @PostMapping("/report-templates/{templateId}/versions")
    public V2ReportApplicationService.TemplateVersionResult createTemplateVersion(@PathVariable UUID templateId,
            @RequestBody CreateTemplateVersionRequest request) {
        return service.createTemplateVersion(templateId, new CreateTemplateVersionCommand(request.definition()));
    }

    @PostMapping("/report-template-versions/{versionId}/publish")
    public V2ReportApplicationService.TemplateVersionResult publishTemplateVersion(@PathVariable UUID versionId,
            @RequestBody IdempotencyRequest request) {
        return service.publishTemplateVersion(versionId, request.idempotencyKey());
    }

    public record SignOutRequest(UUID templateVersionId, String idempotencyKey) { }
    public record WithdrawRequest(String reason, String idempotencyKey) { }
    public record SupplementalRequest(UUID priorReportId, UUID templateVersionId, String content,
            String idempotencyKey) { }
    public record CreateTemplateRequest(String code, String name, UUID businessTypeId) { }
    public record CreateTemplateVersionRequest(String definition) { }
    public record IdempotencyRequest(String idempotencyKey) { }
}
