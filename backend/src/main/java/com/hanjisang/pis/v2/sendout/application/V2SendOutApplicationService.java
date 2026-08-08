package com.hanjisang.pis.v2.sendout.application;

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
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;
import com.hanjisang.pis.v2.sendout.domain.SendOut;
import com.hanjisang.pis.v2.sendout.infrastructure.JdbcV2SendOutRepository;

@Service
public class V2SendOutApplicationService {

    private static final String SEND_OUT_PERMISSION = "P14-PERM-014";

    private final JdbcV2SendOutRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public V2SendOutApplicationService(JdbcV2SendOutRepository repository,
            JdbcV2RegistrationRepository registrationRepository, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public SendOutResult request(UUID caseId, RequestCommand command) {
        ActorContext actor = authorization.require(SEND_OUT_PERMISSION);
        require(caseId, "病例不能为空");
        require(command.externalReference(), "外送引用不能为空");
        require(command.destinationName(), "外送机构不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        activeCase(caseId, actor);
        String operation = "PIS-V2-I06-SEND-OUT-REQUEST";
        String digest = digest(caseId, command.externalReference(), command.destinationName());
        var replay = repository.findIdempotency(operation, command.idempotencyKey()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) {
                throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的外送摘要冲突");
            }
            return result(repository.find(replay.sendOutId(), actor.hospitalScope()).orElseThrow(), true);
        }
        Instant now = Instant.now();
        SendOut sendOut = SendOut.requested(UUID.randomUUID(), caseId, command.externalReference(),
                command.destinationName(), now, actor.actorId());
        repository.insert(sendOut, actor.hospitalScope(), actor.actorId(), now);
        if (!repository.insertIdempotency(operation, command.idempotencyKey(), digest, sendOut.id(), actor.actorId(), now)) {
            throw reject("V2-IDEMPOTENCY-RETRY", "外送命令正在由其他请求处理，请重试");
        }
        audit.append(operation, SEND_OUT_PERMISSION, actor, "ALLOWED", "COMPLETED", sendOut.id(), "V2-SEND-OUT",
                UUID.randomUUID().toString(), "病例外送已登记");
        outbox.append("V2-I06-SEND-OUT-REQUESTED", sendOut.id(), "V2-SEND-OUT", 0,
                UUID.randomUUID().toString(), digest, actor.actorId());
        return result(sendOut, false);
    }

    @Transactional
    public SendOutResult receiveResult(UUID sendOutId, ReceiveResultCommand command) {
        ActorContext actor = authorization.require(SEND_OUT_PERMISSION);
        require(sendOutId, "外送记录不能为空");
        require(command.resultData(), "外送结果不能为空");
        SendOut sendOut = repository.find(sendOutId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SEND-OUT-NOT-FOUND", "外送记录不存在"));
        if (!SendOut.REQUESTED.equals(sendOut.statusCode())) {
            throw reject("V2-SEND-OUT-CLOSED", "外送记录已接收结果或已结束");
        }
        SendOut received = sendOut.withResult(command.resultData(), Instant.now(), actor.actorId());
        if (!repository.updateResult(received, actor.hospitalScope())) {
            throw reject("V2-SEND-OUT-CONFLICT", "外送记录状态已被其他请求改变");
        }
        audit.append("PIS-V2-I06-SEND-OUT-RESULT", SEND_OUT_PERMISSION, actor, "ALLOWED", "COMPLETED",
                sendOutId, "V2-SEND-OUT", UUID.randomUUID().toString(), "病例外送结果已接收");
        outbox.append("V2-I06-SEND-OUT-RESULT-RECEIVED", sendOutId, "V2-SEND-OUT", 0,
                UUID.randomUUID().toString(), digest(sendOutId, command.resultData()), actor.actorId());
        return result(received, false);
    }

    @Transactional(readOnly = true)
    public SendOutResult get(UUID sendOutId) {
        ActorContext actor = authorization.require("P14-PERM-048");
        return result(repository.find(sendOutId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-SEND-OUT-NOT-FOUND", "外送记录不存在")), false);
    }

    private Case activeCase(UUID caseId, ActorContext actor) {
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能登记外送");
        }
        return pathologyCase;
    }

    private static SendOutResult result(SendOut sendOut, boolean duplicate) {
        return new SendOutResult(sendOut.id(), sendOut.caseId(), sendOut.externalReference(),
                sendOut.destinationName(), sendOut.statusCode(), sendOut.requestedAt(), sendOut.resultData(), duplicate);
    }

    private static void require(Object value, String message) {
        if (value == null || value.toString().isBlank()) throw reject("V2-INVALID-REQUEST", message);
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

    public record RequestCommand(String externalReference, String destinationName, String idempotencyKey) { }
    public record ReceiveResultCommand(String resultData) { }
    public record SendOutResult(UUID sendOutId, UUID caseId, String externalReference, String destinationName,
            String statusCode, Instant requestedAt, String resultData, boolean duplicate) { }
}
