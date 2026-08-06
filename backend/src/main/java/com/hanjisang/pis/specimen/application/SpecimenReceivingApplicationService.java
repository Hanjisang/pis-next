package com.hanjisang.pis.specimen.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.specimen.domain.Specimen;
import com.hanjisang.pis.specimen.infrastructure.JdbcSpecimenRepository;

@Service
public class SpecimenReceivingApplicationService {

    private final JdbcSpecimenRepository repository;
    private final SpecimenNumberAllocator numberAllocator;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public SpecimenReceivingApplicationService(JdbcSpecimenRepository repository, SpecimenNumberAllocator numberAllocator,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.numberAllocator = numberAllocator;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public ExpectedSpecimenResult registerExpected(ExpectedSpecimenCommand command) {
        ActorContext actor = authorization.require("P14-PERM-008");
        if (command.caseId() == null || command.specimenKindCode() == null || command.specimenKindCode().isBlank()
                || command.collectionSite() == null || command.collectionSite().isBlank() || command.expectedQuantity() < 1) {
            throw new P15BusinessException("P12-ERR-021", "预计标本缺少病例、类型、部位或有效数量");
        }
        if (command.containerBarcode() != null && !command.containerBarcode().isBlank()) {
            if (!command.containerBarcode().matches("[A-Za-z0-9_-]{4,128}")) {
                throw new P15BusinessException("P12-ERR-021", "容器条码格式不合法");
            }
        }
        String barcode = command.containerBarcode() == null || command.containerBarcode().isBlank()
                ? numberAllocator.containerBarcode() : command.containerBarcode();
        Specimen specimen = repository.insertExpected(command.caseId(), numberAllocator.specimenNumber(), barcode,
                command.specimenKindCode(), command.collectionSite(), command.collectionMethodCode(),
                command.expectedQuantity(), actor.hospitalScope(), actor.actorId(), Instant.now());
        String correlationId = UUID.randomUUID().toString();
        audit.append("P12-API-008", "P14-PERM-008", actor, "ALLOWED", "COMPLETED", specimen.id(), "OBJ-003",
                correlationId, "expected specimen registered");
        outbox.append("P12-EVC-003", specimen.id(), "OBJ-003", specimen.concurrencyVersion(), correlationId,
                barcode, actor.actorId());
        return new ExpectedSpecimenResult(specimen.id(), specimen.specimenNo(), barcode, specimen.lifecycleStateCode(),
                specimen.concurrencyVersion(), false);
    }

    @Transactional
    public ReceivingResult receive(ReceiveCommand command) {
        ActorContext actor = authorization.require("P14-PERM-009");
        if (command.barcode() == null || command.barcode().isBlank() || command.actualQuantity() < 1) {
            throw new P15BusinessException("P12-ERR-021", "接收必须提供条码和有效实际数量");
        }
        Specimen specimen = repository.findByBarcode(command.barcode(), actor.hospitalScope())
                .orElseThrow(() -> new P15BusinessException("P12-ERR-022", "条码未找到对应预计标本"));
        String digest = command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                ? command.barcode() + ":" + command.actualQuantity() : command.idempotencyKey();
        if (repository.hasReceivingFact(specimen.id(), digest)) {
            return new ReceivingResult(specimen.id(), specimen.specimenNo(), specimen.lifecycleStateCode(),
                    specimen.concurrencyVersion(), true, "P12-EVC-003");
        }
        long expectedVersion = command.expectedVersion();
        if (specimen.concurrencyVersion() != expectedVersion) {
            throw new P15BusinessException("P12-ERR-024", "标本版本冲突，请重新读取");
        }
        int expectedQuantity = repository.expectedQuantity(specimen.id());
        if (command.actualQuantity() != expectedQuantity) {
            throw new P15BusinessException("P12-ERR-023", "预计数量与实际数量不一致，请隔离或人工复核");
        }
        String correlationId = UUID.randomUUID().toString();
        if (!repository.transitionToReceived(specimen.id(), expectedVersion, command.actualQuantity(), actor.actorId(),
                digest, Instant.now())) {
            throw new P15BusinessException("P12-ERR-024", "并发接收冲突，请重新读取");
        }
        audit.append("P12-API-009", "P14-PERM-009", actor, "ALLOWED", "COMPLETED", specimen.id(), "OBJ-003",
                correlationId, "identity and quantity verified");
        outbox.append("P12-EVC-003", specimen.id(), "OBJ-003", expectedVersion + 1, correlationId, digest,
                actor.actorId());
        return new ReceivingResult(specimen.id(), specimen.specimenNo(), Specimen.RECEIVED, expectedVersion + 1, false,
                "P12-EVC-003");
    }

    @Transactional
    public IsolationResult isolate(UUID specimenId, IsolationCommand command) {
        ActorContext actor = authorization.require("P14-PERM-010");
        if (command.reason() == null || command.reason().isBlank()) {
            throw new P15BusinessException("P12-ERR-025", "隔离必须提供原因");
        }
        Specimen specimen = repository.findById(specimenId, actor.hospitalScope())
                .orElseThrow(() -> new P15BusinessException("P12-ERR-022", "标本不存在"));
        if (specimen.concurrencyVersion() != command.expectedVersion()) {
            throw new P15BusinessException("P12-ERR-024", "标本版本冲突，请重新读取");
        }
        String correlationId = UUID.randomUUID().toString();
        if (!repository.isolate(specimenId, command.expectedVersion(), command.reason(), actor.actorId(), Instant.now())) {
            throw new P15BusinessException("P12-ERR-026", "当前标本状态不允许隔离");
        }
        audit.append("P12-API-010", "P14-PERM-010", actor, "ALLOWED", "ISOLATED", specimenId, "OBJ-003",
                correlationId, command.reason());
        outbox.append("P12-EVC-003", specimenId, "OBJ-003", command.expectedVersion() + 1, correlationId,
                command.reason(), actor.actorId());
        return new IsolationResult(specimenId, Specimen.ISOLATED, command.expectedVersion() + 1, false);
    }

    @Transactional
    public HandoffResult handoff(UUID specimenId, HandoffCommand command) {
        ActorContext actor = authorization.require("P14-PERM-011");
        if (command.toActorRef() == null || command.toActorRef().isBlank()) {
            throw new P15BusinessException("P12-ERR-027", "交接必须提供接收方");
        }
        String digest = command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                ? specimenId + ":" + command.toActorRef() : command.idempotencyKey();
        String correlationId = UUID.randomUUID().toString();
        boolean created = repository.appendHandoff(specimenId, actor.hospitalScope(), actor.actorId(),
                command.toActorRef(), digest, actor.actorId(), Instant.now());
        if (!created) {
            return new HandoffResult(specimenId, "IDEMPOTENT_REPLAY", true);
        }
        audit.append("P12-API-011", "P14-PERM-011", actor, "ALLOWED", "COMPLETED", specimenId, "OBJ-003",
                correlationId, "handoff signed");
        outbox.append("P12-EVC-003", specimenId, "OBJ-003", command.expectedVersion(), correlationId, digest,
                actor.actorId());
        return new HandoffResult(specimenId, "SIGNED", false);
    }

    public List<Map<String, Object>> queue() {
        ActorContext actor = authorization.require("P14-PERM-049");
        return repository.receivingQueue(actor.hospitalScope());
    }

    public List<Map<String, Object>> trace(UUID caseId) {
        ActorContext actor = authorization.require("P14-PERM-048");
        return repository.trace(caseId, actor.hospitalScope());
    }

    public record ExpectedSpecimenCommand(UUID caseId, String specimenKindCode, String collectionSite,
            String collectionMethodCode, int expectedQuantity, String containerBarcode) {
    }

    public record ReceiveCommand(String barcode, int expectedQuantity, int actualQuantity, long expectedVersion,
            String idempotencyKey) {
    }

    public record IsolationCommand(long expectedVersion, String reason) {
    }

    public record HandoffCommand(String toActorRef, long expectedVersion, String idempotencyKey) {
    }

    public record ExpectedSpecimenResult(UUID specimenId, String specimenNo, String containerBarcode,
            String lifecycleStateCode, long concurrencyVersion, boolean duplicate) {
    }

    public record ReceivingResult(UUID specimenId, String specimenNo, String lifecycleStateCode,
            long concurrencyVersion, boolean duplicate, String eventTypeCode) {
    }

    public record IsolationResult(UUID specimenId, String lifecycleStateCode, long concurrencyVersion,
            boolean duplicate) {
    }

    public record HandoffResult(UUID specimenId, String handoffStateCode, boolean duplicate) {
    }
}
