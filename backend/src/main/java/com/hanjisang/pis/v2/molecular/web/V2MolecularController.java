package com.hanjisang.pis.v2.molecular.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService;
import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService.CompleteResultCommand;
import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService.AttachmentCommand;
import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService.CompleteTestCommand;
import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService.CreateTestCommand;
import com.hanjisang.pis.v2.molecular.application.V2MolecularApplicationService.StartTestCommand;

@RestController
@RequestMapping("/api/v2/molecular")
public class V2MolecularController {

    private final V2MolecularApplicationService service;

    public V2MolecularController(V2MolecularApplicationService service) {
        this.service = service;
    }

    @PostMapping("/cases/{caseId}/results")
    public V2MolecularApplicationService.MolecularResultResult complete(@PathVariable UUID caseId,
            @RequestBody CompleteResultRequest request) {
        return service.completeResult(caseId, new CompleteResultCommand(request.specimenId(), request.resultCode(),
                request.resultData(), request.idempotencyKey()));
    }

    @GetMapping("/results/{resultId}")
    public V2MolecularApplicationService.MolecularResultResult get(@PathVariable UUID resultId) {
        return service.getResult(resultId);
    }

    @GetMapping("/workbench")
    public V2MolecularApplicationService.MolecularWorkbenchResult workbench() { return service.workbench(); }

    @PostMapping("/tests")
    public V2MolecularApplicationService.TestCommandResult createTest(@RequestBody CreateTestRequest request) {
        return service.createTest(new CreateTestCommand(request.caseId(), request.specimenId(), request.projectId(),
                request.detectionNo(), request.instrumentId(), request.reagentKitId(), request.rawDataReference(),
                request.idempotencyKey()));
    }

    @PostMapping("/tests/{id}/start")
    public V2MolecularApplicationService.StartTestResult start(@PathVariable UUID id,
            @RequestBody IdempotentRequest request) {
        return service.startTest(id, new StartTestCommand(request.idempotencyKey()));
    }

    @PostMapping("/tests/{id}/complete")
    public V2MolecularApplicationService.CompleteTestResult completeTest(@PathVariable UUID id,
            @RequestBody CompleteTestRequest request) {
        return service.completeTest(id, new CompleteTestCommand(request.structuredResult(), request.analysisResult(),
                request.idempotencyKey()));
    }

    @PostMapping("/tests/{id}/attachments")
    public V2MolecularApplicationService.TestCommandResult attachment(@PathVariable UUID id,
            @RequestBody AttachmentRequest request) {
        return service.addAttachment(id, new AttachmentCommand(request.digitalSlideId(), request.attachmentReference(),
                request.description()));
    }

    public record CompleteResultRequest(UUID specimenId, String resultCode, String resultData,
            String idempotencyKey) { }
    public record CreateTestRequest(UUID caseId, UUID specimenId, UUID projectId, String detectionNo,
            UUID instrumentId, UUID reagentKitId, String rawDataReference, String idempotencyKey) { }
    public record IdempotentRequest(String idempotencyKey) { }
    public record CompleteTestRequest(String structuredResult, String analysisResult, String idempotencyKey) { }
    public record AttachmentRequest(UUID digitalSlideId, String attachmentReference, String description) { }
}
