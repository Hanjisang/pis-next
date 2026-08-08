package com.hanjisang.pis.v2.frozen.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.frozen.application.V2FrozenApplicationService;
import com.hanjisang.pis.v2.frozen.application.V2FrozenApplicationService.FinishFrozenCommand;
import com.hanjisang.pis.v2.frozen.application.V2FrozenApplicationService.OpenRoundCommand;
import com.hanjisang.pis.v2.frozen.application.V2FrozenApplicationService.RegisterFrozenSpecimenCommand;

@RestController
@RequestMapping("/api/v2")
public class V2FrozenController {

    private final V2FrozenApplicationService service;

    public V2FrozenController(V2FrozenApplicationService service) { this.service = service; }

    @PostMapping("/frozen/cases/{caseId}/rounds")
    public V2FrozenApplicationService.RoundResult openRound(@PathVariable UUID caseId,
            @RequestBody OpenRoundRequest request) {
        return service.openRound(caseId, new OpenRoundCommand(request.arrivalTime(), request.idempotencyKey()));
    }

    @PostMapping("/frozen/cases/{caseId}/specimens")
    public V2FrozenApplicationService.RoundResult registerSpecimen(@PathVariable UUID caseId,
            @RequestBody RegisterSpecimenRequest request) {
        return service.registerSpecimen(caseId, new RegisterFrozenSpecimenCommand(request.specimenCode(),
                request.specimenKindCode(), request.collectionSite(), request.collectionMethodCode(), request.labelCode(),
                request.idempotencyKey()));
    }

    @PostMapping("/frozen/rounds/{roundId}/diagnosis")
    public V2FrozenApplicationService.DiagnosisResult diagnosis(@PathVariable UUID roundId,
            @RequestBody IdempotencyRequest request) {
        return service.createDiagnosis(roundId, request.idempotencyKey());
    }

    @GetMapping("/frozen/cases/{caseId}/workspace")
    public V2FrozenApplicationService.FrozenWorkspace workspace(@PathVariable UUID caseId) {
        return service.workspace(caseId);
    }

    @PostMapping("/frozen/cases/{caseId}/finish")
    public V2FrozenApplicationService.EndResult finish(@PathVariable UUID caseId,
            @RequestBody IdempotencyRequest request) {
        return service.finishFrozenCase(caseId, new FinishFrozenCommand(request.idempotencyKey()));
    }

    public record OpenRoundRequest(java.time.Instant arrivalTime, String idempotencyKey) { }
    public record RegisterSpecimenRequest(String specimenCode, String specimenKindCode, String collectionSite,
            String collectionMethodCode, String labelCode, String idempotencyKey) { }
    public record IdempotencyRequest(String idempotencyKey) { }
}
