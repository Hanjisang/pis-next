package com.hanjisang.pis.v2.registration.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.CreateCaseCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.CorrectPathologyNumberCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.RegistrationPrintCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.RegisterSpecimenCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.ReceiveSpecimenCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.SplitSpecimenCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.SoftDeleteSpecimenCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.UpdateSpecimenCommand;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository.ApplicationMappingOption;

@RestController
@RequestMapping("/api/v2/registration")
public class V2RegistrationController {

    private final V2RegistrationApplicationService service;

    public V2RegistrationController(V2RegistrationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/cases")
    public V2RegistrationApplicationService.CaseResult createCase(@RequestBody CreateCaseRequest request) {
        return service.createCase(new CreateCaseCommand(request.sourceSystemCode(), request.externalApplicationId(),
                request.applicationItemCode(), request.patientReference(), request.visitReference(),
                request.idempotencyKey()));
    }

    @GetMapping("/application-item-mappings")
    public java.util.List<ApplicationMappingOption> applicationItemMappings() {
        return service.applicationMappings();
    }

    @GetMapping("/queue")
    public V2RegistrationApplicationService.RegistrationQueueResult queue() {
        return service.registrationQueue();
    }

    @PostMapping("/inbox/{applicationId}/register")
    public V2RegistrationApplicationService.CaseResult registerInboundApplication(
            @PathVariable UUID applicationId) {
        return service.registerInboundApplication(applicationId);
    }

    @GetMapping("/cases/{caseId}")
    public V2RegistrationApplicationService.CaseResult getCase(@PathVariable UUID caseId) {
        return service.getCase(caseId);
    }

    @PostMapping("/cases/{caseId}/cancel")
    public V2RegistrationApplicationService.CaseResult cancelCase(@PathVariable UUID caseId,
            @RequestBody CancelCaseRequest request) {
        return service.cancelCase(caseId,
                new V2RegistrationApplicationService.CancelCaseCommand(request.expectedVersion(), request.reason()));
    }

    @PostMapping("/cases/{caseId}/pathology-number")
    public V2RegistrationApplicationService.CaseResult correctPathologyNumber(@PathVariable UUID caseId,
            @RequestBody CorrectPathologyNumberRequest request) {
        return service.correctPathologyNumber(caseId,
                new CorrectPathologyNumberCommand(request.newPathologyNo(), request.reason(), request.expectedVersion()));
    }

    @GetMapping("/cases/{caseId}/pathology-number-history")
    public java.util.List<V2RegistrationApplicationService.PathologyNumberHistoryView> pathologyNumberHistory(
            @PathVariable UUID caseId) {
        return service.pathologyNumberHistory(caseId);
    }

    @PostMapping("/cases/{caseId}/specimen-labels/print")
    public V2RegistrationApplicationService.RegistrationPrintResult printSpecimenLabels(@PathVariable UUID caseId,
            @RequestBody RegistrationPrintRequest request) {
        return service.printSpecimenLabels(caseId,
                new RegistrationPrintCommand(request.specimenIds(), request.copies() == null ? 1 : request.copies(),
                        request.printerProfileCode()));
    }

    @PostMapping("/cases/{caseId}/receipt/print")
    public V2RegistrationApplicationService.RegistrationPrintResult printReceipt(@PathVariable UUID caseId,
            @RequestBody RegistrationPrintRequest request) {
        return service.printReceipt(caseId,
                new RegistrationPrintCommand(request.specimenIds(), request.copies() == null ? 1 : request.copies(),
                        request.printerProfileCode()));
    }

    @PostMapping("/specimens")
    public V2RegistrationApplicationService.SpecimenResult registerSpecimen(
            @RequestBody RegisterSpecimenRequest request) {
        return service.registerSpecimen(new RegisterSpecimenCommand(request.caseId(), request.specimenCode(),
                request.specimenName() == null || request.specimenName().isBlank()
                        ? (request.collectionSite() == null || request.collectionSite().isBlank()
                                ? request.specimenKindCode() : request.collectionSite())
                        : request.specimenName(), request.specimenKindCode(),
                request.creationSourceCode() == null ? "REGISTRATION" : request.creationSourceCode(),
                request.sourceKindCode(), request.sourceReference(),
                request.collectionSite(), request.collectionMethodCode(), request.lateralityCode(),
                request.quantityValue(), request.quantityUnitCode(), request.description(), request.removedAt(),
                request.fixedAt(), request.receivedAt(), request.labelCode(), request.creationReason(),
                request.idempotencyKey()));
    }

    @GetMapping("/specimens/{specimenId}")
    public V2RegistrationApplicationService.SpecimenResult getSpecimen(@PathVariable UUID specimenId) {
        return service.getSpecimen(specimenId);
    }

    @PutMapping("/specimens/{specimenId}")
    public V2RegistrationApplicationService.SpecimenResult updateSpecimen(@PathVariable UUID specimenId,
            @RequestBody UpdateSpecimenRequest request) {
        return service.updateSpecimen(specimenId, new UpdateSpecimenCommand(request.specimenCode(),
                request.specimenName() == null || request.specimenName().isBlank()
                        ? (request.collectionSite() == null || request.collectionSite().isBlank()
                                ? request.specimenKindCode() : request.collectionSite())
                        : request.specimenName(), request.specimenKindCode(), request.sourceKindCode(),
                request.sourceReference(),
                request.collectionSite(), request.collectionMethodCode(), request.lateralityCode(),
                request.quantityValue(), request.quantityUnitCode(), request.description(), request.removedAt(),
                request.fixedAt(), request.receivedAt(), request.labelCode(), request.expectedVersion(),
                request.reason()));
    }

    @PostMapping("/specimens/{specimenId}/soft-delete")
    public V2RegistrationApplicationService.SpecimenResult softDeleteSpecimen(@PathVariable UUID specimenId,
            @RequestBody SoftDeleteSpecimenRequest request) {
        return service.softDeleteSpecimen(specimenId,
                new SoftDeleteSpecimenCommand(request.expectedVersion(), request.reason()));
    }

    @PostMapping("/specimens/{specimenId}/receive")
    public V2RegistrationApplicationService.SpecimenResult receiveSpecimen(@PathVariable UUID specimenId,
            @RequestBody ReceiveSpecimenRequest request) {
        return service.receiveSpecimen(specimenId, new ReceiveSpecimenCommand(request.verificationCode(),
                request.actualDescription(), request.reason(), request.receivedAt(), request.expectedVersion()));
    }

    @PostMapping("/specimens/{specimenId}/split")
    public V2RegistrationApplicationService.SpecimenResult splitSpecimen(@PathVariable UUID specimenId,
            @RequestBody SplitSpecimenRequest request) {
        return service.splitSpecimen(specimenId, new SplitSpecimenCommand(request.childSpecimenCode(),
                request.childSpecimenName(), request.specimenKindCode(), request.sourceKindCode(),
                request.collectionSite(), request.lateralityCode(),
                request.quantityValue(), request.quantityUnitCode(), request.description(), request.labelCode(),
                request.reason()));
    }

    public record CreateCaseRequest(String sourceSystemCode, String externalApplicationId, String applicationItemCode,
            String patientReference, String visitReference, String idempotencyKey) { }

    public record RegisterSpecimenRequest(UUID caseId, String specimenCode, String specimenName,
            String specimenKindCode, String creationSourceCode, String sourceKindCode, String sourceReference,
            String collectionSite, String collectionMethodCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode, String creationReason,
            String idempotencyKey) { }

    public record UpdateSpecimenRequest(String specimenCode, String specimenName, String specimenKindCode,
            String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String lateralityCode,
            BigDecimal quantityValue, String quantityUnitCode, String description, Instant removedAt,
            Instant fixedAt, Instant receivedAt, String labelCode, long expectedVersion, String reason) { }

    public record SoftDeleteSpecimenRequest(long expectedVersion, String reason) { }

    public record CancelCaseRequest(long expectedVersion, String reason) { }
    public record CorrectPathologyNumberRequest(String newPathologyNo, String reason, long expectedVersion) { }
    public record RegistrationPrintRequest(java.util.List<UUID> specimenIds, Integer copies,
            String printerProfileCode) { }
    public record ReceiveSpecimenRequest(String verificationCode, String actualDescription, String reason,
            Instant receivedAt, long expectedVersion) { }
    public record SplitSpecimenRequest(String childSpecimenCode, String childSpecimenName, String specimenKindCode,
            String sourceKindCode, String collectionSite, String lateralityCode, BigDecimal quantityValue,
            String quantityUnitCode, String description, String labelCode, String reason) { }
}
