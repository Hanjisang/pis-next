package com.hanjisang.pis.accession.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.accession.application.RegistrationApplicationService;
import com.hanjisang.pis.accession.application.RegistrationApplicationService.CaseCommand;
import com.hanjisang.pis.accession.application.RegistrationApplicationService.ExternalRegistrationCommand;
import com.hanjisang.pis.accession.application.RegistrationApplicationService.ManualRegistrationCommand;

@RestController
@RequestMapping("/api/p15")
public class RegistrationController {

    private final RegistrationApplicationService service;

    public RegistrationController(RegistrationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/registrations/external")
    public ResponseEntity<?> registerExternal(@RequestBody ExternalRegistrationRequest request) {
        var result = service.registerExternal(new ExternalRegistrationCommand(request.sourceSystemCode(),
                request.externalRequestId(), request.sourceMessageIdentity(), request.sourceMessageVersion(),
                request.payloadDigest(), request.rawPayloadReference(), request.externalPatientId(),
                request.externalVisitId(), request.pathologyModalityCode(), request.requestContent()));
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/registrations/manual")
    public ResponseEntity<?> registerManual(@RequestBody ManualRegistrationRequest request) {
        var result = service.registerManual(new ManualRegistrationCommand(request.pathologyModalityCode(),
                request.requestContent(), request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/registrations/{requestId}/accept")
    public RegistrationApplicationService.RegistrationResult accept(@PathVariable UUID requestId,
            @RequestBody VersionRequest request) {
        return service.accept(requestId, request.expectedVersion());
    }

    @PostMapping("/cases")
    public ResponseEntity<RegistrationApplicationService.CaseResult> establishCase(@RequestBody CaseRequest request) {
        var result = service.establishCase(new CaseCommand(UUID.fromString(request.requestId()),
                request.patientReference(), request.visitReference(), request.pathologyModalityCode()));
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    public record ExternalRegistrationRequest(String sourceSystemCode, String externalRequestId,
            String sourceMessageIdentity, String sourceMessageVersion, String payloadDigest,
            String rawPayloadReference, String externalPatientId, String externalVisitId,
            String pathologyModalityCode, String requestContent) {
    }

    public record ManualRegistrationRequest(String pathologyModalityCode, String requestContent, String reason) {
    }

    public record VersionRequest(long expectedVersion) {
    }

    public record CaseRequest(String requestId, String patientReference, String visitReference,
            String pathologyModalityCode) {
    }
}
