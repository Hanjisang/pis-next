package com.hanjisang.pis.v2.report;

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
class V47ReportOutputExistingDatabaseUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v47_report_output_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesV46WithOutputEvidenceAndIdempotency() {
        migrateTo("46");
        migrateTo("47");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'pis_v2' AND table_name = 'report_distribution'
                   AND column_name IN ('requested_by_ref', 'delivery_reference', 'error_code')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'pis_v2' AND table_name = 'report_print_record'
                   AND column_name IN ('requested_by_ref', 'device_job_reference', 'error_code', 'failure_reason')
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'pis_v2' AND table_name = 'report_output_command_idempotency'
                """, Integer.class)).isEqualTo(1);
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration")
                .target(target).load().migrate();
    }
}
