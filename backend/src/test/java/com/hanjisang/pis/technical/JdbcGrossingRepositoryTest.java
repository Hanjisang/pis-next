package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.hanjisang.pis.technical.infrastructure.JdbcGrossingRepository;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:p16-test-schema.sql")
class JdbcGrossingRepositoryTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JdbcGrossingRepository repository;
    private UUID caseId;
    private UUID specimenId;

    @BeforeEach
    void seedReceivedSpecimen() {
        caseId = UUID.randomUUID();
        specimenId = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.pathology_case(id, case_no, organization_reference) VALUES (?, ?, ?)", caseId,
                "DEV-CASE-JDBC", "LOCAL_HOSPITAL");
        jdbc.update("""
                INSERT INTO pis.specimen
                (id, case_id, specimen_no, specimen_kind_code, specimen_source_code, collection_site_text,
                 collection_method_code, specimen_lifecycle_state_code, record_version_no, concurrency_version,
                 organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, 'TISSUE', 'LOCAL', ?, 'SURGICAL', ?, 1, 1, ?, CURRENT_TIMESTAMP, 'test-actor')
                """, specimenId, caseId, "DEV-SP-JDBC", "synthetic site", "P08-SM-003-ST-03", "LOCAL_HOSPITAL");
    }

    @Test
    void sourceAndBlockRelationsUseStableIdsAndUniqueNumbers() {
        var batch = repository.createBatch(specimenId, "DEV-GROSS-JDBC", "LOCAL_HOSPITAL", "test-actor", Instant.now());
        var block = repository.insertBlock(batch.id(), specimenId, caseId, "DEV-BLOCK-JDBC", "ROUTINE", "TISSUE",
                "DEV-BOX-JDBC", "LOCAL_HOSPITAL", "test-actor", Instant.now());
        assertThat(block.id()).isNotEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        assertThat(block.blockNo()).isEqualTo("DEV-BLOCK-JDBC");
        assertThat(repository.batchContainsSpecimen(batch.id(), specimenId, "LOCAL_HOSPITAL")).isTrue();
    }
}
