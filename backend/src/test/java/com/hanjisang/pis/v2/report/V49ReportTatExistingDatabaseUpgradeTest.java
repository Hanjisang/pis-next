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
class V49ReportTatExistingDatabaseUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v49_report_tat_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesV48WithHospitalScopedTatPolicyAndDelayFactsWithoutInventedThresholds() {
        migrateTo("48");
        migrateTo("49");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('report_tat_policy', 'report_delay_declaration')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.report_tat_policy", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'pis_v2' AND indexname = 'uq_v2_report_delay_active'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT version_code FROM pis_v2.schema_metadata WHERE schema_code = 'PIS_V2'
                """, String.class)).isEqualTo("REPORT-TAT-DELAY-CLOSURE");
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration")
                .target(target).load().migrate();
    }
}
