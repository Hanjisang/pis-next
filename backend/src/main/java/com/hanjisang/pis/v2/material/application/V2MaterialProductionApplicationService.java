package com.hanjisang.pis.v2.material.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.integration.device.LabelPrintService;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.JdbcAuditEventRepository.AuditChange;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.capability.BusinessTypeCapability;
import com.hanjisang.pis.v2.capability.BusinessTypeCapabilityService;
import com.hanjisang.pis.v2.material.domain.Block;
import com.hanjisang.pis.v2.material.domain.Grossing;
import com.hanjisang.pis.v2.material.domain.PrintRule;
import com.hanjisang.pis.v2.material.domain.Slide;
import com.hanjisang.pis.v2.material.domain.SlideRule;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository.MaterialIdempotencyResult;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository.MaterialTreeRow;

@Service
public class V2MaterialProductionApplicationService {

    private static final String GROSSING_PERMISSION = "P14-PERM-013";
    private static final String MATERIAL_PERMISSION = "P14-PERM-014";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private static final String RECEIVING_PERMISSION = "P14-PERM-008";
    private static final String SPECIMEN_CANCEL_PERMISSION = "P14-PERM-010";

    private final JdbcV2MaterialRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final LabelPrintService labelPrintService;
    private final BusinessTypeCapabilityService capabilityService;

    public V2MaterialProductionApplicationService(JdbcV2MaterialRepository repository,
            JdbcV2RegistrationRepository registrationRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox, LabelPrintService labelPrintService,
            BusinessTypeCapabilityService capabilityService) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.labelPrintService = labelPrintService;
        this.capabilityService = capabilityService;
    }

    @Transactional
    public GrossingResult createGrossing(CreateGrossingCommand command) {
        validate(command.caseId(), "病例内部ID不能为空");
        validate(command.grossDescription(), "取材描述不能为空");
        validate(command.grossingDoctorId(), "取材医生不能为空");
        validate(command.recorderId(), "取材记录人不能为空");
        validate(command.sourceType(), "取材来源类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        MaterialAuthorization access = authorizeCaseScoped(command.caseId(), GROSSING_PERMISSION,
                "OTHER".equals(command.sourceType()));
        ActorContext actor = access.actor();
        String operation = "PIS-V2-I02-GROSSING-CREATE";
        String digest = digest(command.caseId(), command.sourceType(), command.sourceReferenceId(),
                command.grossDescription(), command.grossingInstruction(), command.grossingDoctorId(),
                command.recorderId());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return GrossingResult.replayed(findGrossing(existing, actor));
        }
        Case pathologyCase = activeCase(command.caseId(), actor);
        if (Grossing.INITIAL.equals(command.sourceType())
                && repository.findLatestActiveGrossing(command.caseId(), Grossing.INITIAL, null,
                        actor.hospitalScope()).isPresent()) {
            throw conflict("该病例已建立首次取材，不能重复创建");
        }
        if (Grossing.TECHNICAL_ORDER.equals(command.sourceType())) {
            validate(command.sourceReferenceId(), "补充取材必须关联技术医嘱项目");
            if (!repository.isSupplementaryGrossingItem(command.sourceReferenceId(), command.caseId(),
                    actor.hospitalScope())) {
                throw new P15BusinessException("V2-SUPPLEMENTARY-GROSSING-SOURCE-INVALID",
                        "技术医嘱不存在、类型不符或不在当前病例范围", 404);
            }
            if (repository.findLatestActiveGrossing(command.caseId(), Grossing.TECHNICAL_ORDER,
                    command.sourceReferenceId(), actor.hospitalScope()).isPresent()) {
                throw conflict("该补取医嘱已建立取材记录，不能重复创建");
            }
        }
        UUID grossingId = UUID.randomUUID();
        reserve(operation, command.idempotencyKey(), digest, "GROSSING", grossingId, actor);
        Instant now = Instant.now();
        Grossing grossing = Grossing.open(grossingId, pathologyCase.id(),
                repository.allocateGrossingNo(actor.hospitalScope(), pathologyCase.id()), command.sourceType(),
                command.sourceReferenceId(), command.grossDescription(), command.grossingInstruction(),
                command.grossingDoctorId(), command.recorderId(), now);
        try {
            repository.insertGrossing(grossing, actor.hospitalScope(), actor.actorId(), now);
        } catch (DataIntegrityViolationException exception) {
            throw conflict(Grossing.INITIAL.equals(command.sourceType())
                    ? "该病例已由其他取材人员建立首次取材" : "取材事实与现有记录冲突");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 0);
        audit.append(operation, access.permissionCode(), actor, "ALLOWED", "COMPLETED", grossing.id(), "V2-GROSSING",
                UUID.randomUUID().toString(), "V2取材已建立");
        outbox.append("PIS-V2-I02-GROSSING-CREATED", grossing.id(), "V2-GROSSING", grossing.concurrencyVersion(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return GrossingResult.of(grossing, false, 0, false);
    }

    @Transactional
    public GrossingResult updateGrossing(UUID grossingId, UpdateGrossingCommand command) {
        ActorContext actor = authorization.require(GROSSING_PERMISSION);
        validate(grossingId, "取材内部ID不能为空");
        validate(command.grossDescription(), "取材描述不能为空");
        validate(command.grossingDoctorId(), "取材医生不能为空");
        validate(command.recorderId(), "取材记录人不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Grossing grossing = findGrossing(grossingId, actor);
        String operation = "PIS-V2-I02-GROSSING-UPDATE";
        String digest = digest(grossingId, command.grossDescription(), command.grossingInstruction(),
                command.grossingDoctorId(), command.recorderId(), command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return GrossingResult.replayed(findGrossing(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "GROSSING", grossingId, actor);
        repository.lockGrossing(grossingId, actor.hospitalScope());
        grossing = findGrossing(grossingId, actor);
        requireVersion(grossing.concurrencyVersion(), command.expectedVersion());
        try {
            grossing.updateDetails(command.grossDescription(), command.grossingInstruction(),
                    command.grossingDoctorId(), command.recorderId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw reject("V2-GROSSING-UPDATE-REJECTED", exception.getMessage());
        }
        persistGrossing(grossing, actor, command.expectedVersion());
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 0);
        audit.append(operation, GROSSING_PERMISSION, actor, "ALLOWED", "COMPLETED", grossingId, "V2-GROSSING",
                UUID.randomUUID().toString(), "V2取材事实已修改");
        return GrossingResult.of(grossing, false, 0, false);
    }

    @Transactional
    public GrossingResult associateSpecimen(UUID grossingId, AssociateSpecimenCommand command) {
        validate(grossingId, "取材内部ID不能为空");
        validate(command.specimenId(), "标本内部ID不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        MaterialAuthorization access = authorizeGrossingScoped(grossingId, GROSSING_PERMISSION);
        ActorContext actor = access.actor();
        Grossing grossing = findGrossing(grossingId, actor);
        String operation = "PIS-V2-I02-GROSSING-SPECIMEN-ASSOCIATE";
        String digest = digest(grossingId, command.specimenId(), command.materialDescription());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return GrossingResult.replayed(findGrossing(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "GROSSING", grossingId, actor);
        repository.lockGrossing(grossingId, actor.hospitalScope());
        grossing = findGrossing(grossingId, actor);
        requireEditable(grossing);
        Specimen specimen = registrationRepository.findSpecimen(command.specimenId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "标本不存在或不在当前数据范围"));
        if (!specimen.caseId().equals(grossing.caseId()) || specimen.deleted()) {
            throw reject("V2-SOURCE-NOT-FOUND", "标本不属于当前病例或已软删除");
        }
        if (Grossing.FROZEN_CONTEXT.equals(grossing.sourceType())
                && !repository.isSpecimenInFrozenRound(grossing.sourceReferenceId(), specimen.id(), actor.hospitalScope())) {
            throw reject("V2-FROZEN-ROUND-SPECIMEN-MISMATCH", "该标本不属于当前冰冻轮次，请返回冰冻工作区核对轮次");
        }
        if (!repository.hasGrossingSpecimen(grossingId, specimen.id())) {
            repository.insertGrossingSpecimen(grossingId, specimen.id(),
                    repository.nextGrossingSpecimenSequence(grossingId), command.materialDescription());
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, access.permissionCode(), actor, "ALLOWED", "COMPLETED", grossingId, "V2-GROSSING",
                UUID.randomUUID().toString(), "V2取材已关联标本");
        return GrossingResult.of(grossing, false, 1, false);
    }

    @Transactional
    public GrossingResult updateGrossingSpecimen(UUID grossingId, UpdateGrossingSpecimenCommand command) {
        validate(command.specimenId(), "标本内部ID不能为空");
        validate(command.materialDescription(), "当前标本的大体所见不能为空");
        MaterialAuthorization access = authorizeGrossingScoped(grossingId, GROSSING_PERMISSION);
        ActorContext actor = access.actor();
        Grossing grossing = findGrossing(grossingId, actor);
        if (grossing.isDeleted()) throw conflict("已失效取材不能修改");
        if (grossing.isCompleted()) validate(command.reason(), "已完成取材的标本所见修正必须填写原因");
        JdbcV2MaterialRepository.GrossingSpecimenFact before = repository.findGrossingSpecimens(grossingId).stream()
                .filter(fact -> fact.specimenId().equals(command.specimenId())).findFirst()
                .orElseThrow(() -> new P15BusinessException("V2-GROSSING-SPECIMEN-NOT-FOUND",
                        "标本不属于当前取材", 404));
        if (!repository.updateGrossingSpecimenDescription(grossingId, command.specimenId(),
                command.materialDescription().trim(), command.expectedVersion())) {
            throw conflict("当前标本的大体所见已被其他用户修改，请刷新后重试");
        }
        if (grossing.isCompleted()) {
            repository.insertGrossingSpecimenCorrection(grossingId, command.specimenId(),
                    before.materialDescription(), command.materialDescription().trim(), command.reason(),
                    actor.hospitalScope(), actor.actorId(), Instant.now());
        }
        audit.append("PIS-V2-GROSSING-SPECIMEN-DESCRIPTION-UPDATE", access.permissionCode(), actor,
                "ALLOWED", "COMPLETED", command.specimenId(), "V2-SPECIMEN", UUID.randomUUID().toString(),
                "当前标本的大体所见已保存");
        return GrossingResult.of(grossing, false, 1, false);
    }

    @Transactional
    public GrossingResult correctCompletedGrossing(UUID grossingId, CorrectGrossingCommand command) {
        ActorContext actor = authorization.require(GROSSING_PERMISSION);
        validate(command.reason(), "取材修正原因不能为空");
        validate(command.grossDescription(), "取材描述不能为空");
        validate(command.grossingDoctorId(), "取材医生不能为空");
        validate(command.recorderId(), "取材记录人不能为空");
        repository.lockGrossing(grossingId, actor.hospitalScope());
        Grossing grossing = findGrossing(grossingId, actor);
        requireVersion(grossing.concurrencyVersion(), command.expectedVersion());
        String beforeDescription = grossing.grossDescription();
        String beforeInstruction = grossing.grossingInstruction();
        try {
            grossing.correctCompletedDetails(command.grossDescription(), command.grossingInstruction(),
                    command.grossingDoctorId(), command.recorderId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        Instant now = Instant.now();
        if (!repository.saveGrossing(grossing, actor.hospitalScope(), command.expectedVersion(), actor.actorId(), now)) {
            throw conflict("取材记录已由其他用户修改，请刷新后重试");
        }
        repository.insertGrossingCorrection(grossingId, command.reason(), beforeDescription,
                grossing.grossDescription(), beforeInstruction, grossing.grossingInstruction(),
                actor.hospitalScope(), actor.actorId(), now);
        audit.appendWithChanges("PIS-V2-GROSSING-CORRECT", GROSSING_PERMISSION, actor, "COMPLETED", grossingId,
                "V2-GROSSING", UUID.randomUUID().toString(), command.reason(),
                List.of(new AuditChange("grossDescription", "大体描述", beforeDescription,
                        grossing.grossDescription()), new AuditChange("grossingInstruction", "取材说明",
                        beforeInstruction, grossing.grossingInstruction())));
        return GrossingResult.of(grossing, false, 0, true);
    }

    @Transactional
    public BlockResult createBlock(UUID grossingId, CreateBlockCommand command) {
        validate(grossingId, "取材内部ID不能为空");
        validate(command.specimenId(), "标本内部ID不能为空");
        validate(command.blockCode(), "蜡块编号不能为空");
        validate(command.blockType(), "蜡块类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        MaterialAuthorization access = authorizeGrossingScoped(grossingId, MATERIAL_PERMISSION);
        ActorContext actor = access.actor();
        Grossing grossing = findGrossing(grossingId, actor);
        String operation = "PIS-V2-I02-BLOCK-CREATE";
        String digest = digest(grossingId, command.specimenId(), command.blockCode(), command.blockType(),
                command.samplingDescription(), command.note());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return BlockResult.replayed(findBlock(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "BLOCK", UUID.randomUUID(), actor);
        repository.lockGrossing(grossingId, actor.hospitalScope());
        grossing = findGrossing(grossingId, actor);
        requireEditable(grossing);
        Specimen specimen = registrationRepository.findSpecimen(command.specimenId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "标本不存在或不在当前数据范围"));
        if (!specimen.caseId().equals(grossing.caseId()) || specimen.deleted()) {
            throw reject("V2-SOURCE-NOT-FOUND", "蜡块来源标本不属于当前病例或已软删除");
        }
        if (!repository.hasGrossingSpecimen(grossing.id(), specimen.id())) {
            throw reject("V2-GROSSING-SPECIMEN-MISSING", "Grossing must be associated with the specimen before block creation");
        }
        if (repository.findActiveBlockIdByCode(grossing.caseId(), command.blockCode(), actor.hospitalScope()).isPresent()) {
            throw conflict("材块编号 " + command.blockCode() + " 已存在");
        }
        UUID blockId = existingReservedId(operation, command.idempotencyKey());
        Block block = command.externalSource()
                ? Block.createExternal(blockId, grossing.caseId(), grossing.id(), specimen.id(), command.blockCode(),
                        command.blockType(), command.externalSourceReference())
                : Block.create(blockId, grossing.caseId(), grossing.id(), specimen.id(), command.blockCode(),
                        command.blockType(), command.samplingDescription(), command.note());
        Instant now = Instant.now();
        try {
            repository.insertBlock(block, actor.hospitalScope(), actor.actorId(), now);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("材块编号 " + command.blockCode() + " 已存在");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, access.permissionCode(), actor, "ALLOWED", "COMPLETED", block.id(), "V2-BLOCK",
                UUID.randomUUID().toString(), "V2蜡块已建立");
        outbox.append("PIS-V2-I02-BLOCK-CREATED", block.id(), "V2-BLOCK", block.concurrencyVersion(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return BlockResult.of(block, false);
    }

    @Transactional
    public SlideResult createDirectCytologySlide(UUID caseId, UUID specimenId, CreateDirectSlideCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(caseId, "病例内部ID不能为空");
        validate(specimenId, "标本内部ID不能为空");
        validate(command.slideCode(), "切片编号不能为空");
        validate(command.slideType(), "切片类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Case pathologyCase = activeCase(caseId, actor);
        BusinessTypeCapability capability = capabilityService.forCase(caseId, actor.hospitalScope());
        if (!capability.supportsDirectSlides()) {
            throw reject("V2-DIRECT-SLIDE-CAPABILITY-REQUIRED", "当前业务类型未启用直接玻片路径");
        }
        Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                .filter(item -> item.caseId().equals(caseId) && !item.deleted())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "标本不属于当前病例"));
        String operation = "PIS-V2-I06-CYTOLOGY-SLIDE-CREATE";
        String digest = digest(caseId, specimenId, command.slideCode(), command.slideType(), command.stainCode());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return SlideResult.of(findSlide(existing, actor), true);
        }
        if (repository.findActiveSlideIdByCode(caseId, command.slideCode(), actor.hospitalScope()).isPresent()) {
            throw reject("V2-SLIDE-CODE-CONFLICT", "同一病例下切片编号已存在");
        }
        UUID slideId = UUID.randomUUID();
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", slideId, actor);
        Slide slide = Slide.fromSpecimenContextWithStain(slideId, caseId, specimenId, command.slideCode(),
                command.slideType(), command.stainCode(), Slide.CYTOLOGY, specimenId, "CYTOLOGY-DIRECT", 1, true);
        Instant now = Instant.now();
        try {
            repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), now);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("玻片编号或业务产出已存在");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
                UUID.randomUUID().toString(), "细胞直接切片已创建");
        outbox.append("V2-I06-CYTOLOGY-SLIDE-CREATED", slide.id(), "V2-SLIDE", slide.concurrencyVersion(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideBatchGenerationResult generateRequiredCytologySlides(UUID caseId,
            GenerateRequiredCytologySlidesCommand command) {
        validate(caseId, "caseId is required");
        validate(command.idempotencyKey(), "idempotencyKey is required");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Case pathologyCase = activeCase(caseId, actor);
        BusinessTypeCapability capability = capabilityService.forCase(caseId, actor.hospitalScope());
        if (!capability.supportsDirectSlides()) throw conflict("Current business type does not support direct cytology slides");
        List<UUID> selectedIds = command.specimenIds() == null ? List.of() : command.specimenIds().stream()
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
        String operation = "PIS-V2-FC03B-CYTOLOGY-SLIDE-GENERATE";
        String digest = digest(caseId, selectedIds, command.slideType(), command.stainCode());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return new SlideBatchGenerationResult(existing.resultCount() == null ? 0 : existing.resultCount(),
                    List.of(), true);
        }
        reserve(operation, command.idempotencyKey(), digest, "SLIDE_BATCH", caseId, actor);
        if (!repository.lockActiveCase(caseId, actor.hospitalScope())) throw conflict("Case is cancelled or out of scope");
        List<Specimen> specimens = registrationRepository.findActiveSpecimensByCase(caseId, actor.hospitalScope());
        if (!selectedIds.isEmpty()) {
            Set<UUID> allowed = specimens.stream().map(Specimen::id).collect(Collectors.toSet());
            if (!allowed.containsAll(selectedIds)) throw conflict("Selected specimen is outside this case");
            specimens = specimens.stream().filter(item -> selectedIds.contains(item.id())).toList();
        }
        if (specimens.isEmpty()) throw conflict("No active cytology specimen is available");
        UUID businessTypeId = repository.findCaseBusinessTypeId(pathologyCase.id(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "Business type was not found"));
        List<SlideRule> rules = repository.findSlideRules(actor.hospitalScope(), businessTypeId, Slide.CYTOLOGY,
                "MANUAL");
        Instant now = Instant.now();
        List<SlideResult> created = new ArrayList<>();
        try {
            for (Specimen specimen : specimens) {
                int outputSequence = 0;
                List<SlideRule> effectiveRules = rules.isEmpty() ? java.util.Collections.singletonList(null) : rules;
                for (SlideRule rule : effectiveRules) {
                    int copies = rule == null ? 1 : rule.copies();
                    for (int occurrence = 1; occurrence <= copies; occurrence++) {
                        outputSequence++;
                        String ruleCode = rule == null ? "CYTOLOGY-DIRECT" : rule.ruleCode();
                        if (repository.slideOutputExistsForSpecimen(specimen.id(), Slide.CYTOLOGY, ruleCode,
                                occurrence)) continue;
                        String code = specimen.specimenCode() + "-" + outputSequence;
                        String slideType = nonBlank(command.slideType(), specimen.preparationMethodCode(),
                                rule == null ? "CYTOLOGY" : rule.slideType());
                        String stainCode = nonBlank(command.stainCode(), rule == null ? null : rule.stainCode(), null);
                        Slide slide = Slide.fromSpecimenContextWithStain(UUID.randomUUID(), caseId, specimen.id(), code,
                                slideType, stainCode, Slide.CYTOLOGY, specimen.id(), ruleCode, occurrence, true);
                        repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), now);
                        created.add(SlideResult.of(slide, false));
                    }
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Another technician generated the same cytology output; refresh and retry");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), created.size());
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId, "V2-CASE",
                UUID.randomUUID().toString(), "cytology slides generated=" + created.size());
        return new SlideBatchGenerationResult(created.size(), created, false);
    }

    @Transactional
    public SlideResult createExtraCytologySlide(UUID caseId, UUID specimenId,
            CreateExtraCytologySlideCommand command) {
        validate(caseId, "caseId is required");
        validate(specimenId, "specimenId is required");
        validate(command.reason(), "Extra slide reason is required");
        validate(command.idempotencyKey(), "idempotencyKey is required");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        activeCase(caseId, actor);
        BusinessTypeCapability capability = capabilityService.forCase(caseId, actor.hospitalScope());
        if (!capability.supportsDirectSlides()) throw conflict("Current business type does not support direct cytology slides");
        Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                .filter(item -> item.caseId().equals(caseId) && !item.deleted())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "Specimen is outside this case"));
        String operation = "PIS-V2-FC03B-CYTOLOGY-SLIDE-EXTRA";
        String digest = digest(caseId, specimenId, command.slideType(), command.stainCode(), command.reason());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) return SlideResult.replayed(findSlide(existing, actor));
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", UUID.randomUUID(), actor);
        if (!repository.lockActiveCase(caseId, actor.hospitalScope())) throw conflict("Case is cancelled");
        int occurrence = repository.nextSpecimenSlideOccurrence(specimenId, Slide.CYTOLOGY, actor.hospitalScope());
        String code = specimen.specimenCode() + "-X" + occurrence;
        String slideType = nonBlank(command.slideType(), specimen.preparationMethodCode(), "CYTOLOGY");
        Slide slide = Slide.fromSpecimenContextWithStain(existingReservedId(operation, command.idempotencyKey()),
                caseId, specimenId, code, slideType, command.stainCode(), Slide.CYTOLOGY, specimenId,
                "MANUAL-CYTOLOGY-EXTRA", occurrence, false);
        try {
            repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), Instant.now());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Slide code " + code + " already exists");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
                UUID.randomUUID().toString(), command.reason());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SpecimenPreparationResult updateCytologyPreparation(UUID caseId, UUID specimenId,
            UpdateCytologyPreparationCommand command) {
        validate(caseId, "caseId is required");
        validate(specimenId, "specimenId is required");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        activeCase(caseId, actor);
        BusinessTypeCapability capability = capabilityService.forCase(caseId, actor.hospitalScope());
        if (!capability.supportsDirectSlides()) throw conflict("Current case is not cytology");
        Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                .filter(item -> item.caseId().equals(caseId) && !item.deleted())
                .orElseThrow(() -> notFound("Specimen is outside this case"));
        requireVersion(specimen.concurrencyVersion(), command.expectedVersion());
        Instant now = Instant.now();
        specimen.updatePreparationMethod(command.preparationMethodCode(), now);
        if (!registrationRepository.updateSpecimenPreparation(specimen.id(), specimen.preparationMethodCode(),
                actor.hospitalScope(), command.expectedVersion(), actor.actorId(), now)) {
            throw conflict("Specimen was changed by another user; refresh and retry");
        }
        audit.append("PIS-V2-FC03B-CYTOLOGY-PREPARATION-UPDATE", MATERIAL_PERMISSION, actor, "ALLOWED",
                "COMPLETED", specimenId, "V2-SPECIMEN", UUID.randomUUID().toString(),
                specimen.preparationMethodCode() == null ? "cleared" : specimen.preparationMethodCode());
        return new SpecimenPreparationResult(specimen.id(), specimen.preparationMethodCode(),
                specimen.concurrencyVersion());
    }

    @Transactional
    public SlideResult createDirectExternalCytologySlide(UUID caseId, UUID specimenId,
            CreateDirectSlideCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(caseId, "caseId is required");
        validate(specimenId, "specimenId is required");
        validate(command.slideCode(), "slideCode is required");
        validate(command.slideType(), "slideType is required");
        validate(command.idempotencyKey(), "idempotencyKey is required");
        activeCase(caseId, actor);
        BusinessTypeCapability capability = capabilityService.forCase(caseId, actor.hospitalScope());
        if (!capability.supportsDirectSlides()) throw conflict("Current case is not cytology");
        Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                .filter(item -> item.caseId().equals(caseId) && !item.deleted())
                .orElseThrow(() -> notFound("Specimen is outside this case"));
        String operation = "PIS-V2-FC03B-CYTOLOGY-EXTERNAL-SLIDE-CREATE";
        String digest = digest(caseId, specimenId, command.slideCode(), command.slideType(), command.stainCode());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) return SlideResult.replayed(findSlide(existing, actor));
        if (repository.findActiveSlideIdByCode(caseId, command.slideCode(), actor.hospitalScope()).isPresent()) {
            throw conflict("Slide code already exists");
        }
        UUID slideId = UUID.randomUUID();
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", slideId, actor);
        Slide slide = Slide.fromSpecimenContextWithStain(slideId, caseId, specimenId, command.slideCode(),
                command.slideType(), command.stainCode(), Slide.EXTERNAL, specimenId, "EXTERNAL-CYTOLOGY", 1,
                false);
        repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), Instant.now());
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
                UUID.randomUUID().toString(), "external cytology slide recorded");
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideResult createDirectExternalSlide(UUID caseId, UUID blockId, CreateDirectSlideCommand command) {
        validate(caseId, "病例内部ID不能为空");
        validate(blockId, "蜡块内部ID不能为空");
        validate(command.slideCode(), "切片编号不能为空");
        validate(command.slideType(), "切片类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        MaterialAuthorization access = authorizeCaseScoped(caseId, MATERIAL_PERMISSION, true);
        ActorContext actor = access.actor();
        activeCase(caseId, actor);
        Block block = findBlock(blockId, actor);
        if (!caseId.equals(block.caseId()) || !block.externalSource() || block.isDeleted()) {
            throw reject("V2-EXTERNAL-BLOCK-REQUIRED", "本院切片必须来源于当前病例的外部蜡块");
        }
        if (repository.findActiveSlideIdByCode(caseId, command.slideCode(), actor.hospitalScope()).isPresent()) {
            throw reject("V2-SLIDE-CODE-CONFLICT", "同一病例下切片编号已存在");
        }
        String operation = "PIS-V2-I06-EXTERNAL-SLIDE-CREATE";
        String digest = digest(caseId, blockId, command.slideCode(), command.slideType());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return SlideResult.replayed(findSlide(existing, actor));
        }
        UUID slideId = UUID.randomUUID();
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", slideId, actor);
        Slide slide = Slide.fromBlockContext(slideId, caseId, blockId, command.slideCode(), command.slideType(),
                Slide.EXTERNAL, blockId, "EXTERNAL-LOCAL", 1, true);
        Instant now = Instant.now();
        repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), now);
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, access.permissionCode(), actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
                UUID.randomUUID().toString(), "外部蜡块的本院切片已创建");
        outbox.append("V2-I06-EXTERNAL-SLIDE-CREATED", slide.id(), "V2-SLIDE", slide.concurrencyVersion(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public BlockResult updateBlock(UUID blockId, UpdateBlockCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(blockId, "蜡块内部ID不能为空");
        validate(command.blockCode(), "蜡块编号不能为空");
        validate(command.blockType(), "蜡块类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Block block = findBlock(blockId, actor);
        Grossing grossing = findGrossing(block.grossingId(), actor);
        String operation = "PIS-V2-I02-BLOCK-UPDATE";
        String digest = digest(blockId, command.blockCode(), command.blockType(), command.samplingDescription(),
                command.note(), command.reason(), command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return BlockResult.replayed(findBlock(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "BLOCK", blockId, actor);
        repository.lockGrossing(block.grossingId(), actor.hospitalScope());
        grossing = findGrossing(block.grossingId(), actor);
        if (grossing.isCompleted()) {
            validate(command.reason(), "已完成取材的材块修正必须填写原因");
        } else {
            requireEditable(grossing);
        }
        requireVersion(block.concurrencyVersion(), command.expectedVersion());
        if (!block.blockCode().equals(command.blockCode())
                && repository.findActiveBlockIdByCode(block.caseId(), command.blockCode(), actor.hospitalScope())
                        .filter(existingId -> !existingId.equals(block.id())).isPresent()) {
            throw conflict("材块编号 " + command.blockCode() + " 已存在");
        }
        String oldCode = block.blockCode();
        if (!oldCode.equals(command.blockCode())) validate(command.reason(), "材块编号修正原因不能为空");
        block.update(command.blockCode(), command.blockType(), command.samplingDescription(), command.note());
        persistBlock(block, actor, command.expectedVersion());
        if (!oldCode.equals(block.blockCode())) {
            repository.insertBlockCodeHistory(block.id(), oldCode, block.blockCode(), command.reason(),
                    actor.hospitalScope(), actor.actorId(), Instant.now());
            renameRelatedSlides(block, actor);
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.appendWithChanges(operation, MATERIAL_PERMISSION, actor, "COMPLETED", block.id(), "V2-BLOCK",
                UUID.randomUUID().toString(), "蜡块信息已修改",
                List.of(new AuditChange("blockCode", "蜡块编号", oldCode, block.blockCode()),
                        new AuditChange("blockType", "蜡块类型", null, command.blockType()),
                        new AuditChange("reason", "修正原因", null, command.reason())));
        return BlockResult.of(block, false);
    }

    @Transactional
    public BlockResult softDeleteBlock(UUID blockId, SoftDeleteCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(blockId, "蜡块内部ID不能为空");
        validate(command.reason(), "蜡块失效原因不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Block block = findBlock(blockId, actor);
        Grossing grossing = findGrossing(block.grossingId(), actor);
        String operation = "PIS-V2-I02-BLOCK-SOFT-DELETE";
        String digest = digest(blockId, command.reason(), command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return BlockResult.replayed(findBlock(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "BLOCK", blockId, actor);
        repository.lockGrossing(block.grossingId(), actor.hospitalScope());
        grossing = findGrossing(block.grossingId(), actor);
        if (repository.activeSlideCountForBlock(blockId, actor.hospitalScope()) > 0) {
            throw conflict("材块已有玻片，不能取消；请保留材料血缘并执行后续纠错");
        }
        requireEditable(grossing);
        requireVersion(block.concurrencyVersion(), command.expectedVersion());
        if (!repository.softDeleteBlock(blockId, actor.hospitalScope(), command.expectedVersion(), command.reason(),
                actor.actorId(), Instant.now())) {
            throw conflict("材块版本冲突，取消未生效");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", block.id(), "V2-BLOCK",
                UUID.randomUUID().toString(), command.reason());
        return BlockResult.of(Block.persisted(block.id(), block.caseId(), block.grossingId(), block.specimenId(),
                block.blockCode(), block.blockType(), block.samplingDescription(), block.quantity(), block.note(),
                block.externalSource(), block.externalSourceReference(),
                Instant.now(), command.reason(), command.expectedVersion() + 1), false);
    }

    @Transactional
    public BlockVerificationResult verifyBlock(UUID blockId, VerifyBlockCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Block block = findBlock(blockId, actor);
        if (block.isDeleted()) throw conflict("已取消材块不能核对");
        validate(command.verifiedCode(), "核对的材块编号不能为空");
        validate(command.verifiedSpecimenId(), "核对的标本不能为空");
        boolean matches = block.blockCode().equals(command.verifiedCode())
                && block.specimenId().equals(command.verifiedSpecimenId()) && command.verifiedQuantity() == 1;
        if (!matches) validate(command.reason(), "核对不一致时必须填写原因");
        Instant now = Instant.now();
        repository.insertBlockVerification(blockId, matches ? "PASSED" : "FAILED", command.verifiedCode(),
                command.verifiedSpecimenId(), command.verifiedQuantity(), command.reason(), actor.hospitalScope(),
                actor.actorId(), now);
        audit.append("PIS-V2-BLOCK-VERIFY", MATERIAL_PERMISSION, actor, "ALLOWED",
                matches ? "COMPLETED" : "REJECTED", blockId, "V2-BLOCK", UUID.randomUUID().toString(),
                matches ? "材块核对通过" : command.reason());
        return new BlockVerificationResult(blockId, matches ? "PASSED" : "FAILED", now, actor.actorId(),
                command.reason());
    }

    @Transactional
    public BlockBatchResult createBlocks(UUID grossingId, CreateBlocksCommand command) {
        if (command.blocks() == null || command.blocks().isEmpty()) {
            throw badRequest("请选择至少一个待建立材块");
        }
        List<BlockResult> created = new ArrayList<>();
        int index = 0;
        for (CreateBlockItem item : command.blocks()) {
            created.add(createBlock(grossingId, new CreateBlockCommand(item.specimenId(), item.blockCode(),
                    item.blockType(), item.samplingDescription(), item.note(),
                    command.idempotencyKey() + "/" + index++, false, null)));
        }
        return new BlockBatchResult(created);
    }

    @Transactional
    public PrintBatchResult printBlocks(PrintBlocksCommand command) {
        if (command.blockIds() == null || command.blockIds().isEmpty()) {
            throw badRequest("请选择至少一个待打印材块");
        }
        List<PrintResult> results = new ArrayList<>();
        int index = 0;
        for (UUID blockId : command.blockIds()) {
            results.add(printBlock(blockId, new PrintCommand(command.reason(),
                    command.idempotencyKey() + "/" + index++)));
        }
        return new PrintBatchResult(results);
    }

    @Transactional
    public GrossingCompletionResult completeGrossing(UUID grossingId, CompleteGrossingCommand command) {
        ActorContext actor = authorization.require(GROSSING_PERMISSION);
        validate(grossingId, "取材内部ID不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Grossing grossing = findGrossing(grossingId, actor);
        String operation = "PIS-V2-I02-GROSSING-COMPLETE";
        String digest = digest(grossingId, command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return completion(findGrossing(existing, actor), existing.resultCount() == null ? 0 : existing.resultCount(),
                    true, actor);
        }
        reserve(operation, command.idempotencyKey(), digest, "GROSSING", grossingId, actor);
        repository.lockGrossing(grossingId, actor.hospitalScope());
        grossing = findGrossing(grossingId, actor);
        requireVersion(grossing.concurrencyVersion(), command.expectedVersion());
        if (grossing.isCompleted()) throw conflict("取材已经完成");
        List<JdbcV2MaterialRepository.GrossingSpecimenFact> specimenFacts =
                repository.findGrossingSpecimens(grossingId);
        if (specimenFacts.isEmpty()) throw conflict("取材完成前必须关联至少一个标本");
        if (specimenFacts.stream().anyMatch(fact -> fact.materialDescription() == null
                || fact.materialDescription().isBlank())) {
            throw conflict("每个标本都必须填写可区分的大体所见");
        }
        List<Block> blocks = repository.findActiveBlocksByGrossing(grossingId, actor.hospitalScope());
        BusinessTypeCapability capability = capabilityService.forCase(grossing.caseId(), actor.hospitalScope());
        if (capability.supportsBlocks() && blocks.isEmpty()) {
            throw conflict("取材完成前必须至少建立一个有效材块");
        }
        JdbcV2MaterialRepository.BlockVerificationPolicy verificationPolicy =
                repository.findBlockVerificationPolicy(grossing.caseId(), actor.hospitalScope());
        if (verificationPolicy.verificationRequired()) {
            for (Block block : blocks) {
                JdbcV2MaterialRepository.BlockVerificationFact verification = repository
                        .latestBlockVerification(block.id(), actor.hospitalScope())
                        .filter(fact -> "PASSED".equals(fact.resultCode()))
                        .orElseThrow(() -> conflict("材块 " + block.blockCode() + " 尚未完成核对"));
                if (verificationPolicy.dualCheckRequired() && !verificationPolicy.sameUserAllowed()
                        && grossing.grossingDoctorId().equals(verification.verifiedByRef())) {
                    throw conflict("材块 " + block.blockCode() + " 需要由另一位人员完成核对");
                }
            }
        }
        UUID businessTypeId = repository.findCaseBusinessTypeId(grossing.caseId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "病例业务类型不存在"));
        String slideContext = Grossing.FROZEN_CONTEXT.equals(grossing.sourceType()) ? Slide.FROZEN_ROUND
                : Grossing.TECHNICAL_ORDER.equals(grossing.sourceType()) ? null : Slide.INITIAL;
        List<SlideRule> rules = slideContext == null ? List.of()
                : repository.findSlideRules(actor.hospitalScope(), businessTypeId, slideContext,
                        "ON_GROSSING_COMPLETE");
        if (!blocks.isEmpty() && slideContext != null && rules.isEmpty()) {
            throw conflict("当前业务类型没有生效的初始切片规则");
        }
        Instant now = Instant.now();
        List<Slide> createdSlides = new ArrayList<>();
        for (Block block : blocks) {
            for (SlideRule rule : rules) {
                for (int occurrence = 1; occurrence <= rule.copies(); occurrence++) {
                    if (repository.slideOutputExists(block.id(), rule.sourceContextType(), rule.ruleCode(), occurrence)) {
                        continue;
                    }
                    Slide slide = Slide.fromBlockContext(UUID.randomUUID(), block.caseId(), block.id(),
                            rule.slideCode(block.blockCode(), occurrence), rule.slideType(), slideContext,
                            Slide.INITIAL.equals(slideContext) ? grossing.id() : grossing.sourceReferenceId(),
                            rule.ruleCode(), occurrence, true);
                    repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), now);
                    createdSlides.add(slide);
                }
            }
        }
        if (!grossing.isCompleted()) {
            grossing.complete(now, actor.actorId());
            persistGrossing(grossing, actor, command.expectedVersion());
        }
        if (Grossing.TECHNICAL_ORDER.equals(grossing.sourceType())) {
            repository.linkSupplementaryGrossingOutputs(grossing.sourceReferenceId(), grossing.id(), blocks,
                    actor.hospitalScope(), actor.actorId(), now);
        }
        PrintRule printRule = repository.findPrintRule(actor.hospitalScope(), businessTypeId, "SLIDE",
                "ON_GROSSING_COMPLETE").orElse(null);
        if (printRule != null) {
            for (Slide slide : createdSlides) {
                recordPrint(grossing.caseId(), "SLIDE", slide.id(), slide.slideCode(), printRule, actor, now);
            }
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), createdSlides.size());
        audit.append(operation, GROSSING_PERMISSION, actor, "ALLOWED", "COMPLETED", grossing.id(), "V2-GROSSING",
                UUID.randomUUID().toString(), "V2取材已完成并同步切片");
        outbox.append("PIS-V2-I02-GROSSING-COMPLETED", grossing.id(), "V2-GROSSING", grossing.concurrencyVersion(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return completion(grossing, createdSlides.size(), false, actor);
    }

    @Transactional
    public GrossingResult reopenGrossing(UUID grossingId, ReopenGrossingCommand command) {
        ActorContext actor = authorization.require(GROSSING_PERMISSION);
        validate(grossingId, "取材内部ID不能为空");
        validate(command.reason(), "取材重开原因不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Grossing grossing = findGrossing(grossingId, actor);
        String operation = "PIS-V2-I02-GROSSING-REOPEN";
        String digest = digest(grossingId, command.reason(), command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return GrossingResult.replayed(findGrossing(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "GROSSING", grossingId, actor);
        repository.lockGrossing(grossingId, actor.hospitalScope());
        grossing = findGrossing(grossingId, actor);
        requireVersion(grossing.concurrencyVersion(), command.expectedVersion());
        grossing.reopen(Instant.now());
        persistGrossing(grossing, actor, command.expectedVersion());
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 0);
        audit.append(operation, GROSSING_PERMISSION, actor, "ALLOWED", "COMPLETED", grossing.id(), "V2-GROSSING",
                UUID.randomUUID().toString(), command.reason());
        return GrossingResult.of(grossing, false, 0, true);
    }

    @Transactional
    public SlideBatchGenerationResult generateRequiredSlides(UUID caseId, GenerateRequiredSlidesCommand command) {
        validate(caseId, "病例内部ID不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Case pathologyCase = activeCase(caseId, actor);
        BusinessTypeCapability capability = capabilityService.forCase(caseId, actor.hospitalScope());
        if (!capability.usesHistologyProcessing()) {
            throw conflict("当前业务类型不属于常规组织制片");
        }
        List<UUID> selectedIds = command.blockIds() == null ? List.of() : command.blockIds().stream()
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
        String operation = "PIS-V2-FC03A-ROUTINE-SLIDE-GENERATE";
        String digest = digest(caseId, selectedIds);
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return new SlideBatchGenerationResult(existing.resultCount() == null ? 0 : existing.resultCount(),
                    List.of(), true);
        }
        reserve(operation, command.idempotencyKey(), digest, "SLIDE_BATCH", caseId, actor);
        if (!repository.lockActiveCase(caseId, actor.hospitalScope())) {
            throw conflict("病例已取消或不在当前数据范围");
        }
        UUID grossingId = repository.findCompletedInitialGrossingId(caseId, actor.hospitalScope())
                .orElseThrow(() -> conflict("首次取材尚未完成，不能生成常规玻片"));
        List<Block> blocks = repository.findActiveInitialBlocksByCase(caseId, actor.hospitalScope());
        if (!selectedIds.isEmpty()) {
            Set<UUID> allowed = blocks.stream().map(Block::id).collect(Collectors.toSet());
            if (!allowed.containsAll(selectedIds)) {
                throw conflict("所选材块不属于当前病例、已失效或不是首次取材材块");
            }
            blocks = blocks.stream().filter(block -> selectedIds.contains(block.id())).toList();
        }
        if (blocks.isEmpty()) {
            throw conflict("当前病例没有可生成玻片的活动材块");
        }
        UUID businessTypeId = repository.findCaseBusinessTypeId(pathologyCase.id(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "病例业务类型不存在"));
        List<SlideRule> rules = repository.findSlideRules(actor.hospitalScope(), businessTypeId, Slide.INITIAL,
                "ON_GROSSING_COMPLETE");
        if (rules.isEmpty()) {
            throw conflict("当前业务类型没有生效的常规玻片规则");
        }
        Instant now = Instant.now();
        List<SlideResult> created = new ArrayList<>();
        try {
            for (Block block : blocks) {
                for (SlideRule rule : rules) {
                    for (int occurrence = 1; occurrence <= rule.copies(); occurrence++) {
                        if (repository.slideOutputExists(block.id(), Slide.INITIAL, grossingId,
                                rule.ruleCode(), occurrence)) {
                            continue;
                        }
                        Slide slide = Slide.initialFromBlock(UUID.randomUUID(), caseId, block.id(),
                                rule.slideCode(block.blockCode(), occurrence), rule.slideType(), grossingId,
                                rule.ruleCode(), occurrence, true);
                        repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), now);
                        created.add(SlideResult.of(slide, false));
                    }
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("其他技术员已生成相同玻片，请刷新后继续");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), created.size());
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", caseId, "V2-CASE",
                UUID.randomUUID().toString(), "按常规规则生成玻片 " + created.size() + " 张");
        return new SlideBatchGenerationResult(created.size(), created, false);
    }

    @Transactional
    public SlideResult createExtraSlide(UUID blockId, CreateExtraSlideCommand command) {
        validate(blockId, "材块内部ID不能为空");
        validate(command.reason(), "额外制片原因不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Block block = findBlock(blockId, actor);
        activeCase(block.caseId(), actor);
        BusinessTypeCapability capability = capabilityService.forCase(block.caseId(), actor.hospitalScope());
        if (!capability.usesHistologyProcessing() || block.isDeleted()) {
            throw conflict("当前材块不能额外生成常规玻片");
        }
        UUID grossingId = repository.findCompletedInitialGrossingId(block.caseId(), actor.hospitalScope())
                .orElseThrow(() -> conflict("首次取材尚未完成"));
        String operation = "PIS-V2-FC03A-ROUTINE-SLIDE-EXTRA";
        String digest = digest(blockId, command.slideType(), command.reason());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) return SlideResult.replayed(findSlide(existing, actor));
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", UUID.randomUUID(), actor);
        if (!repository.lockActiveCase(block.caseId(), actor.hospitalScope())) {
            throw conflict("病例已取消");
        }
        int occurrence = repository.nextSlideOccurrence(block.id(), Slide.INITIAL, actor.hospitalScope());
        String slideType = command.slideType() == null || command.slideType().isBlank() ? "HE"
                : command.slideType().trim().toUpperCase();
        String code = block.blockCode() + "-" + slideType + "-X" + occurrence;
        UUID slideId = repository.findMaterialIdempotency(operation, command.idempotencyKey())
                .map(MaterialIdempotencyResult::resultEntityId).orElseThrow();
        Slide slide = Slide.initialFromBlock(slideId, block.caseId(), block.id(), code, slideType, grossingId,
                "MANUAL-EXTRA-" + slideType, occurrence, false);
        try {
            repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), Instant.now());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("玻片编号 " + code + " 已存在");
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
                UUID.randomUUID().toString(), command.reason());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideResult completeSlide(UUID slideId, CompleteSlideCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(slideId, "切片内部ID不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        String operation = "PIS-V2-I02-SLIDE-COMPLETE";
        String digest = digest(slideId, command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return SlideResult.of(findSlide(existing, actor), true);
        }
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", slideId, actor);
        Slide slide = findSlide(slideId, actor);
        requireVersion(slide.concurrencyVersion(), command.expectedVersion());
        if (slide.isDeleted()) {
            throw reject("V2-SLIDE-DELETED", "已失效切片不能完成");
        }
        Instant now = Instant.now();
        slide.complete(actor.actorId(), now);
        if (slide.concurrencyVersion() != command.expectedVersion()) {
            persistSlide(slide, actor, command.expectedVersion());
        }
        repository.resolveReworkSourceExceptions(slide.id(), actor.hospitalScope(), actor.actorId(), now);
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slideId, "V2-SLIDE",
                UUID.randomUUID().toString(), "V2切片已完成");
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideBatchResult completeSlides(CompleteSlidesCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(command.idempotencyKey(), "幂等键不能为空");
        if (command.slides() == null || command.slides().isEmpty()) {
            throw reject("V2-SLIDE-BATCH-EMPTY", "批量完成至少需要一张切片");
        }
        List<SlideCompletion> completions = command.slides().stream()
                .sorted(Comparator.comparing(value -> value.slideId().toString())).toList();
        String operation = "PIS-V2-I02-SLIDE-BATCH-COMPLETE";
        String digest = digest((Object[]) completions.stream()
                .map(value -> value.slideId() + ":" + value.expectedVersion()).toArray(String[]::new));
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return new SlideBatchResult(existing.resultCount() == null ? 0 : existing.resultCount(), true);
        }
        UUID resultId = completions.get(0).slideId();
        reserve(operation, command.idempotencyKey(), digest, "SLIDE_BATCH", resultId, actor);
        int changed = 0;
        Instant now = Instant.now();
        for (SlideCompletion completion : completions) {
            Slide slide = findSlide(completion.slideId(), actor);
            requireVersion(slide.concurrencyVersion(), completion.expectedVersion());
            if (slide.isDeleted()) {
                throw reject("V2-SLIDE-DELETED", "批量中包含已失效切片");
            }
            if (!slide.isCompleted()) {
                slide.complete(actor.actorId(), now);
                persistSlide(slide, actor, completion.expectedVersion());
                repository.resolveReworkSourceExceptions(slide.id(), actor.hospitalScope(), actor.actorId(), now);
                changed++;
            }
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), changed);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", resultId, "V2-SLIDE-BATCH",
                UUID.randomUUID().toString(), "V2切片批量完成");
        return new SlideBatchResult(changed, false);
    }

    @Transactional
    public SlideResult correctSlideCode(UUID slideId, CorrectSlideCodeCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(command.newSlideCode(), "新玻片编号不能为空");
        validate(command.reason(), "玻片编号更正原因不能为空");
        Slide slide = findSlide(slideId, actor);
        activeCase(slide.caseId(), actor);
        requireVersion(slide.concurrencyVersion(), command.expectedVersion());
        String newCode = command.newSlideCode().trim();
        UUID duplicate = repository.findActiveSlideIdByCode(slide.caseId(), newCode, actor.hospitalScope()).orElse(null);
        if (duplicate != null && !duplicate.equals(slide.id())) {
            throw conflict("玻片编号 " + newCode + " 已存在");
        }
        String oldCode = slide.slideCode();
        if (oldCode.equals(newCode)) throw conflict("新玻片编号与当前编号相同");
        slide.renameCode(newCode);
        Instant now = Instant.now();
        try {
            persistSlide(slide, actor, command.expectedVersion());
        } catch (DataIntegrityViolationException exception) {
            throw conflict("玻片编号 " + newCode + " 已存在");
        }
        repository.insertSlideCodeHistory(slide.id(), oldCode, newCode, command.reason(), actor.hospitalScope(),
                actor.actorId(), now);
        audit.appendWithChanges("PIS-V2-FC03A-SLIDE-CODE-CORRECT", MATERIAL_PERMISSION, actor, "COMPLETED",
                slide.id(), "V2-SLIDE", UUID.randomUUID().toString(), command.reason(),
                List.of(new AuditChange("slideCode", "玻片编号", oldCode, newCode)));
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideResult cancelSlide(UUID slideId, SoftDeleteCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(command.reason(), "玻片失效原因不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Slide slide = findSlide(slideId, actor);
        activeCase(slide.caseId(), actor);
        requireVersion(slide.concurrencyVersion(), command.expectedVersion());
        if (repository.hasSlideDownstreamDependency(slideId)) {
            throw conflict("玻片已有数字切片、医嘱、归档或借阅关联，只能保留历史，不能失效");
        }
        String operation = "PIS-V2-FC03A-SLIDE-CANCEL";
        String digest = digest(slideId, command.expectedVersion(), command.reason());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) return SlideResult.of(findSlide(slideId, actor), true);
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", slideId, actor);
        Instant now = Instant.now();
        slide.softDelete(command.reason(), now);
        if (!repository.softDeleteSlide(slideId, actor.hospitalScope(), command.expectedVersion(), command.reason(),
                actor.actorId(), now)) throw conflict("玻片已被其他用户修改，请刷新后重试");
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slideId, "V2-SLIDE",
                UUID.randomUUID().toString(), command.reason());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideResult correctSlideCompletion(UUID slideId, CorrectSlideCompletionCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(command.reason(), "完成记录修正原因不能为空");
        Slide slide = findSlide(slideId, actor);
        activeCase(slide.caseId(), actor);
        requireVersion(slide.concurrencyVersion(), command.expectedVersion());
        Instant priorAt = slide.completedAt();
        String priorBy = slide.completedBy();
        try {
            slide.correctCompletion();
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        Instant now = Instant.now();
        persistSlide(slide, actor, command.expectedVersion());
        repository.insertSlideCompletionCorrection(slideId, priorAt, priorBy, command.reason(),
                actor.hospitalScope(), actor.actorId(), now);
        audit.append("PIS-V2-FC03A-SLIDE-COMPLETION-CORRECT", MATERIAL_PERMISSION, actor, "ALLOWED",
                "COMPLETED", slideId, "V2-SLIDE", UUID.randomUUID().toString(), command.reason());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public PrintResult printBlock(UUID blockId, PrintCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(command.idempotencyKey(), "幂等键不能为空");
        Block block = findBlock(blockId, actor);
        if (block.isDeleted()) {
            throw reject("V2-BLOCK-DELETED", "Deleted block cannot be printed");
        }
        String operation = "PIS-V2-I02-BLOCK-PRINT";
        String digest = digest(blockId, command.reason());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return new PrintResult(existing.resultEntityId(), true, "REPLAYED");
        }
        reserve(operation, command.idempotencyKey(), digest, "PRINT", blockId, actor);
        PrintRule rule = repository.findPrintRule(actor.hospitalScope(),
                repository.findCaseBusinessTypeId(block.caseId(), actor.hospitalScope()).orElse(null), "BLOCK", "MANUAL")
                .orElse(new PrintRule(UUID.randomUUID(), null, "BLOCK", "MANUAL", "MOCK://SYNTH-PRINTER", true));
        PrintResult result = recordPrint(block.caseId(), "BLOCK", block.id(), block.blockCode(), rule, actor,
                Instant.now());
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        return result;
    }

    @Transactional
    public PrintResult printSlide(UUID slideId, PrintCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(command.idempotencyKey(), "幂等键不能为空");
        Slide slide = findSlide(slideId, actor);
        if (slide.isDeleted()) {
            throw reject("V2-SLIDE-DELETED", "Deleted slide cannot be printed");
        }
        String operation = "PIS-V2-I02-SLIDE-PRINT";
        String digest = digest(slideId, command.reason());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return new PrintResult(existing.resultEntityId(), true, "REPLAYED");
        }
        reserve(operation, command.idempotencyKey(), digest, "PRINT", slideId, actor);
        PrintRule rule = repository.findPrintRule(actor.hospitalScope(),
                repository.findCaseBusinessTypeId(slide.caseId(), actor.hospitalScope()).orElse(null), "SLIDE", "MANUAL")
                .orElse(new PrintRule(UUID.randomUUID(), null, "SLIDE", "MANUAL", "MOCK://SYNTH-PRINTER", true));
        PrintResult result = recordPrint(slide.caseId(), "SLIDE", slide.id(), slide.slideCode(), rule, actor,
                Instant.now());
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        return result;
    }

    @Transactional
    public PrintBatchResult printSlides(PrintSlidesCommand command) {
        if (command.slideIds() == null || command.slideIds().isEmpty()) {
            throw badRequest("请选择至少一张待打印玻片");
        }
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        List<Slide> slides = command.slideIds().stream().distinct()
                .map(id -> findSlide(id, actor))
                .sorted(Comparator.comparing(Slide::slideCode).thenComparing(Slide::id)).toList();
        List<PrintResult> results = new ArrayList<>();
        int index = 0;
        for (Slide slide : slides) {
            results.add(printSlide(slide.id(), new PrintCommand(command.reason(),
                    command.idempotencyKey() + "/" + index++)));
        }
        return new PrintBatchResult(results);
    }

    @Transactional(readOnly = true)
    public MaterialTreeResult materialTree(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        Case pathologyCase = findCaseScoped(caseId, actor);
        List<MaterialTreeRow> rows = repository.findMaterialTree(caseId, actor.hospitalScope());
        Map<UUID, SpecimenNodeBuilder> specimens = new LinkedHashMap<>();
        for (MaterialTreeRow row : rows) {
            SpecimenNodeBuilder specimen = specimens.computeIfAbsent(row.specimenId(), ignored ->
                    new SpecimenNodeBuilder(row.specimenId(), row.specimenNo(), row.specimenCode(),
                            row.specimenName(), row.specimenKindCode(), row.creationSourceCode(),
                            row.collectionSite(), row.collectionMethodCode(), row.specimenDescription(), row.sourceSpecimenCode(),
                            row.preparationMethodCode(), row.specimenConcurrencyVersion()));
            if (row.blockId() != null) {
                BlockNodeBuilder block = specimen.blocks.computeIfAbsent(row.blockId(), ignored ->
                    new BlockNodeBuilder(row.blockId(), row.blockCode(), row.blockType(),
                                row.samplingDescription(), row.blockNote(),
                                row.blockConcurrencyVersion() == null ? 0L : row.blockConcurrencyVersion(),
                                row.blockPrintCount() == null ? 0 : row.blockPrintCount(),
                                repository.latestBlockVerification(row.blockId(), actor.hospitalScope())
                                        .map(JdbcV2MaterialRepository.BlockVerificationFact::resultCode)
                                        .orElse("UNVERIFIED")));
                if (row.slideId() != null) {
                    block.slides.add(new SlideNode(row.slideId(), row.slideCode(), row.slideType(), row.stainCode(),
                            row.sourceContextType(), row.completedAt(), row.completedAt() != null, row.required(),
                            row.concurrencyVersion(), row.slidePrintCount()));
                }
            } else if (row.slideId() != null) {
                specimen.directSlides.add(new SlideNode(row.slideId(), row.slideCode(), row.slideType(), row.stainCode(),
                        row.sourceContextType(), row.completedAt(), row.completedAt() != null, row.required(),
                        row.concurrencyVersion(), row.slidePrintCount()));
            }
        }
        List<SpecimenNode> specimenNodes = specimens.values().stream().map(SpecimenNodeBuilder::build).toList();
        int required = 0;
        int completed = 0;
        for (SpecimenNode specimen : specimenNodes) {
            for (BlockNode block : specimen.blocks()) {
                for (SlideNode slide : block.slides()) {
                    if (slide.required()) { required++; if (slide.completed()) completed++; }
                }
            }
            for (SlideNode slide : specimen.directSlides()) {
                if (slide.required()) { required++; if (slide.completed()) completed++; }
            }
        }
        BusinessTypeCapability capability = capabilityService.forBusinessType(pathologyCase.businessTypeCode());
        if (capability.usesHistologyProcessing()) {
            UUID businessTypeId = repository.findCaseBusinessTypeId(caseId, actor.hospitalScope()).orElseThrow();
            int perBlock = repository.findSlideRules(actor.hospitalScope(), businessTypeId, Slide.INITIAL,
                    "ON_GROSSING_COMPLETE").stream().mapToInt(SlideRule::copies).sum();
            Set<UUID> activeInitialBlockIds = repository.findActiveInitialBlocksByCase(caseId, actor.hospitalScope())
                    .stream().map(Block::id).collect(Collectors.toSet());
            required = activeInitialBlockIds.size() * perBlock;
            completed = (int) repository.findActiveSlidesByCase(caseId, actor.hospitalScope()).stream()
                    .filter(slide -> Slide.INITIAL.equals(slide.sourceContextType()) && slide.required()
                    && activeInitialBlockIds.contains(slide.blockId()) && slide.isCompleted()).count();
        } else if (capability.supportsDirectSlides()) {
            UUID businessTypeId = repository.findCaseBusinessTypeId(caseId, actor.hospitalScope()).orElseThrow();
            int perSpecimen = repository.findSlideRules(actor.hospitalScope(), businessTypeId, Slide.CYTOLOGY,
                    "MANUAL").stream().mapToInt(SlideRule::copies).sum();
            if (perSpecimen == 0) perSpecimen = 1;
            required = specimenNodes.size() * perSpecimen;
            Set<UUID> activeSpecimenIds = specimenNodes.stream().map(SpecimenNode::specimenId)
                    .collect(Collectors.toSet());
            completed = (int) repository.findActiveSlidesByCase(caseId, actor.hospitalScope()).stream()
                    .filter(slide -> Slide.CYTOLOGY.equals(slide.sourceContextType()) && slide.required()
                            && activeSpecimenIds.contains(slide.specimenId()) && slide.isCompleted()).count();
        }
        List<String> availableActions = authorization.decide(MATERIAL_PERMISSION).allowed()
                ? (capability.supportsDirectSlides()
                        ? List.of("GENERATE_CYTOLOGY_SLIDES", "CREATE_EXTRA_CYTOLOGY_SLIDE", "PRINT_SLIDE",
                                "COMPLETE_SLIDE", "CORRECT_SLIDE_CODE", "CORRECT_SLIDE_COMPLETION", "CANCEL_SLIDE",
                                "SCAN_MATERIAL", "RECORD_TECHNICAL_TRACE", "RECORD_PRODUCTION_EXCEPTION",
                                "PERFORM_REWORK")
                        : List.of("GENERATE_REQUIRED_SLIDES", "CREATE_EXTRA_SLIDE", "PRINT_SLIDE", "COMPLETE_SLIDE",
                                "CORRECT_SLIDE_CODE", "CORRECT_SLIDE_COMPLETION", "CANCEL_SLIDE", "SCAN_MATERIAL",
                                "RECORD_TECHNICAL_TRACE", "RECORD_PRODUCTION_EXCEPTION", "PERFORM_REWORK"))
                : List.of();
        return new MaterialTreeResult(pathologyCase.id(), pathologyCase.caseNo(), pathologyCase.businessTypeCode(),
                capability, specimenNodes, required, completed, required > 0 && required == completed,
                availableActions);
    }

    @Transactional(readOnly = true)
    public MaterialLocateResult locateMaterial(UUID caseId, String barcode) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(barcode, "条码不能为空");
        activeCase(caseId, actor);
        var located = repository.locateMaterial(caseId, barcode.trim(), actor.hospitalScope())
                .orElseThrow(() -> new P15BusinessException("V2-MATERIAL-BARCODE-NOT-FOUND",
                        "未找到该材块或玻片", 404));
        if (!located.caseId().equals(caseId)) {
            throw new P15BusinessException("V2-MATERIAL-WRONG-CASE", "该材料不属于当前病例", 409);
        }
        return new MaterialLocateResult(located.materialKind(), located.materialId(), located.businessCode());
    }

    @Transactional(readOnly = true)
    public GrossingWorkspaceResult grossingWorkspace(UUID caseId, String sourceType, UUID sourceReferenceId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        validate(sourceType, "取材来源类型不能为空");
        Case pathologyCase = activeCase(caseId, actor);
        MaterialTreeResult materials = materialTree(caseId);
        List<SpecimenNode> workspaceSpecimens = materials.specimens();
        if (Grossing.FROZEN_CONTEXT.equals(sourceType)) {
            validate(sourceReferenceId, "冰冻取材必须指定轮次");
            var roundSpecimenIds = repository.findFrozenRoundSpecimenIds(sourceReferenceId, caseId,
                    actor.hospitalScope());
            workspaceSpecimens = materials.specimens().stream()
                    .filter(specimen -> roundSpecimenIds.contains(specimen.specimenId()))
                    .toList();
        } else if (Grossing.TECHNICAL_ORDER.equals(sourceType)) {
            validate(sourceReferenceId, "补充取材必须指定技术医嘱项目");
            if (!repository.isSupplementaryGrossingItem(sourceReferenceId, caseId, actor.hospitalScope())) {
                throw new P15BusinessException("V2-SUPPLEMENTARY-GROSSING-SOURCE-INVALID",
                        "技术医嘱不存在、类型不符或不在当前病例范围", 404);
            }
            var targetScope = repository.supplementaryTargetScope(sourceReferenceId, caseId,
                    actor.hospitalScope());
            if (!targetScope.caseTarget() && !targetScope.specimenIds().isEmpty()) {
                workspaceSpecimens = materials.specimens().stream()
                        .filter(specimen -> targetScope.specimenIds().contains(specimen.specimenId()))
                        .toList();
            }
        }
        GrossingWorkspaceRecord grossing = repository.findLatestActiveGrossing(caseId, sourceType,
                        sourceReferenceId, actor.hospitalScope())
                .map(GrossingWorkspaceRecord::from)
                .orElse(null);
        if (grossing != null) {
            Map<UUID, JdbcV2MaterialRepository.GrossingSpecimenFact> facts = repository
                    .findGrossingSpecimens(grossing.grossingId()).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            JdbcV2MaterialRepository.GrossingSpecimenFact::specimenId, fact -> fact));
            workspaceSpecimens = workspaceSpecimens.stream().map(specimen -> {
                var fact = facts.get(specimen.specimenId());
                return fact == null ? specimen : specimen.withGrossDescription(fact.materialDescription(),
                        fact.concurrencyVersion());
            }).toList();
        }
        List<String> availableActions = new ArrayList<>();
        if (actor.permissions().contains(RECEIVING_PERMISSION)) {
            availableActions.addAll(List.of("SPECIMEN_ADD", "SPECIMEN_UPDATE", "SPECIMEN_SPLIT"));
        }
        if (actor.permissions().contains(SPECIMEN_CANCEL_PERMISSION)) {
            availableActions.add("SPECIMEN_CANCEL");
        }
        if (actor.permissions().contains(GROSSING_PERMISSION)) {
            availableActions.addAll(List.of("GROSSING_START", "GROSSING_UPDATE", "GROSSING_CORRECT",
                    "GROSSING_COMPLETE", "GROSS_IMAGE_CAPTURE", "GROSS_IMAGE_ANNOTATE", "GROSS_IMAGE_MEASURE"));
        }
        if (actor.permissions().contains(MATERIAL_PERMISSION)) {
            availableActions.addAll(List.of("BLOCK_CREATE", "BLOCK_UPDATE", "BLOCK_CANCEL", "BLOCK_PRINT",
                    "BLOCK_VERIFY"));
        }
        return new GrossingWorkspaceResult(pathologyCase.id(), pathologyCase.caseNo(),
                pathologyCase.businessTypeCode(), pathologyCase.patientReference(), pathologyCase.visitReference(),
                pathologyCase.externalApplicationId(), workspaceSpecimens, grossing, availableActions,
                repository.findBlockVerificationPolicy(caseId, actor.hospitalScope()));
    }

    private PrintResult recordPrint(UUID caseId, String entityKind, UUID entityId, String businessCode,
            PrintRule rule, ActorContext actor, Instant now) {
        LabelPrintService.PrintResult serviceResult = labelPrintService.print(new LabelPrintService.PrintRequest(
                entityKind, entityId, businessCode, rule.printerProfileCode(), businessCode, actor.actorId()));
        repository.insertPrintLog(caseId, entityKind, entityId, businessCode, rule.printerProfileCode(), actor.actorId(),
                now, new JdbcV2MaterialRepository.PrintServiceResult(serviceResult.resultCode(),
                        serviceResult.failureReason()));
        audit.append("PIS-V2-I02-PRINT", MATERIAL_PERMISSION, actor, "ALLOWED", serviceResult.resultCode(), entityId,
                "V2-" + entityKind, UUID.randomUUID().toString(), serviceResult.failureReason() == null
                        ? "打印请求已记录" : serviceResult.failureReason());
        return new PrintResult(entityId, false, serviceResult.resultCode());
    }

    private void renameRelatedSlides(Block block, ActorContext actor) {
        UUID businessTypeId = repository.findCaseBusinessTypeId(block.caseId(), actor.hospitalScope()).orElse(null);
        List<SlideRule> rules = businessTypeId == null ? List.of()
                : repository.findSlideRules(actor.hospitalScope(), businessTypeId, Slide.INITIAL,
                        "ON_GROSSING_COMPLETE");
        for (Slide slide : repository.findActiveSlidesByBlock(block.id(), actor.hospitalScope())) {
            rules.stream().filter(rule -> rule.ruleCode().equals(slide.ruleCode())).findFirst().ifPresent(rule -> {
                String code = rule.slideCode(block.blockCode(), slide.occurrenceNo());
                if (!code.equals(slide.slideCode())) {
                    long expectedVersion = slide.concurrencyVersion();
                    slide.renameCode(code);
                    persistSlide(slide, actor, expectedVersion);
                }
            });
        }
    }

    private void persistGrossing(Grossing grossing, ActorContext actor, long expectedVersion) {
        if (!repository.saveGrossing(grossing, actor.hospitalScope(), expectedVersion, actor.actorId(), Instant.now())) {
            throw conflict("取材版本冲突，请重新读取后重试");
        }
    }

    private void persistBlock(Block block, ActorContext actor, long expectedVersion) {
        if (!repository.saveBlock(block, actor.hospitalScope(), expectedVersion, actor.actorId(), Instant.now())) {
            throw conflict("材块版本冲突，请重新读取后重试");
        }
    }

    private void persistSlide(Slide slide, ActorContext actor, long expectedVersion) {
        if (!repository.saveSlide(slide, actor.hospitalScope(), expectedVersion, actor.actorId(), Instant.now())) {
            throw conflict("切片版本冲突，请重新读取后重试");
        }
    }

    private Grossing findGrossing(MaterialIdempotencyResult existing, ActorContext actor) {
        if (existing.resultEntityId() == null) {
            throw reject("V2-IDEMPOTENCY-INVALID", "幂等结果缺少取材身份");
        }
        return findGrossing(existing.resultEntityId(), actor);
    }

    private Block findBlock(MaterialIdempotencyResult existing, ActorContext actor) {
        if (existing.resultEntityId() == null) {
            throw reject("V2-IDEMPOTENCY-INVALID", "幂等结果缺少蜡块身份");
        }
        return findBlock(existing.resultEntityId(), actor);
    }

    private Slide findSlide(MaterialIdempotencyResult existing, ActorContext actor) {
        if (existing.resultEntityId() == null) {
            throw reject("V2-IDEMPOTENCY-INVALID", "幂等结果缺少切片身份");
        }
        return findSlide(existing.resultEntityId(), actor);
    }

    private Grossing findGrossing(UUID id, ActorContext actor) {
        return repository.findGrossing(id, actor.hospitalScope())
                .orElseThrow(() -> notFound("取材不存在或不在当前数据范围"));
    }

    private Block findBlock(UUID id, ActorContext actor) {
        return repository.findBlock(id, actor.hospitalScope())
                .orElseThrow(() -> notFound("蜡块不存在或不在当前数据范围"));
    }

    private Slide findSlide(UUID id, ActorContext actor) {
        return repository.findSlide(id, actor.hospitalScope())
                .orElseThrow(() -> notFound("切片不存在或不在当前数据范围"));
    }

    private Case activeCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = findCaseScoped(caseId, actor);
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能开展材料生产");
        }
        return pathologyCase;
    }

    private Case findCaseScoped(UUID caseId, ActorContext actor) {
        return registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> new P15BusinessException("V2-SOURCE-NOT-FOUND",
                        "病例不存在或不在当前数据范围", 404));
    }

    private MaterialAuthorization authorizeCaseScoped(UUID caseId, String requiredPermission,
            boolean allowReferralReceiving) {
        var direct = authorization.decide(requiredPermission);
        if (direct.allowed()) {
            return new MaterialAuthorization(direct.actor(), requiredPermission);
        }
        ActorContext receivingActor = authorization.require(RECEIVING_PERMISSION);
        Case pathologyCase = activeCase(caseId, receivingActor);
        if (!allowReferralReceiving || !"REFERRAL".equals(pathologyCase.businessTypeCode())) {
            throw new P15BusinessException("P12-ERR-075", "当前身份无权处理该病例的材料", 403);
        }
        return new MaterialAuthorization(receivingActor, RECEIVING_PERMISSION);
    }

    private MaterialAuthorization authorizeGrossingScoped(UUID grossingId, String requiredPermission) {
        var direct = authorization.decide(requiredPermission);
        if (direct.allowed()) {
            return new MaterialAuthorization(direct.actor(), requiredPermission);
        }
        ActorContext receivingActor = authorization.require(RECEIVING_PERMISSION);
        Grossing grossing = findGrossing(grossingId, receivingActor);
        return authorizeCaseScoped(grossing.caseId(), requiredPermission, true);
    }

    private MaterialIdempotencyResult existing(String operation, String key, String digest) {
        MaterialIdempotencyResult existing = repository.findMaterialIdempotency(operation, key).orElse(null);
        if (existing != null && !existing.payloadDigest().equals(digest)) {
            throw conflict("相同幂等键对应的材料命令摘要冲突");
        }
        return existing;
    }

    private void reserve(String operation, String key, String digest, String kind, UUID entityId, ActorContext actor) {
        if (!repository.insertMaterialIdempotency(operation, key, digest, kind, entityId, null, actor.actorId(),
                Instant.now())) {
            MaterialIdempotencyResult existing = repository.findMaterialIdempotency(operation, key)
                    .orElseThrow(() -> reject("V2-IDEMPOTENCY-REPLAY-ERROR", "材料命令幂等记录不可读"));
            if (!existing.payloadDigest().equals(digest)) {
                throw conflict("材料命令摘要冲突");
            }
            throw conflict("材料命令正在由其他请求处理，请重试");
        }
    }

    private UUID existingReservedId(String operation, String key) {
        return repository.findMaterialIdempotency(operation, key).map(MaterialIdempotencyResult::resultEntityId)
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "材料命令保留记录不可读"));
    }

    private static void requireEditable(Grossing grossing) {
        if (!grossing.isEditable()) {
            throw conflict("已完成取材只能执行有原因的修正；新增材料请从补取医嘱建立新取材");
        }
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw conflict("版本冲突，请重新读取后重试");
        }
    }

    private static void validate(Object value, String message) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw reject("V2-INVALID-REQUEST", message);
        }
    }

    private static String nonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    private static P15BusinessException notFound(String message) {
        return new P15BusinessException("V2-SOURCE-NOT-FOUND", message, 404);
    }

    private static P15BusinessException badRequest(String message) {
        return new P15BusinessException("V2-INVALID-REQUEST", message, 400);
    }

    private static P15BusinessException conflict(String message) {
        return new P15BusinessException("V2-BUSINESS-CONFLICT", message, 409);
    }

    private static String digest(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private static GrossingCompletionResult completion(Grossing grossing, int createdSlides, boolean duplicate,
            ActorContext actor) {
        return new GrossingCompletionResult(grossing.id(), grossing.grossingNo(), grossing.completedAt(),
                createdSlides, duplicate, "COMPLETED");
    }

    private record SpecimenNodeBuilder(UUID id, String specimenNo, String specimenCode, String specimenName,
            String specimenKindCode, String creationSourceCode, String collectionSite, String collectionMethodCode,
            String specimenDescription,
            String sourceSpecimenCode, String preparationMethodCode, Long specimenConcurrencyVersion,
            Map<UUID, BlockNodeBuilder> blocks, List<SlideNode> directSlides) {
        private SpecimenNodeBuilder(UUID id, String specimenNo, String specimenCode, String specimenName,
                String specimenKindCode, String creationSourceCode, String collectionSite,
                String collectionMethodCode, String specimenDescription, String sourceSpecimenCode,
                String preparationMethodCode, Long specimenConcurrencyVersion) {
            this(id, specimenNo, specimenCode, specimenName, specimenKindCode, creationSourceCode, collectionSite,
                    collectionMethodCode, specimenDescription, sourceSpecimenCode, preparationMethodCode,
                    specimenConcurrencyVersion,
                    new LinkedHashMap<>(), new ArrayList<>());
        }

        private SpecimenNode build() {
            return new SpecimenNode(id, specimenNo, specimenCode, specimenName, specimenKindCode,
                    creationSourceCode, collectionSite, collectionMethodCode, specimenDescription, sourceSpecimenCode,
                    preparationMethodCode, specimenConcurrencyVersion == null ? 0L : specimenConcurrencyVersion,
                    null, 0,
                    blocks.values().stream().map(BlockNodeBuilder::build).toList(), directSlides);
        }
    }

    private record BlockNodeBuilder(UUID id, String blockCode, String blockType, String samplingDescription,
            String note, long concurrencyVersion, int printCount, String verificationStatus,
            List<SlideNode> slides) {
        private BlockNodeBuilder(UUID id, String blockCode, String blockType, String samplingDescription, String note,
                long concurrencyVersion, int printCount, String verificationStatus) {
            this(id, blockCode, blockType, samplingDescription, note, concurrencyVersion, printCount,
                    verificationStatus, new ArrayList<>());
        }

        private BlockNode build() {
            return new BlockNode(id, blockCode, blockType, samplingDescription, note, concurrencyVersion, printCount,
                    verificationStatus, slides);
        }
    }

    private record MaterialAuthorization(ActorContext actor, String permissionCode) { }

    public record CreateGrossingCommand(UUID caseId, String sourceType, UUID sourceReferenceId,
            String grossDescription, String grossingInstruction, String grossingDoctorId, String recorderId,
            String idempotencyKey) { }

    public record UpdateGrossingCommand(String grossDescription, String grossingInstruction, String grossingDoctorId,
            String recorderId, long expectedVersion, String idempotencyKey) { }

    public record AssociateSpecimenCommand(UUID specimenId, String materialDescription, String idempotencyKey) { }

    public record UpdateGrossingSpecimenCommand(UUID specimenId, String materialDescription, long expectedVersion,
            String reason) {
        public UpdateGrossingSpecimenCommand(UUID specimenId, String materialDescription, long expectedVersion) {
            this(specimenId, materialDescription, expectedVersion, null);
        }
    }

    public record CorrectGrossingCommand(String grossDescription, String grossingInstruction,
            String grossingDoctorId, String recorderId, String reason, long expectedVersion) { }

    public record CreateBlockCommand(UUID specimenId, String blockCode, String blockType,
            String samplingDescription, String note, String idempotencyKey, boolean externalSource,
            String externalSourceReference) {
        public CreateBlockCommand(UUID specimenId, String blockCode, String blockType, String idempotencyKey) {
            this(specimenId, blockCode, blockType, null, null, idempotencyKey, false, null);
        }

        public CreateBlockCommand(UUID specimenId, String blockCode, String blockType, String idempotencyKey,
                boolean externalSource, String externalSourceReference) {
            this(specimenId, blockCode, blockType, null, null, idempotencyKey, externalSource,
                    externalSourceReference);
        }
    }

    public record CreateBlockItem(UUID specimenId, String blockCode, String blockType, String samplingDescription,
            String note) { }

    public record CreateBlocksCommand(List<CreateBlockItem> blocks, String idempotencyKey) { }

    public record CreateDirectSlideCommand(String slideCode, String slideType, String idempotencyKey,
            String stainCode) {
        public CreateDirectSlideCommand(String slideCode, String slideType, String idempotencyKey) {
            this(slideCode, slideType, idempotencyKey, null);
        }
    }

    public record GenerateRequiredCytologySlidesCommand(List<UUID> specimenIds, String slideType,
            String stainCode, String idempotencyKey) { }

    public record CreateExtraCytologySlideCommand(String slideType, String stainCode, String reason,
            String idempotencyKey) { }

    public record UpdateCytologyPreparationCommand(String preparationMethodCode, long expectedVersion) { }

    public record UpdateBlockCommand(String blockCode, String blockType, String samplingDescription, String note,
            String reason, long expectedVersion, String idempotencyKey) {
        public UpdateBlockCommand(String blockCode, String blockType, long expectedVersion, String idempotencyKey) {
            this(blockCode, blockType, null, null, null, expectedVersion, idempotencyKey);
        }
    }

    public record VerifyBlockCommand(String verifiedCode, UUID verifiedSpecimenId, int verifiedQuantity,
            String reason) { }

    public record CompleteGrossingCommand(long expectedVersion, String idempotencyKey) { }

    public record ReopenGrossingCommand(long expectedVersion, String reason, String idempotencyKey) { }

    public record SoftDeleteCommand(long expectedVersion, String reason, String idempotencyKey) { }

    public record CompleteSlideCommand(long expectedVersion, String idempotencyKey) { }

    public record SlideCompletion(UUID slideId, long expectedVersion) { }

    public record CompleteSlidesCommand(List<SlideCompletion> slides, String idempotencyKey) { }

    public record GenerateRequiredSlidesCommand(List<UUID> blockIds, String idempotencyKey) { }

    public record CreateExtraSlideCommand(String slideType, String reason, String idempotencyKey) { }

    public record CorrectSlideCodeCommand(String newSlideCode, String reason, long expectedVersion) { }

    public record CorrectSlideCompletionCommand(String reason, long expectedVersion) { }

    public record PrintCommand(String reason, String idempotencyKey) { }

    public record PrintBlocksCommand(List<UUID> blockIds, String reason, String idempotencyKey) { }

    public record PrintSlidesCommand(List<UUID> slideIds, String reason, String idempotencyKey) { }

    public record GrossingResult(UUID grossingId, String grossingNo, UUID caseId, String sourceType,
            Instant completedAt, long concurrencyVersion, boolean duplicate, int affectedCount, boolean reopened) {
        static GrossingResult of(Grossing grossing, boolean duplicate, int affectedCount, boolean reopened) {
            return new GrossingResult(grossing.id(), grossing.grossingNo(), grossing.caseId(), grossing.sourceType(),
                    grossing.completedAt(), grossing.concurrencyVersion(), duplicate, affectedCount, reopened);
        }

        static GrossingResult replayed(Grossing grossing) { return of(grossing, true, 0, false); }
    }

    public record BlockResult(UUID blockId, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType, String samplingDescription, String note, Instant deletedAt, long concurrencyVersion,
            boolean duplicate) {
        static BlockResult of(Block block, boolean duplicate) {
            return new BlockResult(block.id(), block.caseId(), block.grossingId(), block.specimenId(), block.blockCode(),
                    block.blockType(), block.samplingDescription(), block.note(), block.deletedAt(),
                    block.concurrencyVersion(), duplicate);
        }

        static BlockResult replayed(Block block) { return of(block, true); }
    }

    public record SlideResult(UUID slideId, UUID caseId, UUID blockId, UUID specimenId, String slideCode,
            String slideType, String stainCode, String sourceContextType, Instant completedAt,
            long concurrencyVersion, boolean duplicate) {
        static SlideResult of(Slide slide, boolean duplicate) {
            return new SlideResult(slide.id(), slide.caseId(), slide.blockId(), slide.specimenId(), slide.slideCode(),
                    slide.slideType(), slide.stainCode(), slide.sourceContextType(), slide.completedAt(),
                    slide.concurrencyVersion(), duplicate);
        }

        static SlideResult replayed(Slide slide) { return of(slide, true); }
    }

    public record GrossingCompletionResult(UUID grossingId, String grossingNo, Instant completedAt,
            int createdSlideCount, boolean duplicate, String eventTypeCode) { }

    public record SlideBatchResult(int changedCount, boolean duplicate) { }

    public record SlideBatchGenerationResult(int createdCount, List<SlideResult> slides, boolean duplicate) { }

    public record PrintResult(UUID entityId, boolean duplicate, String resultCode) { }

    public record PrintBatchResult(List<PrintResult> results) { }

    public record BlockBatchResult(List<BlockResult> blocks) { }

    public record BlockVerificationResult(UUID blockId, String resultCode, Instant verifiedAt,
            String verifiedByRef, String reason) { }

    public record MaterialTreeResult(UUID caseId, String caseNo, String businessTypeCode,
            BusinessTypeCapability capability,
            List<SpecimenNode> specimens, int initialRequiredCount, int initialCompletedCount,
            boolean initialProductionComplete, List<String> availableActions) { }

    public record MaterialLocateResult(String materialKind, UUID materialId, String businessCode) { }

    public record GrossingWorkspaceResult(UUID caseId, String caseNo, String businessTypeCode,
            String patientReference, String visitReference, String applicationNo, List<SpecimenNode> specimens,
            GrossingWorkspaceRecord grossing, List<String> availableActions,
            JdbcV2MaterialRepository.BlockVerificationPolicy verificationPolicy) { }

    public record GrossingWorkspaceRecord(UUID grossingId, String grossingNo, String sourceType,
            UUID sourceReferenceId, String grossDescription, String grossingInstruction, String grossingDoctorId,
            String recorderId, Instant startedAt, Instant completedAt, long concurrencyVersion) {
        static GrossingWorkspaceRecord from(Grossing grossing) {
            return new GrossingWorkspaceRecord(grossing.id(), grossing.grossingNo(), grossing.sourceType(),
                    grossing.sourceReferenceId(), grossing.grossDescription(), grossing.grossingInstruction(),
                    grossing.grossingDoctorId(), grossing.recorderId(), grossing.startedAt(), grossing.completedAt(),
                    grossing.concurrencyVersion());
        }
    }

    public record SpecimenNode(UUID specimenId, String specimenNo, String specimenCode, String specimenName,
            String specimenKindCode, String creationSourceCode, String collectionSite, String collectionMethodCode,
            String specimenDescription,
            String sourceSpecimenCode, String preparationMethodCode, long specimenConcurrencyVersion,
            String grossMaterialDescription, long grossSpecimenVersion,
            List<BlockNode> blocks, List<SlideNode> directSlides) {
        SpecimenNode withGrossDescription(String description, long version) {
            return new SpecimenNode(specimenId, specimenNo, specimenCode, specimenName, specimenKindCode,
                    creationSourceCode, collectionSite, collectionMethodCode, specimenDescription, sourceSpecimenCode, preparationMethodCode,
                    specimenConcurrencyVersion,
                    description,
                    version, blocks, directSlides);
        }
    }

    public record BlockNode(UUID blockId, String blockCode, String blockType, String samplingDescription, String note,
            long concurrencyVersion, int printCount, String verificationStatus, List<SlideNode> slides) { }

    public record SlideNode(UUID slideId, String slideCode, String slideType, String stainCode, String sourceContextType,
            Instant completedAt, boolean completed, boolean required, long concurrencyVersion, int printCount) { }

    public record SpecimenPreparationResult(UUID specimenId, String preparationMethodCode, long concurrencyVersion) { }
}
