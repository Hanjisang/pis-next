package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V2RegistrationPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis")
            .withUsername("pis")
            .withPassword("synthetic-v2-i01");

    @Test
    void postgresMigrationCreatesV2I01ASchemaAndSeedConfiguration() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("SELECT version_code FROM pis_v2.schema_metadata WHERE schema_code = 'PIS_V2'",
                String.class)).isEqualTo("V2-I01A");
        assertThat(jdbc.queryForObject("SELECT version FROM pis.flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("12");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.business_type", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.application_item_mapping", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_number_rule", Integer.class))
                .isEqualTo(16);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('case_state_history', 'specimen_state_history',
                                     'specimen_receipt_fact', 'specimen_exception')
                """, Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'pis_v2'
                  AND indexname IN ('uq_v2_specimen_code_active', 'uq_v2_specimen_label_active')
                """, Integer.class)).isEqualTo(2);

        UUID caseResult = UUID.randomUUID();
        int inserted = jdbc.update("""
                MERGE INTO pis_v2.idempotency_record AS target
                USING (VALUES (?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS UUID),
                               CAST(? AS TIMESTAMP WITH TIME ZONE), ?)) AS incoming
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code,
                     result_case_id, result_specimen_id, created_at, created_by_ref)
                ON target.operation_code = incoming.operation_code
                   AND target.idempotency_key = incoming.idempotency_key
                WHEN NOT MATCHED THEN INSERT
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code,
                     result_case_id, result_specimen_id, created_at, created_by_ref)
                VALUES (incoming.id, incoming.operation_code, incoming.idempotency_key, incoming.payload_digest,
                        incoming.result_kind_code, incoming.result_case_id, incoming.result_specimen_id,
                        incoming.created_at, incoming.created_by_ref)
                """, UUID.randomUUID(), "V2-TEST", "same-key", "digest", "CASE", caseResult, null,
                java.sql.Timestamp.from(java.time.Instant.now()), "TEST");
        assertThat(inserted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.idempotency_record WHERE operation_code = 'V2-TEST'",
                Integer.class)).isEqualTo(1);
    }
}
