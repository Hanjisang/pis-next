package com.hanjisang.pis.specimen.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.specimen.application.SpecimenReceivingApplicationService;
import com.hanjisang.pis.specimen.application.SpecimenReceivingApplicationService.ExpectedSpecimenCommand;
import com.hanjisang.pis.specimen.application.SpecimenReceivingApplicationService.HandoffCommand;
import com.hanjisang.pis.specimen.application.SpecimenReceivingApplicationService.IsolationCommand;
import com.hanjisang.pis.specimen.application.SpecimenReceivingApplicationService.ReceiveCommand;

@RestController
@RequestMapping("/api/p15")
public class SpecimenReceivingController {

    private final SpecimenReceivingApplicationService service;

    public SpecimenReceivingController(SpecimenReceivingApplicationService service) {
        this.service = service;
    }

    @PostMapping("/cases/{caseId}/expected-specimens")
    public ResponseEntity<?> registerExpected(@PathVariable UUID caseId, @RequestBody ExpectedSpecimenRequest request) {
        var result = service.registerExpected(new ExpectedSpecimenCommand(caseId, request.specimenKindCode(),
                request.collectionSite(), request.collectionMethodCode(), request.expectedQuantity(),
                request.containerBarcode()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/specimens/receive")
    public SpecimenReceivingApplicationService.ReceivingResult receive(@RequestBody ReceiveRequest request) {
        return service.receive(new ReceiveCommand(request.barcode(), request.expectedQuantity(), request.actualQuantity(),
                request.expectedVersion(), request.idempotencyKey()));
    }

    @PostMapping("/specimens/{specimenId}/isolation")
    public SpecimenReceivingApplicationService.IsolationResult isolate(@PathVariable UUID specimenId,
            @RequestBody IsolationRequest request) {
        return service.isolate(specimenId, new IsolationCommand(request.expectedVersion(), request.reason()));
    }

    @PostMapping("/specimens/{specimenId}/handoffs")
    public SpecimenReceivingApplicationService.HandoffResult handoff(@PathVariable UUID specimenId,
            @RequestBody HandoffRequest request) {
        return service.handoff(specimenId, new HandoffCommand(request.toActorRef(), request.expectedVersion(),
                request.idempotencyKey()));
    }

    @GetMapping("/receiving-queue")
    public List<Map<String, Object>> queue() {
        return service.queue();
    }

    @GetMapping("/cases/{caseId}/trace")
    public List<Map<String, Object>> trace(@PathVariable UUID caseId) {
        return service.trace(caseId);
    }

    public record ExpectedSpecimenRequest(String specimenKindCode, String collectionSite,
            String collectionMethodCode, int expectedQuantity, String containerBarcode) {
    }

    public record ReceiveRequest(String barcode, int expectedQuantity, int actualQuantity, long expectedVersion,
            String idempotencyKey) {
    }

    public record IsolationRequest(long expectedVersion, String reason) {
    }

    public record HandoffRequest(String toActorRef, long expectedVersion, String idempotencyKey) {
    }
}
