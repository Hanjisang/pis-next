package com.hanjisang.pis.v2.consultation.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.AssociateSpecimenCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateBlockCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateDirectSlideCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialProductionApplicationService.CreateGrossingCommand;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService;
import com.hanjisang.pis.v2.registration.application.V2RegistrationApplicationService.RegisterSpecimenCommand;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2ConsultationApplicationService {

    private final JdbcV2RegistrationRepository registrationRepository;
    private final V2RegistrationApplicationService registration;
    private final V2MaterialProductionApplicationService material;
    private final P15AuthorizationService authorization;

    public V2ConsultationApplicationService(JdbcV2RegistrationRepository registrationRepository,
            V2RegistrationApplicationService registration, V2MaterialProductionApplicationService material,
            P15AuthorizationService authorization) {
        this.registrationRepository = registrationRepository;
        this.registration = registration;
        this.material = material;
        this.authorization = authorization;
    }

    @Transactional
    public ExternalMaterialResult registerExternalMaterial(UUID caseId, ExternalMaterialCommand command) {
        ActorContext actor = authorization.require("P14-PERM-008");
        require(caseId, "病例不能为空");
        require(command.externalReference(), "外院材料引用不能为空");
        require(command.blockCode(), "外院蜡块编号不能为空");
        require(command.blockType(), "蜡块类型不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        var pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!"REFERRAL".equals(pathologyCase.businessTypeCode())) {
            throw reject("V2-CONSULTATION-CASE-REQUIRED", "会诊外部材料只能进入会诊病例");
        }
        String specimenCode = "EXT-" + shortCode(command.externalReference());
        var specimen = registration.registerSpecimen(new RegisterSpecimenCommand(caseId, specimenCode,
                command.specimenKindCode(), "EXTERNAL", command.externalReference(), "外院材料",
                "CONSULTATION", null, command.idempotencyKey() + "/specimen"));
        UUID sourceReferenceId = UUID.nameUUIDFromBytes(command.externalReference().getBytes(StandardCharsets.UTF_8));
        var grossing = material.createGrossing(new CreateGrossingCommand(caseId, "OTHER", sourceReferenceId,
                "外院材料接收：" + command.externalReference(), "外院材料，不改变外部来源事实",
                command.operatorId(), command.operatorId(), command.idempotencyKey() + "/grossing"));
        material.associateSpecimen(grossing.grossingId(), new AssociateSpecimenCommand(specimen.specimenId(),
                "外院材料引用=" + command.externalReference(), command.idempotencyKey() + "/grossing-specimen"));
        var block = material.createBlock(grossing.grossingId(), new CreateBlockCommand(specimen.specimenId(),
                command.blockCode(), command.blockType(), command.idempotencyKey() + "/block", true,
                command.externalReference()));
        V2MaterialProductionApplicationService.SlideResult slide = null;
        if (command.createLocalSlide()) {
            slide = material.createDirectExternalSlide(caseId, block.blockId(), new CreateDirectSlideCommand(
                    command.localSlideCode(), command.localSlideType(), command.idempotencyKey() + "/slide"));
        }
        return new ExternalMaterialResult(caseId, specimen.specimenId(), grossing.grossingId(), block.blockId(),
                slide == null ? null : slide.slideId(), command.externalReference());
    }

    private static String shortCode(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9]", "");
        return (normalized.isBlank() ? UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString()
                .replace("-", "") : normalized).substring(0, Math.min(24, normalized.isBlank() ? 32 : normalized.length()));
    }

    private static void require(Object value, String message) {
        if (value == null || value.toString().isBlank()) {
            throw reject("V2-INVALID-REQUEST", message);
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    public record ExternalMaterialCommand(String externalReference, String specimenKindCode, String blockCode,
            String blockType, String operatorId, boolean createLocalSlide, String localSlideCode,
            String localSlideType, String idempotencyKey) { }

    public record ExternalMaterialResult(UUID caseId, UUID specimenId, UUID grossingId, UUID blockId, UUID slideId,
            String externalReference) { }
}
