package com.hanjisang.pis.v2.registration.web;

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
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.RegisterSpecimenCommand;
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

    @GetMapping("/cases/{caseId}")
    public V2RegistrationApplicationService.CaseResult getCase(@PathVariable UUID caseId) {
        return service.getCase(caseId);
    }

    @PostMapping("/specimens")
    public V2RegistrationApplicationService.SpecimenResult registerSpecimen(
            @RequestBody RegisterSpecimenRequest request) {
        return service.registerSpecimen(new RegisterSpecimenCommand(request.caseId(), request.specimenCode(),
                request.specimenKindCode(), request.sourceKindCode(), request.sourceReference(),
                request.collectionSite(), request.collectionMethodCode(), request.labelCode(),
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
                request.specimenKindCode(), request.sourceKindCode(), request.sourceReference(),
                request.collectionSite(), request.collectionMethodCode(), request.labelCode(),
                request.expectedVersion()));
    }

    @PostMapping("/specimens/{specimenId}/soft-delete")
    public V2RegistrationApplicationService.SpecimenResult softDeleteSpecimen(@PathVariable UUID specimenId,
            @RequestBody SoftDeleteSpecimenRequest request) {
        return service.softDeleteSpecimen(specimenId,
                new SoftDeleteSpecimenCommand(request.expectedVersion(), request.reason()));
    }

    public record CreateCaseRequest(String sourceSystemCode, String externalApplicationId, String applicationItemCode,
            String patientReference, String visitReference, String idempotencyKey) { }

    public record RegisterSpecimenRequest(UUID caseId, String specimenCode, String specimenKindCode,
            String sourceKindCode, String sourceReference, String collectionSite, String collectionMethodCode,
            String labelCode, String idempotencyKey) { }

    public record UpdateSpecimenRequest(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String labelCode,
            long expectedVersion) { }

    public record SoftDeleteSpecimenRequest(long expectedVersion, String reason) { }
}
