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
class V48ReportTemplateDesignerExistingDatabaseUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v48_report_template_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesV47WithVersionedTumorPresetsAndTemplateSourceTraceability() {
        migrateTo("47");
        migrateTo("48");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.report_template_preset", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT preset_code FROM pis_v2.report_template_preset", String.class))
                .containsExactlyInAnyOrder("TUMOR-LUNG", "TUMOR-BREAST", "TUMOR-COLORECTAL");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = 'pis_v2' AND table_name = 'report_template'
                   AND column_name = 'source_preset_code'
                """, Integer.class)).isEqualTo(1);
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration")
                .target(target).load().migrate();
    }
}
