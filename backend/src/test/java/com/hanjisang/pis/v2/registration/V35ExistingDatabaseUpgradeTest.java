package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V35ExistingDatabaseUpgradeTest {

    private static final List<String> PRESERVED_TABLES = List.of(
            "pathology_application", "pathology_application_item", "pathology_application_case",
            "pathology_case", "case_context_snapshot", "specimen", "diagnosis", "report");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v35_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesRepresentativeV34RegistrationDataToV35WithoutLosingMedicalFacts() {
        migrateTo("34");
        JdbcTemplate jdbc = jdbc();
        Seed seed = seedRepresentativeV34Data(jdbc);
        Map<String, Integer> before = counts(jdbc);

        migrateTo("35");

        assertThat(jdbc.queryForObject(
                "SELECT version FROM pis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("35");
        assertThat(counts(jdbc)).isEqualTo(before);
        assertThat(before.values()).allMatch(count -> count > 0);
        assertThat(jdbc.queryForObject(
                "SELECT case_no FROM pis_v2.pathology_case WHERE id = ?", String.class, seed.caseId()))
                .isEqualTo("P-UPGRADE-001");
        assertThat(jdbc.queryForObject(
                "SELECT specimen_code FROM pis_v2.specimen WHERE id = ?", String.class, seed.specimenId()))
                .isEqualTo("SP-UPGRADE-001");
        assertThat(jdbc.queryForObject(
                "SELECT report_no FROM pis_v2.report WHERE id = ?", String.class, seed.reportId()))
                .isEqualTo("RP-UPGRADE-001");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name IN
                    ('pathology_number_history', 'pathology_registration_label_print')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'pis_v2' AND table_name = 'pathology_application'
                  AND column_name IN ('patient_info_source_code', 'ward_reference', 'bed_reference', 'age_unit_code')
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'pis_v2' AND indexname = 'uq_v2_case_no_active'
                """, Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                SELECT ?, case_no, 'UPGRADE-TEST', 'DUPLICATE-ACTIVE', 'ITEM-DUPLICATE',
                       business_type_id, 'ACTIVE', TRUE, 0, organization_reference, CURRENT_TIMESTAMP, 'upgrade-test'
                  FROM pis_v2.pathology_case WHERE id = ?
                """, UUID.randomUUID(), seed.caseId())).isInstanceOf(DataAccessException.class);
    }

    private static void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static Map<String, Integer> counts(JdbcTemplate jdbc) {
        Map<String, Integer> result = new LinkedHashMap<>();
        PRESERVED_TABLES.forEach(table -> result.put(table,
                jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2." + table, Integer.class)));
        return result;
    }

    private static Seed seedRepresentativeV34Data(JdbcTemplate jdbc) {
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'", UUID.class);
        UUID mappingId = jdbc.queryForObject("""
                SELECT id FROM pis_v2.application_item_mapping
                WHERE application_item_code = 'SYNTH-HISTOLOGY'
                """, UUID.class);
        UUID diagnosisTemplateVersionId = jdbc.queryForObject("""
                SELECT dtv.id FROM pis_v2.diagnosis_template_version dtv
                JOIN pis_v2.diagnosis_template dt ON dt.id = dtv.template_id
                WHERE dt.business_type_id = ? AND dtv.status_code = 'PUBLISHED' LIMIT 1
                """, UUID.class, businessTypeId);
        UUID reportTemplateVersionId = jdbc.queryForObject("""
                SELECT rtv.id FROM pis_v2.report_template_version rtv
                JOIN pis_v2.report_template rt ON rt.id = rtv.template_id
                WHERE rt.business_type_id = ? AND rtv.status_code = 'PUBLISHED' LIMIT 1
                """, UUID.class, businessTypeId);
        UUID applicationId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbc.update("""
                INSERT INTO pis_v2.pathology_application
                    (id, application_no, source_type_code, source_system_code, patient_reference,
                     patient_name, visit_reference, visit_type_code, application_department,
                     applicant_reference, applied_at, status_code, organization_reference,
                     concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, 'APP-UPGRADE-001', 'MANUAL', 'UPGRADE-TEST', 'SYNTHETIC-PATIENT',
                        'Synthetic Patient', 'MZ-UPGRADE-001', 'OUTPATIENT', 'SYNTH-DEPARTMENT',
                        'SYNTH-DOCTOR', ?, 'REGISTERED', 'LOCAL_HOSPITAL', 0,
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, applicationId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_application_item
                    (id, application_id, external_item_code, item_name, mapping_id, business_type_id,
                     specimen_kind_code, specimen_description, sequence_no, status_code, created_at, created_by_ref)
                VALUES (?, ?, 'SYNTH-HISTOLOGY', 'Synthetic histology', ?, ?, 'TISSUE',
                        'Synthetic specimen', 1, 'REGISTERED', ?, 'upgrade-test')
                """, itemId, applicationId, mappingId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, 'P-UPGRADE-001', 'UPGRADE-TEST', 'APP-UPGRADE-001', 'SYNTH-HISTOLOGY',
                        ?, 'ACTIVE', TRUE, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test')
                """, caseId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_application_case
                    (id, application_id, application_item_id, case_id, linked_at, linked_by_ref)
                VALUES (?, ?, ?, ?, ?, 'upgrade-test')
                """, UUID.randomUUID(), applicationId, itemId, caseId, now);
        jdbc.update("""
                INSERT INTO pis_v2.case_context_snapshot
                    (id, case_id, patient_reference, visit_reference, snapshot_version_no, captured_at, captured_by_ref)
                VALUES (?, ?, 'SYNTHETIC-PATIENT', 'MZ-UPGRADE-001', 1, ?, 'upgrade-test')
                """, UUID.randomUUID(), caseId, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_kind_code, source_kind_code,
                     source_reference, collection_site, collection_method_code, label_code,
                     concurrency_version, organization_reference, created_at, created_by_ref,
                     updated_at, updated_by_ref)
                VALUES (?, ?, 'SP-UPGRADE-001', 'SP-UPGRADE-001', 'TISSUE', 'APPLICATION',
                        'APP-UPGRADE-001', 'SYNTHETIC-SITE', 'SURGERY', 'LBL-UPGRADE-001', 0,
                        'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, specimenId, caseId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.diagnosis
                    (id, case_id, context_type, context_id, template_version_id, structured_data,
                     microscopic_description, diagnosis_text, concurrency_version, organization_reference,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'CASE', ?, ?, '{}'::jsonb, 'Synthetic microscopy', 'Synthetic diagnosis',
                        0, 'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, diagnosisId, caseId, caseId, diagnosisTemplateVersionId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.report
                    (id, report_no, organization_reference, case_id, diagnosis_id, template_version_id,
                     report_nature_code, status_code, diagnosis_snapshot, responsibility_snapshot,
                     case_snapshot, material_snapshot, technical_result_snapshot, rendered_content,
                     rendered_content_hash, pdf_file_reference, pdf_content_hash, signed_by_ref,
                     signed_at, concurrency_version, created_at, created_by_ref)
                VALUES (?, 'RP-UPGRADE-001', 'LOCAL_HOSPITAL', ?, ?, ?, 'ORIGINAL', 'EFFECTIVE',
                        '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
                        'Synthetic report', 'rendered-hash', 'synthetic/report.pdf', 'pdf-hash',
                        'doctor-upgrade', ?, 0, ?, 'upgrade-test')
                """, reportId, caseId, diagnosisId, reportTemplateVersionId, now, now);
        return new Seed(caseId, specimenId, reportId);
    }

    private record Seed(UUID caseId, UUID specimenId, UUID reportId) { }
}
