package com.hanjisang.pis.v2.report.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService;
import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService.ClinicianQueryCommand;
import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService.ClinicianReportResult;
import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService.DeclareDelayCommand;
import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService.PatientQueryCommand;
import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService.PatientReportResult;
import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService.ResolveDelayCommand;

@RestController
@RequestMapping("/api/v2/report-center")
public class V2ReportCenterController {

    private final V2ReportCenterApplicationService service;

    public V2ReportCenterController(V2ReportCenterApplicationService service) { this.service = service; }

    @GetMapping
    public V2ReportCenterApplicationService.ReportCenterResult get() { return service.get(); }

    @GetMapping("/access/clinician")
    public List<ClinicianReportResult> clinicianQuery(
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String pathologyNo,
            @RequestParam(required = false) String patientReference) {
        return service.clinicianQuery(new ClinicianQueryCommand(reportNo, pathologyNo, patientReference));
    }

    @PostMapping("/access/patient")
    public List<PatientReportResult> patientQuery(@RequestBody PatientQueryRequest request) {
        return service.patientQuery(new PatientQueryCommand(request.reportNo(), request.pathologyNo(),
                request.identityReference(), request.terminalReference()));
    }

    @PostMapping("/delays")
    public V2ReportCenterApplicationService.DelayResult declareDelay(@RequestBody DeclareDelayRequest request) {
        return service.declareDelay(new DeclareDelayCommand(request.diagnosisId(), request.reasonCode(),
                request.reasonDetail(), request.expectedSignAt(), request.idempotencyKey()));
    }

    @PostMapping("/delays/{delayId}/resolve")
    public V2ReportCenterApplicationService.DelayResult resolveDelay(@PathVariable UUID delayId,
            @RequestBody ResolveDelayRequest request) {
        return service.resolveDelay(delayId, new ResolveDelayCommand(request.resolutionNote(), request.idempotencyKey()));
    }

    public record DeclareDelayRequest(UUID diagnosisId, String reasonCode, String reasonDetail,
            Instant expectedSignAt, String idempotencyKey) { }
    public record ResolveDelayRequest(String resolutionNote, String idempotencyKey) { }
    public record PatientQueryRequest(String reportNo, String pathologyNo, String identityReference,
            String terminalReference) { }
}
