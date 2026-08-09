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
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.integration.device.LabelPrintService;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
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

    private final JdbcV2MaterialRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final LabelPrintService labelPrintService;

    public V2MaterialProductionApplicationService(JdbcV2MaterialRepository repository,
            JdbcV2RegistrationRepository registrationRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox, LabelPrintService labelPrintService) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.labelPrintService = labelPrintService;
    }

    @Transactional
    public GrossingResult createGrossing(CreateGrossingCommand command) {
        ActorContext actor = authorization.require(GROSSING_PERMISSION);
        validate(command.caseId(), "病例内部ID不能为空");
        validate(command.grossDescription(), "取材描述不能为空");
        validate(command.grossingDoctorId(), "取材医生不能为空");
        validate(command.recorderId(), "取材记录人不能为空");
        validate(command.sourceType(), "取材来源类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Case pathologyCase = activeCase(command.caseId(), actor);
        String operation = "PIS-V2-I02-GROSSING-CREATE";
        String digest = digest(command.caseId(), command.sourceType(), command.sourceReferenceId(),
                command.grossDescription(), command.grossingInstruction(), command.grossingDoctorId(),
                command.recorderId());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return GrossingResult.replayed(findGrossing(existing, actor));
        }
        UUID grossingId = UUID.randomUUID();
        reserve(operation, command.idempotencyKey(), digest, "GROSSING", grossingId, actor);
        Instant now = Instant.now();
        Grossing grossing = Grossing.open(grossingId, pathologyCase.id(),
                repository.allocateGrossingNo(actor.hospitalScope(), pathologyCase.id()), command.sourceType(),
                command.sourceReferenceId(), command.grossDescription(), command.grossingInstruction(),
                command.grossingDoctorId(), command.recorderId(), now);
        repository.insertGrossing(grossing, actor.hospitalScope(), actor.actorId(), now);
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 0);
        audit.append(operation, GROSSING_PERMISSION, actor, "ALLOWED", "COMPLETED", grossing.id(), "V2-GROSSING",
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
        ActorContext actor = authorization.require(GROSSING_PERMISSION);
        validate(grossingId, "取材内部ID不能为空");
        validate(command.specimenId(), "标本内部ID不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
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
        if (!repository.hasGrossingSpecimen(grossingId, specimen.id())) {
            repository.insertGrossingSpecimen(grossingId, specimen.id(),
                    repository.nextGrossingSpecimenSequence(grossingId), command.materialDescription());
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, GROSSING_PERMISSION, actor, "ALLOWED", "COMPLETED", grossingId, "V2-GROSSING",
                UUID.randomUUID().toString(), "V2取材已关联标本");
        return GrossingResult.of(grossing, false, 1, false);
    }

    @Transactional
    public BlockResult createBlock(UUID grossingId, CreateBlockCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(grossingId, "取材内部ID不能为空");
        validate(command.specimenId(), "标本内部ID不能为空");
        validate(command.blockCode(), "蜡块编号不能为空");
        validate(command.blockType(), "蜡块类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
        Grossing grossing = findGrossing(grossingId, actor);
        String operation = "PIS-V2-I02-BLOCK-CREATE";
        String digest = digest(grossingId, command.specimenId(), command.blockCode(), command.blockType());
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
            throw reject("V2-BLOCK-CODE-CONFLICT", "同一病例下蜡块编号已存在");
        }
        UUID blockId = existingReservedId(operation, command.idempotencyKey());
        Block block = command.externalSource()
                ? Block.createExternal(blockId, grossing.caseId(), grossing.id(), specimen.id(), command.blockCode(),
                        command.blockType(), command.externalSourceReference())
                : Block.create(blockId, grossing.caseId(), grossing.id(), specimen.id(), command.blockCode(),
                        command.blockType());
        Instant now = Instant.now();
        repository.insertBlock(block, actor.hospitalScope(), actor.actorId(), now);
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", block.id(), "V2-BLOCK",
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
        if (!pathologyCase.businessTypeCode().startsWith("CYTOLOGY_")) {
            throw reject("V2-CYTOLOGY-CASE-REQUIRED", "直接细胞切片只能进入细胞病例");
        }
        Specimen specimen = registrationRepository.findSpecimen(specimenId, actor.hospitalScope())
                .filter(item -> item.caseId().equals(caseId) && !item.deleted())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "标本不属于当前病例"));
        if (repository.findActiveSlideIdByCode(caseId, command.slideCode(), actor.hospitalScope()).isPresent()) {
            throw reject("V2-SLIDE-CODE-CONFLICT", "同一病例下切片编号已存在");
        }
        String operation = "PIS-V2-I06-CYTOLOGY-SLIDE-CREATE";
        String digest = digest(caseId, specimenId, command.slideCode(), command.slideType());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return SlideResult.of(findSlide(existing, actor), true);
        }
        UUID slideId = UUID.randomUUID();
        reserve(operation, command.idempotencyKey(), digest, "SLIDE", slideId, actor);
        Slide slide = Slide.fromSpecimenContext(slideId, caseId, specimenId, command.slideCode(),
                command.slideType(), Slide.CYTOLOGY, specimenId, "CYTOLOGY-DIRECT", 1, true);
        Instant now = Instant.now();
        repository.insertSlide(slide, actor.hospitalScope(), actor.actorId(), now);
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
                UUID.randomUUID().toString(), "细胞直接切片已创建");
        outbox.append("V2-I06-CYTOLOGY-SLIDE-CREATED", slide.id(), "V2-SLIDE", slide.concurrencyVersion(),
                UUID.randomUUID().toString(), digest, actor.actorId());
        return SlideResult.of(slide, false);
    }

    @Transactional
    public SlideResult createDirectExternalSlide(UUID caseId, UUID blockId, CreateDirectSlideCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        validate(caseId, "病例内部ID不能为空");
        validate(blockId, "蜡块内部ID不能为空");
        validate(command.slideCode(), "切片编号不能为空");
        validate(command.slideType(), "切片类型不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");
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
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", slide.id(), "V2-SLIDE",
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
        String digest = digest(blockId, command.blockCode(), command.blockType(), command.expectedVersion());
        MaterialIdempotencyResult existing = existing(operation, command.idempotencyKey(), digest);
        if (existing != null) {
            return BlockResult.replayed(findBlock(existing, actor));
        }
        reserve(operation, command.idempotencyKey(), digest, "BLOCK", blockId, actor);
        repository.lockGrossing(block.grossingId(), actor.hospitalScope());
        grossing = findGrossing(block.grossingId(), actor);
        requireEditable(grossing);
        requireVersion(block.concurrencyVersion(), command.expectedVersion());
        if (!block.blockCode().equals(command.blockCode())
                && repository.findActiveBlockIdByCode(block.caseId(), command.blockCode(), actor.hospitalScope())
                        .filter(existingId -> !existingId.equals(block.id())).isPresent()) {
            throw reject("V2-BLOCK-CODE-CONFLICT", "同一病例下蜡块编号已存在");
        }
        String oldCode = block.blockCode();
        block.update(command.blockCode(), command.blockType());
        persistBlock(block, actor, command.expectedVersion());
        if (!oldCode.equals(block.blockCode())) {
            renameRelatedSlides(block, actor);
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", block.id(), "V2-BLOCK",
                UUID.randomUUID().toString(), "V2蜡块事实已修改");
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
        requireEditable(grossing);
        requireVersion(block.concurrencyVersion(), command.expectedVersion());
        if (!repository.softDeleteBlock(blockId, actor.hospitalScope(), command.expectedVersion(), command.reason(),
                actor.actorId(), Instant.now())) {
            throw reject("V2-VERSION-CONFLICT", "蜡块版本冲突，失效未生效");
        }
        for (Slide slide : repository.findActiveSlidesByBlock(blockId, actor.hospitalScope())) {
            repository.softDeleteSlide(slide.id(), actor.hospitalScope(), slide.concurrencyVersion(),
                    "来源蜡块已软删除", actor.actorId(), Instant.now());
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), 1);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", block.id(), "V2-BLOCK",
                UUID.randomUUID().toString(), command.reason());
        return BlockResult.of(Block.persisted(block.id(), block.caseId(), block.grossingId(), block.specimenId(),
                block.blockCode(), block.blockType(), block.externalSource(), block.externalSourceReference(),
                Instant.now(), command.reason(), command.expectedVersion() + 1), false);
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
        List<Block> blocks = repository.findActiveBlocksByGrossing(grossingId, actor.hospitalScope());
        if (blocks.isEmpty()) {
            throw reject("V2-GROSSING-NO-BLOCK", "取材完成前必须至少建立一个有效蜡块");
        }
        UUID businessTypeId = repository.findCaseBusinessTypeId(grossing.caseId(), actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "病例业务类型不存在"));
        String slideContext = Grossing.FROZEN_CONTEXT.equals(grossing.sourceType()) ? Slide.FROZEN_ROUND : Slide.INITIAL;
        List<SlideRule> rules = repository.findSlideRules(actor.hospitalScope(), businessTypeId, slideContext,
                "ON_GROSSING_COMPLETE");
        if (rules.isEmpty()) {
            throw reject("V2-SLIDE-RULE-MISSING", "当前业务类型没有生效的初始切片规则");
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
                changed++;
            }
        }
        repository.updateMaterialIdempotencyResult(operation, command.idempotencyKey(), changed);
        audit.append(operation, MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", resultId, "V2-SLIDE-BATCH",
                UUID.randomUUID().toString(), "V2切片批量完成");
        return new SlideBatchResult(changed, false);
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

    @Transactional(readOnly = true)
    public MaterialTreeResult materialTree(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        Case pathologyCase = activeCase(caseId, actor);
        List<MaterialTreeRow> rows = repository.findMaterialTree(caseId, actor.hospitalScope());
        Map<UUID, SpecimenNodeBuilder> specimens = new LinkedHashMap<>();
        for (MaterialTreeRow row : rows) {
            SpecimenNodeBuilder specimen = specimens.computeIfAbsent(row.specimenId(), ignored ->
                    new SpecimenNodeBuilder(row.specimenId(), row.specimenNo(), row.specimenCode(),
                            row.specimenKindCode()));
            if (row.blockId() != null) {
                BlockNodeBuilder block = specimen.blocks.computeIfAbsent(row.blockId(), ignored ->
                        new BlockNodeBuilder(row.blockId(), row.blockCode(), row.blockType()));
                if (row.slideId() != null) {
                    block.slides.add(new SlideNode(row.slideId(), row.slideCode(), row.slideType(),
                            row.sourceContextType(), row.completedAt(), row.completedAt() != null, row.required(),
                            row.concurrencyVersion()));
                }
            } else if (row.slideId() != null) {
                specimen.directSlides.add(new SlideNode(row.slideId(), row.slideCode(), row.slideType(),
                        row.sourceContextType(), row.completedAt(), row.completedAt() != null, row.required(),
                        row.concurrencyVersion()));
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
        return new MaterialTreeResult(pathologyCase.id(), pathologyCase.caseNo(), pathologyCase.businessTypeCode(),
                specimenNodes, required, completed, required > 0 && required == completed);
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
            throw reject("V2-VERSION-CONFLICT", "取材版本冲突，请重新读取后重试");
        }
    }

    private void persistBlock(Block block, ActorContext actor, long expectedVersion) {
        if (!repository.saveBlock(block, actor.hospitalScope(), expectedVersion, actor.actorId(), Instant.now())) {
            throw reject("V2-VERSION-CONFLICT", "蜡块版本冲突，请重新读取后重试");
        }
    }

    private void persistSlide(Slide slide, ActorContext actor, long expectedVersion) {
        if (!repository.saveSlide(slide, actor.hospitalScope(), expectedVersion, actor.actorId(), Instant.now())) {
            throw reject("V2-VERSION-CONFLICT", "切片版本冲突，请重新读取后重试");
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
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "取材不存在或不在当前数据范围"));
    }

    private Block findBlock(UUID id, ActorContext actor) {
        return repository.findBlock(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "蜡块不存在或不在当前数据范围"));
    }

    private Slide findSlide(UUID id, ActorContext actor) {
        return repository.findSlide(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "切片不存在或不在当前数据范围"));
    }

    private Case activeCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能开展材料生产");
        }
        return pathologyCase;
    }

    private MaterialIdempotencyResult existing(String operation, String key, String digest) {
        MaterialIdempotencyResult existing = repository.findMaterialIdempotency(operation, key).orElse(null);
        if (existing != null && !existing.payloadDigest().equals(digest)) {
            throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的材料命令摘要冲突");
        }
        return existing;
    }

    private void reserve(String operation, String key, String digest, String kind, UUID entityId, ActorContext actor) {
        if (!repository.insertMaterialIdempotency(operation, key, digest, kind, entityId, null, actor.actorId(),
                Instant.now())) {
            MaterialIdempotencyResult existing = repository.findMaterialIdempotency(operation, key)
                    .orElseThrow(() -> reject("V2-IDEMPOTENCY-REPLAY-ERROR", "材料命令幂等记录不可读"));
            if (!existing.payloadDigest().equals(digest)) {
                throw reject("V2-IDEMPOTENCY-CONFLICT", "材料命令摘要冲突");
            }
            throw reject("V2-IDEMPOTENCY-RETRY", "材料命令正在由其他请求处理，请重试");
        }
    }

    private UUID existingReservedId(String operation, String key) {
        return repository.findMaterialIdempotency(operation, key).map(MaterialIdempotencyResult::resultEntityId)
                .orElseThrow(() -> reject("V2-IDEMPOTENCY-INVALID", "材料命令保留记录不可读"));
    }

    private static void requireEditable(Grossing grossing) {
        if (!grossing.isEditable()) {
            throw reject("V2-GROSSING-CLOSED", "已完成取材需要先授权重开");
        }
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw reject("V2-VERSION-CONFLICT", "版本冲突，请重新读取后重试");
        }
    }

    private static void validate(Object value, String message) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw reject("V2-INVALID-REQUEST", message);
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
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

    private record SpecimenNodeBuilder(UUID id, String specimenNo, String specimenCode, String specimenKindCode,
            Map<UUID, BlockNodeBuilder> blocks, List<SlideNode> directSlides) {
        private SpecimenNodeBuilder(UUID id, String specimenNo, String specimenCode, String specimenKindCode) {
            this(id, specimenNo, specimenCode, specimenKindCode, new LinkedHashMap<>(), new ArrayList<>());
        }

        private SpecimenNode build() {
            return new SpecimenNode(id, specimenNo, specimenCode, specimenKindCode,
                    blocks.values().stream().map(BlockNodeBuilder::build).toList(), directSlides);
        }
    }

    private record BlockNodeBuilder(UUID id, String blockCode, String blockType, List<SlideNode> slides) {
        private BlockNodeBuilder(UUID id, String blockCode, String blockType) {
            this(id, blockCode, blockType, new ArrayList<>());
        }

        private BlockNode build() { return new BlockNode(id, blockCode, blockType, slides); }
    }

    public record CreateGrossingCommand(UUID caseId, String sourceType, UUID sourceReferenceId,
            String grossDescription, String grossingInstruction, String grossingDoctorId, String recorderId,
            String idempotencyKey) { }

    public record UpdateGrossingCommand(String grossDescription, String grossingInstruction, String grossingDoctorId,
            String recorderId, long expectedVersion, String idempotencyKey) { }

    public record AssociateSpecimenCommand(UUID specimenId, String materialDescription, String idempotencyKey) { }

    public record CreateBlockCommand(UUID specimenId, String blockCode, String blockType, String idempotencyKey,
            boolean externalSource, String externalSourceReference) {
        public CreateBlockCommand(UUID specimenId, String blockCode, String blockType, String idempotencyKey) {
            this(specimenId, blockCode, blockType, idempotencyKey, false, null);
        }
    }

    public record CreateDirectSlideCommand(String slideCode, String slideType, String idempotencyKey) { }

    public record UpdateBlockCommand(String blockCode, String blockType, long expectedVersion,
            String idempotencyKey) { }

    public record CompleteGrossingCommand(long expectedVersion, String idempotencyKey) { }

    public record ReopenGrossingCommand(long expectedVersion, String reason, String idempotencyKey) { }

    public record SoftDeleteCommand(long expectedVersion, String reason, String idempotencyKey) { }

    public record CompleteSlideCommand(long expectedVersion, String idempotencyKey) { }

    public record SlideCompletion(UUID slideId, long expectedVersion) { }

    public record CompleteSlidesCommand(List<SlideCompletion> slides, String idempotencyKey) { }

    public record PrintCommand(String reason, String idempotencyKey) { }

    public record GrossingResult(UUID grossingId, String grossingNo, UUID caseId, String sourceType,
            Instant completedAt, long concurrencyVersion, boolean duplicate, int affectedCount, boolean reopened) {
        static GrossingResult of(Grossing grossing, boolean duplicate, int affectedCount, boolean reopened) {
            return new GrossingResult(grossing.id(), grossing.grossingNo(), grossing.caseId(), grossing.sourceType(),
                    grossing.completedAt(), grossing.concurrencyVersion(), duplicate, affectedCount, reopened);
        }

        static GrossingResult replayed(Grossing grossing) { return of(grossing, true, 0, false); }
    }

    public record BlockResult(UUID blockId, UUID caseId, UUID grossingId, UUID specimenId, String blockCode,
            String blockType, Instant deletedAt, long concurrencyVersion, boolean duplicate) {
        static BlockResult of(Block block, boolean duplicate) {
            return new BlockResult(block.id(), block.caseId(), block.grossingId(), block.specimenId(), block.blockCode(),
                    block.blockType(), block.deletedAt(), block.concurrencyVersion(), duplicate);
        }

        static BlockResult replayed(Block block) { return of(block, true); }
    }

    public record SlideResult(UUID slideId, UUID caseId, UUID blockId, String slideCode, String slideType,
            String sourceContextType, Instant completedAt, long concurrencyVersion, boolean duplicate) {
        static SlideResult of(Slide slide, boolean duplicate) {
            return new SlideResult(slide.id(), slide.caseId(), slide.blockId(), slide.slideCode(), slide.slideType(),
                    slide.sourceContextType(), slide.completedAt(), slide.concurrencyVersion(), duplicate);
        }

        static SlideResult replayed(Slide slide) { return of(slide, true); }
    }

    public record GrossingCompletionResult(UUID grossingId, String grossingNo, Instant completedAt,
            int createdSlideCount, boolean duplicate, String eventTypeCode) { }

    public record SlideBatchResult(int changedCount, boolean duplicate) { }

    public record PrintResult(UUID entityId, boolean duplicate, String resultCode) { }

    public record MaterialTreeResult(UUID caseId, String caseNo, String businessTypeCode,
            List<SpecimenNode> specimens, int initialRequiredCount, int initialCompletedCount,
            boolean initialProductionComplete) { }

    public record SpecimenNode(UUID specimenId, String specimenNo, String specimenCode, String specimenKindCode,
            List<BlockNode> blocks, List<SlideNode> directSlides) { }

    public record BlockNode(UUID blockId, String blockCode, String blockType, List<SlideNode> slides) { }

    public record SlideNode(UUID slideId, String slideCode, String slideType, String sourceContextType,
            Instant completedAt, boolean completed, boolean required, long concurrencyVersion) { }
}
