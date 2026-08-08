package com.hanjisang.pis.v2.consultation.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.consultation.application.V2ConsultationApplicationService;
import com.hanjisang.pis.v2.consultation.application.V2ConsultationApplicationService.ExternalMaterialCommand;

@RestController
@RequestMapping("/api/v2/consultation")
public class V2ConsultationController {

    private final V2ConsultationApplicationService service;

    public V2ConsultationController(V2ConsultationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/cases/{caseId}/external-material")
    public V2ConsultationApplicationService.ExternalMaterialResult registerExternalMaterial(
            @PathVariable UUID caseId, @RequestBody ExternalMaterialRequest request) {
        return service.registerExternalMaterial(caseId, new ExternalMaterialCommand(request.externalReference(),
                request.specimenKindCode(), request.blockCode(), request.blockType(), request.operatorId(),
                request.createLocalSlide(), request.localSlideCode(), request.localSlideType(),
                request.idempotencyKey()));
    }

    public record ExternalMaterialRequest(String externalReference, String specimenKindCode, String blockCode,
            String blockType, String operatorId, boolean createLocalSlide, String localSlideCode,
            String localSlideType, String idempotencyKey) { }
}
