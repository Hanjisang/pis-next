package com.hanjisang.pis.v2.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V45DiagnosisExistingDatabaseUpgradeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v45_diagnosis_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesLegacyAssignmentRulesAndAddsCapacityFactsWithoutLosingConfiguration() {
        migrateTo("44");
        JdbcTemplate jdbc = jdbc();
        UUID ruleId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.assignment_rule
                    (id, organization_reference, campus_code, business_type_code, department_code, site_code,
                     diagnosis_group_code, doctor_id, priority, enabled, concurrency_version, created_at,
                     created_by_ref, updated_at, updated_by_ref)
                VALUES (?, 'LOCAL_HOSPITAL', 'MAIN', 'HISTOLOGY', '*', '*', 'LEGACY-GROUP', NULL, 0, TRUE, 0,
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, ruleId, now, now);

        migrateTo("45");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.assignment_rule WHERE id = ?", Integer.class,
                ruleId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT enabled FROM pis_v2.assignment_rule WHERE id = ?", Boolean.class,
                ruleId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT daily_case_limit FROM pis_v2.assignment_rule WHERE id = ?",
                Integer.class, ruleId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'pis_v2' AND table_name = 'diagnosis_auto_assignment_fact'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                  FROM pg_constraint
                 WHERE conname = 'ck_v2_responsibility_source'
                """, String.class)).contains("AUTO");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint
                 WHERE conname IN ('ck_v2_assignment_rule_daily_limit', 'ck_v2_assignment_rule_enabled_doctor',
                                   'ck_v2_auto_assignment_count', 'ck_v2_auto_assignment_limit')
                """, Integer.class)).isEqualTo(4);
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration")
                .target(target).load().migrate();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
