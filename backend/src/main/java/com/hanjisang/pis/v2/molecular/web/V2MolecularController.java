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

    public record CompleteResultRequest(UUID specimenId, String resultCode, String resultData,
            String idempotencyKey) { }
}
