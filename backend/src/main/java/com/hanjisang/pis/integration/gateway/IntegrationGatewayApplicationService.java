package com.hanjisang.pis.integration.gateway;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter.AdapterResult;

@Service
public class IntegrationGatewayApplicationService {

    private static final int DEFAULT_MAX_RETRIES = 3;

    private final IntegrationMessageMapper mapper;
    private final IntegrationAdapterRegistry adapters;
    private final IntegrationMessageLogStore logs;

    public IntegrationGatewayApplicationService(IntegrationMessageMapper mapper, IntegrationAdapterRegistry adapters,
            IntegrationMessageLogStore logs) {
        this.mapper = mapper;
        this.adapters = adapters;
        this.logs = logs;
    }

    public DispatchResult dispatch(String adapterCode, IntegrationRequestDto request) {
        IntegrationEnvelope envelope = mapper.map(request);
        IntegrationMessageLog existing = logs.findByMessageIdentity(envelope).orElse(null);
        if (existing != null) {
            if (!existing.envelope().requestDigest().equals(envelope.requestDigest())) {
                throw new IllegalArgumentException("相同消息 ID 的请求摘要冲突");
            }
            return DispatchResult.from(existing, true);
        }
        IntegrationMessageLog created = logs.createPending(envelope, DEFAULT_MAX_RETRIES, Instant.now());
        return DispatchResult.from(attempt(created.id(), created.retryCount(), adapterCode), false);
    }

    public DispatchResult retry(UUID messageLogId, String adapterCode) {
        IntegrationMessageLog current = logs.findById(messageLogId)
                .orElseThrow(() -> new IllegalArgumentException("接口消息日志不存在"));
        if (current.status() == IntegrationMessageLog.Status.SUCCEEDED) {
            return DispatchResult.from(current, true);
        }
        if (current.status() == IntegrationMessageLog.Status.SENDING) {
            throw new DeliveryInProgressException();
        }
        if (current.status() == IntegrationMessageLog.Status.DEAD_LETTER) {
            throw new IllegalStateException("死信必须先经过受控重放审批");
        }
        return DispatchResult.from(attempt(current.id(), current.retryCount(), adapterCode), false);
    }

    public List<IntegrationMessageLogStore.IntegrationAttempt> attempts(UUID messageLogId) {
        logs.findById(messageLogId).orElseThrow(() -> new IllegalArgumentException("接口消息日志不存在"));
        return logs.findAttempts(messageLogId);
    }

    public void requestReplay(UUID messageLogId, String requestedByRef, String reason) {
        if (requestedByRef == null || requestedByRef.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("人工重放必须记录申请人和原因");
        }
        logs.findById(messageLogId).orElseThrow(() -> new IllegalArgumentException("接口消息日志不存在"));
        logs.requestReplay(messageLogId, requestedByRef.trim(), reason.trim(), Instant.now());
    }

    private IntegrationMessageLog attempt(UUID messageLogId, int expectedRetryCount, String adapterCode) {
        IntegrationMessageLog current = logs.claimAttempt(messageLogId, expectedRetryCount, Instant.now())
                .orElseThrow(DeliveryInProgressException::new);
        IntegrationAdapter adapter = adapters.require(adapterCode, current.envelope().capability());
        Instant startedAt = Instant.now();
        AdapterResult result;
        try {
            result = adapter.exchange(current.envelope(), current.retryCount() + 1);
        } catch (RuntimeException exception) {
            result = AdapterResult.failure(true, "ADAPTER_EXCEPTION", safeMessage(exception));
        }
        return logs.recordAttempt(current, adapterCode, result, startedAt, Instant.now());
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    public record DispatchResult(UUID messageLogId, String statusCode, int retryCount, boolean duplicate,
            String responseSummary, String errorCode, String errorMessage) {
        static DispatchResult from(IntegrationMessageLog log, boolean duplicate) {
            return new DispatchResult(log.id(), log.status().name(), log.retryCount(), duplicate,
                    log.responseSummary(), log.errorCode(), log.errorMessage());
        }
    }

    public static final class DeliveryInProgressException extends IllegalStateException {
        public DeliveryInProgressException() { super("接口消息正在发送"); }
    }
}
