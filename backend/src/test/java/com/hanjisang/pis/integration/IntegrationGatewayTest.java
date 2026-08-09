package com.hanjisang.pis.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.integration.gateway.IntegrationAdapter.AdapterResult;
import com.hanjisang.pis.integration.gateway.IntegrationAdapterRegistry;
import com.hanjisang.pis.integration.gateway.IntegrationEnvelope;
import com.hanjisang.pis.integration.gateway.IntegrationGatewayApplicationService;
import com.hanjisang.pis.integration.gateway.IntegrationMessageLog;
import com.hanjisang.pis.integration.gateway.IntegrationMessageLog.Status;
import com.hanjisang.pis.integration.gateway.IntegrationMessageLogStore;
import com.hanjisang.pis.integration.gateway.IntegrationMessageMapper;
import com.hanjisang.pis.integration.gateway.IntegrationRequestDto;
import com.hanjisang.pis.integration.gateway.mock.MockHisAdapter;
import com.hanjisang.pis.integration.gateway.mock.MockReportDeliveryAdapter;

class IntegrationGatewayTest {

    @Test
    void hisAdapterMapsAndProcessesIdempotentlyWithoutExposingExternalDtoToDomain() {
        InMemoryLogStore logs = new InMemoryLogStore();
        IntegrationGatewayApplicationService service = service(logs);
        IntegrationRequestDto request = request("HIS-ORDER-001", "ORDER_RECEIVE", "mock://his/order/1", "digest-1");

        var first = service.dispatch("MOCK_HIS", request);
        var replay = service.dispatch("MOCK_HIS", request);

        assertThat(first.statusCode()).isEqualTo("SUCCEEDED");
        assertThat(first.responseSummary()).isEqualTo("HIS_ACCEPTED");
        assertThat(replay.messageLogId()).isEqualTo(first.messageLogId());
        assertThat(replay.duplicate()).isTrue();
        assertThat(logs.messages).hasSize(1);
        assertThatThrownBy(() -> service.dispatch("MOCK_HIS",
                request("HIS-ORDER-001", "ORDER_RECEIVE", "mock://his/order/1", "changed-digest")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("摘要冲突");
    }

    @Test
    void reportDeliveryFailureRetriesAndDeadLettersWithoutChangingSignedBusinessFact() {
        InMemoryLogStore logs = new InMemoryLogStore();
        IntegrationGatewayApplicationService service = service(logs);
        IntegrationRequestDto request = request("REPORT-SIGNED-001", "REPORT_DELIVERY",
                "mock://fail/report/R001", "signed-report-digest");

        var first = service.dispatch("MOCK_REPORT_DELIVERY", request);
        var second = service.retry(first.messageLogId(), "MOCK_REPORT_DELIVERY");
        var third = service.retry(first.messageLogId(), "MOCK_REPORT_DELIVERY");

        assertThat(first.statusCode()).isEqualTo("RETRY_PENDING");
        assertThat(second.statusCode()).isEqualTo("RETRY_PENDING");
        assertThat(third.statusCode()).isEqualTo("DEAD_LETTER");
        assertThat(third.retryCount()).isEqualTo(3);
        assertThat(logs.findById(first.messageLogId()).orElseThrow().envelope().businessKey())
                .isEqualTo("CASE-R001-SIGNED");
        assertThatThrownBy(() -> service.retry(first.messageLogId(), "MOCK_REPORT_DELIVERY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重放审批");
    }

    private static IntegrationGatewayApplicationService service(InMemoryLogStore logs) {
        return new IntegrationGatewayApplicationService(new IntegrationMessageMapper(),
                new IntegrationAdapterRegistry(List.of(new MockHisAdapter(), new MockReportDeliveryAdapter())), logs);
    }

    private static IntegrationRequestDto request(String messageId, String capability, String reference,
            String digest) {
        return new IntegrationRequestDto("HOSPITAL_A", "OUTBOUND", "PIS", "SYNTH-HIS", messageId,
                capability, "CASE-R001-SIGNED", reference, digest, Instant.parse("2026-08-09T00:00:00Z"));
    }

    private static final class InMemoryLogStore implements IntegrationMessageLogStore {

        private final Map<UUID, IntegrationMessageLog> messages = new LinkedHashMap<>();

        @Override
        public Optional<IntegrationMessageLog> findByMessageIdentity(IntegrationEnvelope envelope) {
            return messages.values().stream().filter(item -> sameIdentity(item.envelope(), envelope)).findFirst();
        }

        @Override
        public Optional<IntegrationMessageLog> findById(UUID id) {
            return Optional.ofNullable(messages.get(id));
        }

        @Override
        public IntegrationMessageLog createPending(IntegrationEnvelope envelope, int maxRetries, Instant now) {
            IntegrationMessageLog log = new IntegrationMessageLog(UUID.randomUUID(), envelope, Status.PENDING,
                    null, null, null, 0, maxRetries, null, null, now, now);
            messages.put(log.id(), log);
            return log;
        }

        @Override
        public IntegrationMessageLog recordAttempt(IntegrationMessageLog current, String adapterCode,
                AdapterResult result, Instant startedAt, Instant completedAt) {
            int retries = current.retryCount() + 1;
            Status status = result.succeeded() ? Status.SUCCEEDED
                    : result.retryable() && retries < current.maxRetries() ? Status.RETRY_PENDING : Status.DEAD_LETTER;
            IntegrationMessageLog updated = new IntegrationMessageLog(current.id(), current.envelope(), status,
                    result.responseSummary(), result.errorCode(), result.errorMessage(), retries,
                    current.maxRetries(), status == Status.RETRY_PENDING ? completedAt.plusSeconds(60) : null,
                    completedAt, current.createdAt(), completedAt);
            messages.put(updated.id(), updated);
            return updated;
        }

        @Override
        public void requestReplay(UUID messageLogId, String requestedByRef, String reason, Instant now) {
            if (!messages.containsKey(messageLogId)) throw new IllegalArgumentException("message missing");
        }

        private static boolean sameIdentity(IntegrationEnvelope left, IntegrationEnvelope right) {
            return left.hospitalProfileCode().equals(right.hospitalProfileCode())
                    && left.sourceSystemCode().equals(right.sourceSystemCode())
                    && left.targetSystemCode().equals(right.targetSystemCode())
                    && left.messageId().equals(right.messageId());
        }
    }
}
