package com.hanjisang.pis.v2.frozen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies that the published Frozen facts survive all later migrations. */
@Testcontainers
class V39FrozenExistingDatabaseUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v39_frozen_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesV38FrozenFactsToV39WithoutChangingExistingIdentity() {
        migrateTo("38");
        JdbcTemplate jdbc = jdbc();
        Fixture fixture = seedV38FrozenFacts(jdbc);
        Map<String, Integer> before = counts(jdbc);

        migrateTo(null);

        assertThat(jdbc.queryForObject(
                "SELECT version FROM pis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("49");
        assertThat(counts(jdbc)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT round_no FROM pis_v2.frozen_round WHERE id = ?", Integer.class,
                fixture.roundId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'pis_v2' AND table_name = 'frozen_round'
                  AND column_name IN ('cancelled_at', 'cancelled_by_ref', 'cancellation_reason')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name = 'frozen_end_specimen'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'pis_v2' AND indexname = 'uq_v2_case_frozen_source'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'pis_v2' AND indexname = 'uq_v2_frozen_round_specimen_global'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'pis_v2' AND indexname = 'idx_v2_integration_retry_claim'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name = 'frozen_tat_alert_action'
                """, Integer.class)).isEqualTo(1);
    }

    private void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private Map<String, Integer> counts(JdbcTemplate jdbc) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("pathology_case", jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_case", Integer.class));
        result.put("specimen", jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen", Integer.class));
        result.put("frozen_round", jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.frozen_round", Integer.class));
        result.put("frozen_round_specimen",
                jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.frozen_round_specimen", Integer.class));
        return result;
    }

    private Fixture seedV38FrozenFacts(JdbcTemplate jdbc) {
        UUID frozenType = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'FROZEN'", UUID.class);
        UUID caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'UPGRADE', ?, 'SYNTH-FROZEN', ?, 'ACTIVE', TRUE, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test')
                """, caseId, "F-V38-" + caseId, "APP-FC03C-" + caseId, frozenType, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_name, specimen_kind_code, source_kind_code,
                     source_reference, collection_site, collection_method_code, label_code, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, 'F-V38-S1', 'Synthetic frozen specimen', 'TISSUE', 'APPLICATION', ?,
                        'Synthetic site', 'FROZEN', ?, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, specimenId, caseId, "FS-V38-" + specimenId, "APP-FC03C-" + caseId,
                "LBL-FC03C-" + specimenId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.frozen_round
                    (id, case_id, round_no, status_code, arrival_time, registered_at, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 1, 'OPEN', ?, ?, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test')
                """, roundId, caseId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.frozen_round_specimen
                    (frozen_round_id, specimen_id, sequence_no, linked_at, linked_by_ref)
                VALUES (?, ?, 1, ?, 'upgrade-test')
                """, roundId, specimenId, now);
        return new Fixture(caseId, specimenId, roundId);
    }

    private record Fixture(UUID caseId, UUID specimenId, UUID roundId) { }
}
