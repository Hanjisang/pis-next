package com.hanjisang.pis.accession;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.accession.domain.PathologyRequest;

class PathologyRequestTest {

    @Test
    void onlyWaitingRequestCanBeAccepted() {
        PathologyRequest request = PathologyRequest.received(UUID.randomUUID(), "DEV-REQ-1", "TEST", "HISTOLOGY",
                Instant.now());

        request.accept();

        org.assertj.core.api.Assertions.assertThat(request.lifecycleStateCode())
                .isEqualTo(PathologyRequest.ESTABLISHED);
        org.assertj.core.api.Assertions.assertThat(request.concurrencyVersion()).isEqualTo(1);
        assertThatThrownBy(request::accept).isInstanceOf(IllegalStateException.class);
    }
}
