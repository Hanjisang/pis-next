package com.hanjisang.pis.v2.cytology;

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
class V50CytologyDiagnosisReportExistingDatabaseUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v50_cytology_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesV49WithVersionedCytologySchemasAndReportsWithoutPatientConclusions() {
        migrateTo("49");
        migrateTo("50");
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.diagnosis_template_version tv
                JOIN pis_v2.diagnosis_template t ON t.id = tv.template_id
                JOIN pis_v2.business_type bt ON bt.id = t.business_type_id
                WHERE tv.version_no = 2 AND tv.status_code = 'PUBLISHED'
                  AND bt.business_type_code IN ('CYTOLOGY_GYN', 'CYTOLOGY_NON_GYN', 'CYTOLOGY_FNA')
                """, Integer.class)).isEqualTo(3);
        String tbs = jdbc.queryForObject("""
                SELECT tv.schema_definition::text FROM pis_v2.diagnosis_template_version tv
                JOIN pis_v2.diagnosis_template t ON t.id = tv.template_id
                JOIN pis_v2.business_type bt ON bt.id = t.business_type_id
                WHERE bt.business_type_code = 'CYTOLOGY_GYN' AND tv.version_no = 2
                """, String.class);
        assertThat(tbs).contains("TBS-2014", "specimenAdequacy", "generalCategory", "interpretationResult")
                .doesNotContain("patientReference");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.report_template_version rv
                JOIN pis_v2.report_template rt ON rt.id = rv.template_id
                JOIN pis_v2.business_type bt ON bt.id = rt.business_type_id
                WHERE rv.version_no = 2 AND rv.status_code = 'PUBLISHED'
                  AND bt.business_type_code IN ('CYTOLOGY_GYN', 'CYTOLOGY_NON_GYN', 'CYTOLOGY_FNA')
                  AND rv.definition::text LIKE '%细胞学结构化结果%'
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT version_code FROM pis_v2.schema_metadata WHERE schema_code = 'PIS_V2'
                """, String.class)).isEqualTo("CYTOLOGY-DIAGNOSIS-REPORT-CLOSURE");
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration")
                .target(target).load().migrate();
    }
}
