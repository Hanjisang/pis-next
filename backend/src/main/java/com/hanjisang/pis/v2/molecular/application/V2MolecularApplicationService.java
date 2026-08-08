package com.hanjisang.pis.v2.molecular.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.molecular.domain.MolecularResult;
import com.hanjisang.pis.v2.molecular.infrastructure.JdbcV2MolecularResultRepository;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2MolecularApplicationService {

    private static final String MOLECULAR_PERMISSION = "P14-PERM-014";

    private final JdbcV2MolecularResultRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public V2MolecularApplicationService(JdbcV2MolecularResultRepository repository,
            JdbcV2RegistrationRepository registrationRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public MolecularResultResult completeResult(UUID caseId, CompleteResultCommand command) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        require(caseId, "病例不能为空");
        require(command.resultCode(), "分子结果编码不能为空");
        require(command.resultData(), "分子结果数据不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!"MOLECULAR".equals(pathologyCase.businessTypeCode())) {
            throw reject("V2-MOLECULAR-CASE-REQUIRED", "独立分子结果只能进入分子病例");
        }
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能录入分子结果");
        }
        if (command.specimenId() != null) {
            registrationRepository.findSpecimen(command.specimenId(), actor.hospitalScope())
                    .filter(specimen -> specimen.caseId().equals(caseId) && !specimen.deleted())
                    .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "分子结果关联标本不属于当前病例"));
        }
        String operation = "PIS-V2-I06-MOLECULAR-RESULT";
        String digest = digest(caseId, command.specimenId(), command.resultCode(), command.resultData());
        var replay = repository.findIdempotency(operation, command.idempotencyKey()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) {
                throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的分子结果摘要冲突");
            }
            return result(repository.find(replay.resultId(), actor.hospitalScope()).orElseThrow(), true);
        }
        UUID resultId = UUID.randomUUID();
        Instant now = Instant.now();
        MolecularResult result = MolecularResult.completed(resultId, caseId, command.specimenId(),
                command.resultCode(), command.resultData(), now, actor.actorId());
        repository.insert(result, actor.hospitalScope(), actor.actorId(), now);
        if (!repository.insertIdempotency(operation, command.idempotencyKey(), digest, resultId, actor.actorId(), now)) {
            throw reject("V2-IDEMPOTENCY-RETRY", "分子结果命令正在由其他请求处理，请重试");
        }
        audit.append(operation, MOLECULAR_PERMISSION, actor, "ALLOWED", "COMPLETED", resultId,
                "V2-MOLECULAR-RESULT", UUID.randomUUID().toString(), "独立分子结果已记录");
        outbox.append("V2-I06-MOLECULAR-RESULT-COMPLETED", resultId, "V2-MOLECULAR-RESULT", 0,
                UUID.randomUUID().toString(), digest, actor.actorId());
        return result(result, false);
    }

    @Transactional(readOnly = true)
    public MolecularResultResult getResult(UUID resultId) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        return result(repository.find(resultId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-MOLECULAR-RESULT-NOT-FOUND", "分子结果不存在")), false);
    }

    private MolecularResultResult result(MolecularResult result, boolean duplicate) {
        return new MolecularResultResult(result.id(), result.caseId(), result.specimenId(), result.resultCode(),
                result.resultData(), result.statusCode(), result.completedAt(), result.completedBy(), duplicate);
    }

    private static void require(Object value, String message) {
        if (value == null || value.toString().isBlank()) {
            throw reject("V2-INVALID-REQUEST", message);
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    private static String digest(Object... values) {
        try {
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record CompleteResultCommand(UUID specimenId, String resultCode, String resultData,
            String idempotencyKey) { }

    public record MolecularResultResult(UUID resultId, UUID caseId, UUID specimenId, String resultCode,
            String resultData, String statusCode, Instant completedAt, String completedBy, boolean duplicate) { }
}
