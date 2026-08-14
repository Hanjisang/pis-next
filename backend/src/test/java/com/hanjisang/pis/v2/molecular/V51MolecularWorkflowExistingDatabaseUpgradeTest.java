package com.hanjisang.pis.v2.molecular;

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
class V51MolecularWorkflowExistingDatabaseUpgradeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v51_molecular_upgrade").withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesV50WithExecutionResultAttachmentsAttemptsAndVersionedTemplates() {
        migrateTo("50");
        migrateTo("51");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema='pis_v2' AND table_name='molecular_test'
                  AND column_name IN ('result_id','started_at','completed_by_ref','concurrency_version')
                """, String.class)).containsExactlyInAnyOrder("result_id", "started_at", "completed_by_ref", "concurrency_version");
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables WHERE table_schema='pis_v2'
                  AND table_name IN ('molecular_test_attachment','molecular_instrument_attempt','molecular_command_idempotency')
                """, String.class)).hasSize(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.diagnosis_template_version v
                JOIN pis_v2.diagnosis_template t ON t.id=v.template_id
                JOIN pis_v2.business_type bt ON bt.id=t.business_type_id
                WHERE bt.business_type_code='MOLECULAR' AND v.version_no=2 AND v.status_code='PUBLISHED'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.report_template_version v
                JOIN pis_v2.report_template t ON t.id=v.template_id
                JOIN pis_v2.business_type bt ON bt.id=t.business_type_id
                WHERE bt.business_type_code='MOLECULAR' AND v.version_no=2 AND v.status_code='PUBLISHED'
                  AND v.definition::text LIKE '%分子检测结果%'
                """, Integer.class)).isEqualTo(1);
    }

    private void migrateTo(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration")
                .target(target).load().migrate();
    }
}
