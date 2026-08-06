package com.hanjisang.pis.specimen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.hanjisang.pis.specimen.infrastructure.JdbcSpecimenRepository;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:p15-test-schema.sql")
class JdbcSpecimenRepositoryTest {

    @Autowired
    private JdbcSpecimenRepository repository;

    @Test
    void receivingPersistsHistoryAndSameDigestIsIdempotent() {
        UUID caseId = UUID.randomUUID();
        var specimen = repository.insertExpected(caseId, "DEV-SP-TEST", "DEV-CNT-TEST", "TISSUE", "synthetic site",
                "SURGICAL", 1, "LOCAL_HOSPITAL", "test-actor", Instant.now());

        assertThat(repository.transitionToReceived(specimen.id(), 0, 1, "test-actor", "receipt-1", Instant.now()))
                .isTrue();
        assertThat(repository.hasReceivingFact(specimen.id(), "receipt-1")).isTrue();
        assertThat(repository.receivingQueue("LOCAL_HOSPITAL")).hasSize(0);
    }
}
