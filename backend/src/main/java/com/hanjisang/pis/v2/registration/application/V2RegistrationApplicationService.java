package com.hanjisang.pis.v2.registration.application;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.integration.InboundApplicationInbox;
import com.hanjisang.pis.integration.InboundApplicationSource.InboundApplication;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.JdbcAuditEventRepository.AuditChange;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.Specimen;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository.IdempotencyResult;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository.Routing;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository.ApplicationMappingOption;

@Service
public class V2RegistrationApplicationService {

    private static final String CASE_OPERATION = "PIS-V2-I01-CASE-CREATE";
    private static final String SPECIMEN_OPERATION = "PIS-V2-I01-SPECIMEN-REGISTER";

    private final JdbcV2RegistrationRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final InboundApplicationInbox inboundInbox;

    public V2RegistrationApplicationService(JdbcV2RegistrationRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox,
            InboundApplicationInbox inboundInbox) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.inboundInbox = inboundInbox;
    }

    @Transactional
    public CaseResult createCase(CreateCaseCommand command) {
        ActorContext actor = authorization.require("P14-PERM-004");
        validate(command.sourceSystemCode(), "申请来源系统不能为空");
        validate(command.externalApplicationId(), "外部申请标识不能为空");
        validate(command.applicationItemCode(), "申请项目不能为空");
        validate(command.patientReference(), "患者上下文引用不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");

        String digest = digest(command.sourceSystemCode(), command.externalApplicationId(),
                command.applicationItemCode(), command.patientReference(), command.visitReference());
        var existing = repository.findIdempotency(CASE_OPERATION, command.idempotencyKey());
        if (existing.isPresent()) {
            return replayCase(existing.get(), digest, actor);
        }

        Routing routing = repository.findRouting(command.applicationItemCode())
                .orElseThrow(() -> reject("P12-ERR-011", "申请项目没有生效的V2业务类型映射"));
        if (!routing.mapping().canRouteNewCase() || !routing.businessType().acceptsNewCase()) {
            throw reject("P12-ERR-011", "申请项目或业务类型当前未启用");
        }

        Instant now = Instant.now();
        UUID caseId = UUID.randomUUID();
        if (!repository.insertIdempotency(CASE_OPERATION, command.idempotencyKey(), digest, "CASE", caseId, null,
                actor.actorId(), now)) {
            return replayCase(repository.findIdempotency(CASE_OPERATION, command.idempotencyKey())
                    .orElseThrow(() -> reject("P12-ERR-006", "V2病例幂等记录不可读")), digest, actor);
        }
        String caseNo = repository.allocateNumber(actor.hospitalScope(), routing.businessType().code(), "CASE", now);
        Case pathologyCase = Case.active(caseId, caseNo, command.sourceSystemCode(), command.externalApplicationId(),
                command.applicationItemCode(), routing.businessType().id(), routing.businessType().code(),
                command.patientReference(), command.visitReference());
        repository.insertCase(pathologyCase, actor.hospitalScope(), now, actor.actorId());

        String correlationId = UUID.randomUUID().toString();
        audit.append("PIS-V2-I01-CASE-CREATE", "P14-PERM-004", actor, "ALLOWED", "COMPLETED", caseId,
                "V2-CASE", correlationId, "V2病例已建立");
        outbox.append("P12-EVC-002", caseId, "V2-CASE", pathologyCase.concurrencyVersion(), correlationId,
                digest, actor.actorId());
        return CaseResult.created(pathologyCase, false, "P12-EVC-002");
    }

    @Transactional(readOnly = true)
    public List<ApplicationMappingOption> applicationMappings() {
        ActorContext actor = authorization.require("P14-PERM-004");
        return repository.findActiveApplicationMappings(actor.hospitalScope());
    }

    @Transactional(readOnly = true)
    public RegistrationQueueResult registrationQueue() {
        ActorContext actor = authorization.require("P14-PERM-004");
        var inbox = inboundInbox.snapshot();
        List<PendingApplicationView> pending = inbox.pendingApplications().stream()
                .map(item -> pendingApplication(item)).toList();
        List<CancelledApplicationView> cancelled = inbox.cancelledApplications().stream()
                .map(item -> new CancelledApplicationView(item.applicationId(), item.applicationNo(),
                        item.patientReference(), item.visitReference(), item.department(), item.doctor(),
                        item.applicationItemCode(), item.receivedAt())).toList();
        List<RegistrationCaseView> recent = repository.findRecentRegistrations(actor.hospitalScope()).stream()
                .map(row -> new RegistrationCaseView(row.caseId(), row.caseNo(), row.applicationNo(),
                        row.applicationItemCode(), row.businessTypeCode(), row.businessTypeName(),
                        row.patientReference(), row.registeredAt()))
                .toList();
        return new RegistrationQueueResult(inbox.sourceAvailable(), inbox.sourceMessage(), pending, cancelled, recent,
                Instant.now());
    }

    @Transactional
    public CaseResult registerInboundApplication(UUID applicationId) {
        ActorContext actor = authorization.require("P14-PERM-004");
        InboundApplication item = inboundInbox.require(applicationId);
        CaseResult result = createCase(new CreateCaseCommand(item.sourceSystemCode(), item.applicationNo(),
                item.applicationItemCode(), item.patientReference(), item.visitReference(),
                "inbound-application-" + item.applicationId()));
        inboundInbox.markRegistered(item.applicationId(), result.caseId(), Instant.now());
        audit.append("PIS-V2-I01-INBOUND-APPLICATION-REGISTER", "P14-PERM-004", actor, "ALLOWED", "COMPLETED",
                result.caseId(), "V2-CASE", UUID.randomUUID().toString(), "申请已登记");
        return result;
    }

    private PendingApplicationView pendingApplication(InboundApplication item) {
        var routing = repository.findRouting(item.applicationItemCode());
        return new PendingApplicationView(item.applicationId(), item.applicationNo(), item.patientReference(),
                item.visitReference(), item.department(), item.doctor(), item.applicationItemCode(),
                routing.map(value -> value.businessType().code()).orElse(null),
                routing.map(value -> value.businessType().displayName()).orElse(null), item.receivedAt());
    }

    @Transactional
    public SpecimenResult registerSpecimen(RegisterSpecimenCommand command) {
        ActorContext actor = authorization.require("P14-PERM-008");
        validate(command.caseId(), "病例内部ID不能为空");
        validate(command.specimenCode(), "标本代码不能为空");
        validate(command.specimenKindCode(), "标本类型不能为空");
        validate(command.sourceKindCode(), "标本来源类型不能为空");
        validate(command.sourceReference(), "标本来源引用不能为空");
        validate(command.collectionSite(), "标本来源部位不能为空");
        validate(command.collectionMethodCode(), "标本采集方式不能为空");
        validate(command.idempotencyKey(), "幂等键不能为空");

        String digest = digest(command.caseId().toString(), command.specimenCode(), command.specimenKindCode(),
                command.sourceKindCode(), command.sourceReference(), command.collectionSite(),
                command.collectionMethodCode(), command.lateralityCode(), command.quantityValue(),
                command.quantityUnitCode(), command.description(), command.removedAt(), command.fixedAt(),
                command.receivedAt(), command.labelCode());
        var existing = repository.findIdempotency(SPECIMEN_OPERATION, command.idempotencyKey());
        if (existing.isPresent()) {
            return replaySpecimen(existing.get(), digest, actor);
        }

        Case pathologyCase = repository.findCase(command.caseId(), actor.hospitalScope())
                .orElseThrow(() -> reject("P12-ERR-010", "V2病例不存在或不在当前数据范围"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("P12-ERR-010", "已取消V2病例不能登记标本");
        }
        if (repository.findSpecimenIdByCode(pathologyCase.id(), command.specimenCode()).isPresent()) {
            throw reject("P12-ERR-022", "同一病例下标本代码已存在");
        }
        if (repository.findSpecimenIdByLabel(actor.hospitalScope(), command.labelCode()).isPresent()) {
            throw reject("P12-ERR-022", "标签已绑定其他V2标本，不能重复使用");
        }

        Instant now = Instant.now();
        UUID specimenId = UUID.randomUUID();
        if (!repository.insertIdempotency(SPECIMEN_OPERATION, command.idempotencyKey(), digest, "SPECIMEN", null,
                specimenId, actor.actorId(), now)) {
            return replaySpecimen(repository.findIdempotency(SPECIMEN_OPERATION, command.idempotencyKey())
                    .orElseThrow(() -> reject("P12-ERR-006", "V2标本幂等记录不可读")), digest, actor);
        }
        String specimenNo = repository.allocateNumber(actor.hospitalScope(), pathologyCase.businessTypeCode(),
                "SPECIMEN", now);
        Specimen specimen = Specimen.register(specimenId, pathologyCase.id(), specimenNo, command.specimenCode(),
                command.specimenKindCode(), command.sourceKindCode(), command.sourceReference(),
                command.collectionSite(), command.collectionMethodCode(), command.lateralityCode(),
                command.quantityValue(), command.quantityUnitCode(), command.description(), command.removedAt(),
                command.fixedAt(), command.receivedAt(), command.labelCode());
        repository.insertSpecimen(specimen, actor.hospitalScope(), actor.actorId(), now);

        String correlationId = UUID.randomUUID().toString();
        audit.append("PIS-V2-I01-SPECIMEN-REGISTER", "P14-PERM-008", actor, "ALLOWED", "COMPLETED", specimenId,
                "V2-SPECIMEN", correlationId, "V2标本已登记");
        return SpecimenResult.created(specimen, false, "PIS-V2-SPECIMEN-REGISTERED");
    }

    @Transactional
    public SpecimenResult updateSpecimen(UUID specimenId, UpdateSpecimenCommand command) {
        ActorContext actor = authorization.require("P14-PERM-008");
        validate(specimenId, "标本内部ID不能为空");
        validate(command.specimenCode(), "标本代码不能为空");
        validate(command.specimenKindCode(), "标本类型不能为空");
        validate(command.sourceKindCode(), "标本来源类型不能为空");
        validate(command.sourceReference(), "标本来源引用不能为空");
        validate(command.collectionSite(), "标本来源部位不能为空");
        validate(command.collectionMethodCode(), "标本采集方式不能为空");
        Specimen specimen = findSpecimen(specimenId, actor);
        requireExpectedVersion(specimen, command.expectedVersion());
        String beforeSpecimenCode = specimen.specimenCode();
        String beforeCollectionSite = specimen.collectionSite();
        if (repository.findSpecimenIdByCode(specimen.caseId(), command.specimenCode())
                .filter(existingId -> !existingId.equals(specimenId)).isPresent()) {
            throw reject("P12-ERR-022", "同一病例下标本代码已存在");
        }
        if (repository.findSpecimenIdByLabel(actor.hospitalScope(), command.labelCode())
                .filter(existingId -> !existingId.equals(specimenId)).isPresent()) {
            throw reject("P12-ERR-022", "标签已绑定其他V2标本，不能重复使用");
        }
        Instant now = Instant.now();
        try {
            specimen.updateDetails(command.specimenCode(), command.specimenKindCode(), command.sourceKindCode(),
                    command.sourceReference(), command.collectionSite(), command.collectionMethodCode(),
                    command.lateralityCode(), command.quantityValue(), command.quantityUnitCode(),
                    command.description(), command.removedAt(), command.fixedAt(), command.receivedAt(),
                    command.labelCode(), now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw reject("P12-ERR-010", exception.getMessage());
        }
        if (!repository.updateSpecimen(specimen, actor.hospitalScope(), command.expectedVersion(), actor.actorId(), now)) {
            throw reject("P12-ERR-010", "V2标本版本冲突，修改未生效");
        }
        audit.appendWithChanges("PIS-V2-I01-SPECIMEN-UPDATE", "P14-PERM-008", actor, "COMPLETED", specimenId,
                "V2-SPECIMEN", UUID.randomUUID().toString(), "标本信息已修改",
                List.of(new AuditChange("specimenCode", "标本代码", beforeSpecimenCode, specimen.specimenCode()),
                        new AuditChange("collectionSite", "标本部位", beforeCollectionSite, specimen.collectionSite())));
        return SpecimenResult.created(specimen, false, "PIS-V2-SPECIMEN-UPDATED");
    }

    @Transactional
    public SpecimenResult softDeleteSpecimen(UUID specimenId, SoftDeleteSpecimenCommand command) {
        ActorContext actor = authorization.require("P14-PERM-010");
        validate(specimenId, "标本内部ID不能为空");
        validate(command.reason(), "标本软删除原因不能为空");
        Specimen specimen = findSpecimen(specimenId, actor);
        requireExpectedVersion(specimen, command.expectedVersion());
        Instant now = Instant.now();
        try {
            specimen.softDelete(command.reason(), now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw reject("P12-ERR-010", exception.getMessage());
        }
        if (!repository.softDeleteSpecimen(specimenId, actor.hospitalScope(), command.expectedVersion(), command.reason(),
                actor.actorId(), now)) {
            throw reject("P12-ERR-010", "V2标本版本冲突，软删除未生效");
        }
        audit.append("PIS-V2-I01-SPECIMEN-SOFT-DELETE", "P14-PERM-010", actor, "ALLOWED", "COMPLETED", specimenId,
                "V2-SPECIMEN", UUID.randomUUID().toString(), command.reason());
        return SpecimenResult.created(specimen, false, "PIS-V2-SPECIMEN-SOFT-DELETED");
    }

    @Transactional
    public SpecimenResult receiveSpecimen(UUID specimenId, ReceiveSpecimenCommand command) {
        ActorContext actor = authorization.require("P14-PERM-008");
        validate(specimenId, "specimenId");
        validate(command.verificationCode(), "verificationCode");
        Specimen specimen = findSpecimen(specimenId, actor);
        if (specimen.deleted()) throw reject("P12-ERR-010", "Deleted specimen cannot be received");
        Instant now = command.receivedAt() == null ? Instant.now() : command.receivedAt();
        if (!repository.markSpecimenReceived(specimenId, actor.hospitalScope(), command.expectedVersion(), now,
                actor.actorId(), Instant.now())) {
            throw reject("P12-ERR-010", "Specimen version conflict; receiving was not applied");
        }
        repository.insertSpecimenReceipt(specimenId, command.verificationCode(), command.actualDescription(),
                command.reason(), actor.hospitalScope(), actor.actorId(), now);
        audit.append("PIS-V2-SPECIMEN-RECEIVE", "P14-PERM-008", actor, "ALLOWED", "COMPLETED", specimenId,
                "V2-SPECIMEN", UUID.randomUUID().toString(), command.verificationCode());
        return SpecimenResult.read(findSpecimen(specimenId, actor));
    }

    @Transactional
    public SpecimenResult splitSpecimen(UUID specimenId, SplitSpecimenCommand command) {
        ActorContext actor = authorization.require("P14-PERM-008");
        validate(specimenId, "specimenId");
        validate(command.childSpecimenCode(), "childSpecimenCode");
        validate(command.reason(), "reason");
        Specimen source = findSpecimen(specimenId, actor);
        if (source.deleted()) throw reject("P12-ERR-010", "Deleted specimen cannot be split");
        SpecimenResult child = registerSpecimen(new RegisterSpecimenCommand(source.caseId(),
                command.childSpecimenCode(), command.specimenKindCode() == null ? source.specimenKindCode()
                        : command.specimenKindCode(),
                command.sourceKindCode() == null ? source.sourceKindCode() : command.sourceKindCode(),
                source.sourceReference(), source.collectionSite(), source.collectionMethodCode(),
                command.lateralityCode() == null ? source.lateralityCode() : command.lateralityCode(),
                command.quantityValue(), command.quantityUnitCode(),
                command.description() == null ? source.description() : command.description(), source.removedAt(),
                source.fixedAt(), source.receivedAt(), command.labelCode(),
                "specimen-split-" + specimenId + "-" + command.childSpecimenCode()));
        repository.insertSpecimenSplit(specimenId, child.specimenId(), command.quantityValue(), command.reason(),
                actor.hospitalScope(), actor.actorId(), Instant.now());
        audit.append("PIS-V2-SPECIMEN-SPLIT", "P14-PERM-008", actor, "ALLOWED", "COMPLETED", child.specimenId(),
                "V2-SPECIMEN", UUID.randomUUID().toString(), "Split from " + specimenId);
        return child;
    }

    @Transactional(readOnly = true)
    public CaseResult getCase(UUID caseId) {
        ActorContext actor = authorization.require("P14-PERM-048");
        return CaseResult.read(repository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("P12-ERR-010", "V2病例不存在或不在当前数据范围")));
    }

    @Transactional
    public CaseResult cancelCase(UUID caseId, CancelCaseCommand command) {
        ActorContext actor = authorization.require("P14-PERM-010");
        validate(caseId, "caseId");
        validate(command.reason(), "reason");
        Case pathologyCase = repository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("P12-ERR-010", "Case not found"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("P12-ERR-010", "Only an ACTIVE case can be cancelled");
        }
        Instant now = Instant.now();
        if (!repository.cancelCase(caseId, actor.hospitalScope(), command.expectedVersion(), command.reason(),
                actor.actorId(), now)) {
            throw reject("P12-ERR-010", "Case version conflict; cancellation was not applied");
        }
        audit.append("PIS-V2-CASE-CANCEL", "P14-PERM-010", actor, "ALLOWED", "COMPLETED", caseId,
                "V2-CASE", UUID.randomUUID().toString(), command.reason());
        return CaseResult.read(repository.findCase(caseId, actor.hospitalScope()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public SpecimenResult getSpecimen(UUID specimenId) {
        ActorContext actor = authorization.require("P14-PERM-049");
        return SpecimenResult.read(findSpecimen(specimenId, actor));
    }

    private CaseResult replayCase(IdempotencyResult existing, String digest, ActorContext actor) {
        verifyDigest(existing, digest);
        if (!"CASE".equals(existing.resultKindCode()) || existing.resultCaseId() == null) {
            throw reject("P12-ERR-003", "幂等记录的V2病例结果类型不一致");
        }
        Case pathologyCase = repository.findCase(existing.resultCaseId(), actor.hospitalScope())
                .orElseThrow(() -> reject("P12-ERR-006", "V2病例幂等结果缺少业务事实"));
        return CaseResult.created(pathologyCase, true, "P12-EVC-002");
    }

    private SpecimenResult replaySpecimen(IdempotencyResult existing, String digest, ActorContext actor) {
        verifyDigest(existing, digest);
        if (!"SPECIMEN".equals(existing.resultKindCode()) || existing.resultSpecimenId() == null) {
            throw reject("P12-ERR-003", "幂等记录的V2标本结果类型不一致");
        }
        return SpecimenResult.created(findSpecimen(existing.resultSpecimenId(), actor), true,
                "PIS-V2-SPECIMEN-REGISTERED");
    }

    private Specimen findSpecimen(UUID specimenId, ActorContext actor) {
        return repository.findSpecimen(specimenId, actor.hospitalScope())
                .orElseThrow(() -> reject("P12-ERR-010", "V2标本不存在或不在当前数据范围"));
    }

    private static void requireExpectedVersion(Specimen specimen, long expectedVersion) {
        if (specimen.concurrencyVersion() != expectedVersion) {
            throw reject("P12-ERR-010", "V2标本版本冲突，请重新读取后重试");
        }
    }

    private void verifyDigest(IdempotencyResult existing, String digest) {
        if (!existing.payloadDigest().equals(digest)) {
            throw reject("P12-ERR-003", "相同幂等键对应的V2请求摘要冲突");
        }
    }

    private static void validate(Object value, String message) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw reject("P12-ERR-001", message);
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    private static String digest(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = java.util.Arrays.stream(values)
                    .map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    public record CreateCaseCommand(String sourceSystemCode, String externalApplicationId, String applicationItemCode,
            String patientReference, String visitReference, String idempotencyKey) { }

    public record RegisterSpecimenCommand(UUID caseId, String specimenCode, String specimenKindCode,
            String sourceKindCode, String sourceReference, String collectionSite, String collectionMethodCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode, String idempotencyKey) {
        public RegisterSpecimenCommand(UUID caseId, String specimenCode, String specimenKindCode,
                String sourceKindCode, String sourceReference, String collectionSite, String collectionMethodCode,
                String labelCode, String idempotencyKey) {
            this(caseId, specimenCode, specimenKindCode, sourceKindCode, sourceReference, collectionSite,
                    collectionMethodCode, null, null, null, null, null, null, null, labelCode, idempotencyKey);
        }
    }

    public record UpdateSpecimenCommand(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String lateralityCode,
            BigDecimal quantityValue, String quantityUnitCode, String description, Instant removedAt,
            Instant fixedAt, Instant receivedAt, String labelCode, long expectedVersion) {
        public UpdateSpecimenCommand(String specimenCode, String specimenKindCode, String sourceKindCode,
                String sourceReference, String collectionSite, String collectionMethodCode, String labelCode,
                long expectedVersion) {
            this(specimenCode, specimenKindCode, sourceKindCode, sourceReference, collectionSite,
                    collectionMethodCode, null, null, null, null, null, null, null, labelCode, expectedVersion);
        }
    }

    public record SoftDeleteSpecimenCommand(long expectedVersion, String reason) { }

    public record CancelCaseCommand(long expectedVersion, String reason) { }
    public record ReceiveSpecimenCommand(String verificationCode, String actualDescription, String reason,
            Instant receivedAt, long expectedVersion) { }
    public record SplitSpecimenCommand(String childSpecimenCode, String specimenKindCode, String sourceKindCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            String labelCode, String reason) { }

    public record CaseResult(UUID caseId, String caseNo, String businessTypeCode, String patientReference,
            String visitReference, String applicationNo, String lifecycleStateCode, boolean numberBindingActive,
            long concurrencyVersion, Instant cancelledAt, String cancelledByRef, String cancellationReason,
            boolean duplicate, String eventTypeCode) {
        static CaseResult created(Case pathologyCase, boolean duplicate, String eventTypeCode) {
            return new CaseResult(pathologyCase.id(), pathologyCase.caseNo(), pathologyCase.businessTypeCode(),
                    pathologyCase.patientReference(), pathologyCase.visitReference(), pathologyCase.externalApplicationId(),
                    pathologyCase.lifecycleStateCode(), pathologyCase.numberBindingActive(),
                    pathologyCase.concurrencyVersion(), null, null, null, duplicate, eventTypeCode);
        }

        static CaseResult read(Case pathologyCase) {
            return new CaseResult(pathologyCase.id(), pathologyCase.caseNo(), pathologyCase.businessTypeCode(),
                    pathologyCase.patientReference(), pathologyCase.visitReference(), pathologyCase.externalApplicationId(),
                    pathologyCase.lifecycleStateCode(), pathologyCase.numberBindingActive(),
                    pathologyCase.concurrencyVersion(), null, null, null, false, "PIS-V2-CASE-READ");
        }
    }

    public record RegistrationQueueResult(boolean sourceAvailable, String sourceMessage,
            List<PendingApplicationView> pendingApplications, List<CancelledApplicationView> cancelledApplications,
            List<RegistrationCaseView> recentRegistrations, Instant refreshedAt) { }

    public record PendingApplicationView(UUID applicationId, String applicationNo, String patientReference,
            String visitReference, String department, String doctor, String applicationItemCode,
            String businessTypeCode, String businessTypeName, Instant receivedAt) { }

    public record CancelledApplicationView(UUID applicationId, String applicationNo, String patientReference,
            String visitReference, String department, String doctor, String applicationItemCode,
            Instant receivedAt) { }

    public record RegistrationCaseView(UUID caseId, String caseNo, String applicationNo,
            String applicationItemCode, String businessTypeCode, String businessTypeName,
            String patientReference, Instant registeredAt) { }

    public record SpecimenResult(UUID specimenId, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String lateralityCode, BigDecimal quantityValue, String quantityUnitCode,
            String description, Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode,
            Instant deletedAt, String deletionReason, long concurrencyVersion, boolean duplicate,
            String eventTypeCode) {
        static SpecimenResult created(Specimen specimen, boolean duplicate, String eventTypeCode) {
            return new SpecimenResult(specimen.id(), specimen.caseId(), specimen.specimenNo(), specimen.specimenCode(),
                    specimen.specimenKindCode(), specimen.sourceKindCode(), specimen.sourceReference(),
                    specimen.collectionSite(), specimen.collectionMethodCode(), specimen.lateralityCode(),
                    specimen.quantityValue(), specimen.quantityUnitCode(), specimen.description(), specimen.removedAt(),
                    specimen.fixedAt(), specimen.receivedAt(), specimen.labelCode(), specimen.deletedAt(),
                    specimen.deletionReason(), specimen.concurrencyVersion(), duplicate, eventTypeCode);
        }

        static SpecimenResult read(Specimen specimen) {
            return created(specimen, false, "PIS-V2-SPECIMEN-READ");
        }
    }
}
