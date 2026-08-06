package com.hanjisang.pis.accession.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.accession.domain.PathologyCase;
import com.hanjisang.pis.accession.domain.PathologyRequest;
import com.hanjisang.pis.accession.infrastructure.JdbcRegistrationRepository;
import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;

@Service
public class RegistrationApplicationService {

    private final JdbcRegistrationRepository repository;
    private final BusinessNumberAllocator numberAllocator;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public RegistrationApplicationService(JdbcRegistrationRepository repository, BusinessNumberAllocator numberAllocator,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.numberAllocator = numberAllocator;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public RegistrationResult registerExternal(ExternalRegistrationCommand command) {
        ActorContext actor = authorization.require("P14-PERM-001");
        validateExternal(command);
        String correlationId = UUID.randomUUID().toString();
        var existing = repository.findRequestByExternal(command.sourceSystemCode(), command.externalRequestId());
        if (existing.isPresent()) {
            String existingDigest = repository.findExternalDigest(command.sourceSystemCode(), command.externalRequestId())
                    .orElse(command.payloadDigest());
            if (!existingDigest.equals(command.payloadDigest())) {
                audit.append("P12-API-001", "P14-PERM-001", actor, "REJECTED", "IDEMPOTENCY_CONFLICT", existing.get(),
                        "OBJ-001", correlationId, "P12-ERR-003");
                throw new P15BusinessException("P12-ERR-003", "相同外部申请标识的载荷摘要冲突");
            }
            audit.append("P12-API-001", "P14-PERM-001", actor, "IDEMPOTENT", "REPLAYED", existing.get(), "OBJ-001",
                    correlationId, "same-payload replay");
            return RegistrationResult.duplicate(existing.get(), "P12-EVC-001");
        }
        PathologyRequest request;
        try {
            request = repository.insertExternal(command.sourceSystemCode(), command.externalRequestId(),
                    command.sourceMessageIdentity(), command.sourceMessageVersion(), command.payloadDigest(),
                    command.rawPayloadReference(), command.externalPatientId(), command.externalVisitId(),
                    command.pathologyModalityCode(), command.requestContent(), numberAllocator.applicationNumber(),
                    actor.actorId(), Instant.now());
        } catch (DuplicateKeyException exception) {
            var concurrent = repository.findRequestByExternal(command.sourceSystemCode(), command.externalRequestId());
            if (concurrent.isPresent()) {
                return RegistrationResult.duplicate(concurrent.get(), "P12-EVC-001");
            }
            throw new P15BusinessException("P12-ERR-006", "外部申请幂等记录无法完成");
        }
        audit.append("P12-API-001", "P14-PERM-001", actor, "ALLOWED", "COMPLETED", request.id(), "OBJ-001",
                correlationId, "external request received");
        outbox.append("P12-EVC-001", request.id(), "OBJ-001", request.concurrencyVersion(), correlationId,
                command.payloadDigest(), actor.actorId());
        return RegistrationResult.created(request.id(), request.applicationNo(), request.lifecycleStateCode(),
                request.concurrencyVersion(), "P12-EVC-001");
    }

    @Transactional
    public RegistrationResult registerManual(ManualRegistrationCommand command) {
        ActorContext actor = authorization.require("P14-PERM-002");
        if (command.pathologyModalityCode() == null || command.pathologyModalityCode().isBlank()
                || command.reason() == null || command.reason().isBlank()) {
            throw new P15BusinessException("P12-ERR-007", "手工申请必须提供病理类型和建立原因");
        }
        String correlationId = UUID.randomUUID().toString();
        PathologyRequest request = repository.insertManual(command.pathologyModalityCode(), command.requestContent(),
                command.reason(), numberAllocator.applicationNumber(), actor.actorId(), Instant.now());
        audit.append("P12-API-002", "P14-PERM-002", actor, "ALLOWED", "COMPLETED", request.id(), "OBJ-001",
                correlationId, command.reason());
        outbox.append("P12-EVC-001", request.id(), "OBJ-001", request.concurrencyVersion(), correlationId,
                request.applicationNo(), actor.actorId());
        return RegistrationResult.created(request.id(), request.applicationNo(), request.lifecycleStateCode(),
                request.concurrencyVersion(), "P12-EVC-001");
    }

    @Transactional
    public RegistrationResult accept(UUID requestId, long expectedVersion) {
        ActorContext actor = authorization.require("P14-PERM-003");
        PathologyRequest request = repository.findRequest(requestId)
                .orElseThrow(() -> new P15BusinessException("P12-ERR-007", "申请不存在"));
        String correlationId = UUID.randomUUID().toString();
        long currentVersion = repository.currentRequestVersion(requestId);
        if (currentVersion != expectedVersion) {
            throw new P15BusinessException("P12-ERR-010", "申请版本冲突，请重新读取");
        }
        if (!repository.acceptRequest(requestId, expectedVersion, actor.actorId(), Instant.now())) {
            throw new P15BusinessException("P12-ERR-009", "申请当前状态不允许接受");
        }
        audit.append("P12-API-003", "P14-PERM-003", actor, "ALLOWED", "COMPLETED", requestId, "OBJ-001",
                correlationId, "application accepted");
        outbox.append("P12-EVC-001", requestId, "OBJ-001", expectedVersion + 1, correlationId, "accepted",
                actor.actorId());
        return RegistrationResult.created(requestId, request.applicationNo(), PathologyRequest.ESTABLISHED,
                expectedVersion + 1, "P12-EVC-001");
    }

    @Transactional
    public CaseResult establishCase(CaseCommand command) {
        ActorContext actor = authorization.require("P14-PERM-004");
        if (command.patientReference() == null || command.patientReference().isBlank()
                || command.pathologyModalityCode() == null || command.pathologyModalityCode().isBlank()) {
            throw new P15BusinessException("P12-ERR-011", "建立病例需要患者引用和病理类型");
        }
        PathologyRequest request = repository.findRequest(command.requestId())
                .orElseThrow(() -> new P15BusinessException("P12-ERR-010", "申请不存在"));
        if (!PathologyRequest.ESTABLISHED.equals(repository.findRequestState(command.requestId()))) {
            throw new P15BusinessException("P12-ERR-010", "申请尚未完成核对");
        }
        String correlationId = UUID.randomUUID().toString();
        var existing = repository.findCaseByRequest(command.requestId());
        if (existing.isPresent()) {
            return CaseResult.duplicate(existing.get(), "P12-EVC-002");
        }
        UUID snapshotId = repository.createSnapshot(command.patientReference(), command.visitReference(), actor.actorId(),
                Instant.now());
        PathologyCase pathologyCase = repository.insertCase(command.requestId(), numberAllocator.caseNumber(),
                command.pathologyModalityCode(), snapshotId, actor.hospitalScope(), actor.actorId(), Instant.now());
        audit.append("P12-API-004", "P14-PERM-004", actor, "ALLOWED", "COMPLETED", pathologyCase.id(), "OBJ-002",
                correlationId, "case established");
        outbox.append("P12-EVC-002", pathologyCase.id(), "OBJ-002", 0, correlationId, pathologyCase.caseNo(),
                actor.actorId());
        return CaseResult.created(pathologyCase.id(), pathologyCase.caseNo(), snapshotId, "P12-EVC-002");
    }

    private void validateExternal(ExternalRegistrationCommand command) {
        if (command.sourceSystemCode() == null || command.sourceSystemCode().isBlank()
                || command.externalRequestId() == null || command.externalRequestId().isBlank()
                || command.sourceMessageIdentity() == null || command.sourceMessageIdentity().isBlank()
                || command.payloadDigest() == null || command.payloadDigest().isBlank()
                || command.pathologyModalityCode() == null || command.pathologyModalityCode().isBlank()
                || (command.externalPatientId() == null || command.externalPatientId().isBlank())
                        && (command.externalVisitId() == null || command.externalVisitId().isBlank())) {
            throw new P15BusinessException("P12-ERR-001", "外部申请缺少来源、业务标识、消息身份、摘要或病理类型");
        }
        if (command.externalVisitId() != null && !command.externalVisitId().isBlank()
                && (command.externalPatientId() == null || command.externalPatientId().isBlank())) {
            throw new P15BusinessException("P12-ERR-005", "就诊引用必须关联患者引用");
        }
    }

    public record ExternalRegistrationCommand(String sourceSystemCode, String externalRequestId,
            String sourceMessageIdentity, String sourceMessageVersion, String payloadDigest,
            String rawPayloadReference, String externalPatientId, String externalVisitId,
            String pathologyModalityCode, String requestContent) {
    }

    public record ManualRegistrationCommand(String pathologyModalityCode, String requestContent, String reason) {
    }

    public record CaseCommand(UUID requestId, String patientReference, String visitReference,
            String pathologyModalityCode) {
    }

    public record RegistrationResult(UUID requestId, String applicationNo, String lifecycleStateCode,
            long concurrencyVersion, boolean duplicate, String eventTypeCode) {
        static RegistrationResult created(UUID id, String no, String state, long version, String event) {
            return new RegistrationResult(id, no, state, version, false, event);
        }

        static RegistrationResult duplicate(UUID id, String event) {
            return new RegistrationResult(id, null, "IDEMPOTENT_REPLAY", 0, true, event);
        }
    }

    public record CaseResult(UUID caseId, String caseNo, UUID snapshotId, boolean duplicate, String eventTypeCode) {
        static CaseResult created(UUID id, String no, UUID snapshot, String event) {
            return new CaseResult(id, no, snapshot, false, event);
        }

        static CaseResult duplicate(UUID id, String event) {
            return new CaseResult(id, null, null, true, event);
        }
    }
}
