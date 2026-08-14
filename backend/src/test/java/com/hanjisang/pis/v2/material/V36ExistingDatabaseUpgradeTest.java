package com.hanjisang.pis.v2.material;

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
class V36ExistingDatabaseUpgradeTest {

    private static final List<String> PRESERVED_TABLES = List.of(
            "pathology_case", "specimen", "grossing", "grossing_specimen", "block", "slide", "diagnosis", "report");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v36_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesRepresentativeV36MaterialChainToLatestWithoutLosingMedicalFacts() {
        migrateTo("36");
        JdbcTemplate jdbc = jdbc();
        Seed seed = seedV35MaterialChain(jdbc);
        Map<String, Integer> before = counts(jdbc);

        migrateTo(null);

        assertThat(jdbc.queryForObject(
                "SELECT version FROM pis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("49");
        assertThat(counts(jdbc)).isEqualTo(before);
        assertThat(before.values()).allMatch(count -> count > 0);
        assertThat(jdbc.queryForObject("SELECT specimen_name FROM pis_v2.specimen WHERE id = ?", String.class,
                seed.specimenId())).isEqualTo("升级测试部位");
        assertThat(jdbc.queryForObject("SELECT creation_source_code FROM pis_v2.specimen WHERE id = ?", String.class,
                seed.specimenId())).isEqualTo("REGISTRATION");
        assertThat(jdbc.queryForObject("SELECT sampling_description IS NULL FROM pis_v2.block WHERE id = ?",
                Boolean.class, seed.blockId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT quantity FROM pis_v2.block WHERE id = ?", Integer.class,
                seed.blockId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'pis_v2'
                  AND table_name IN ('material_process_fact_correction', 'slide_code_history',
                                     'slide_completion_correction')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'pis_v2'
                  AND table_name = 'material_process_fact'
                  AND column_name IN ('block_id', 'target_kind_code', 'equipment_id', 'stain_code')
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.material_process_fact
                WHERE block_id = ? AND slide_id IS NULL AND target_kind_code = 'BLOCK'
                  AND phase_code = 'DEHYDRATION'
                """, Integer.class, seed.blockId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.material_process_fact
                WHERE case_id = ? AND phase_code = 'DEHYDRATION'
                """, Integer.class, seed.caseId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.material_process_fact
                WHERE case_id = ? AND slide_id IS NOT NULL AND target_kind_code = 'SLIDE'
                  AND phase_code = 'DEHYDRATION'
                """, Integer.class, seed.caseId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'pis_v2'
                  AND table_name IN ('grossing_correction_history', 'grossing_specimen_correction_history',
                                     'block_code_history', 'block_verification_policy', 'block_verification')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'pis_v2' AND indexname = 'uq_v2_initial_grossing_active'",
                Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pis_v2.grossing
                    (id, case_id, grossing_no, source_type, gross_description, grossing_doctor_id, recorder_id,
                     started_at, concurrency_version, organization_reference, created_at, created_by_ref,
                     updated_at, updated_by_ref)
                VALUES (?, ?, 'G-UPGRADE-DUP', 'INITIAL', '重复首次取材', 'doctor', 'recorder',
                        CURRENT_TIMESTAMP, 0, 'LOCAL_HOSPITAL', CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test')
                """, UUID.randomUUID(), seed.caseId())).isInstanceOf(DataAccessException.class);
    }

    private static void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis").defaultSchema("pis").locations("classpath:db/migration");
        if (target != null) configuration.target(target);
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

    private static Seed seedV35MaterialChain(JdbcTemplate jdbc) {
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'", UUID.class);
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
        UUID caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID grossingId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID slideId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, 'P-V36-UPGRADE', 'UPGRADE', 'APP-V36-UPGRADE', 'SYNTH-HISTOLOGY', ?,
                        'ACTIVE', TRUE, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test')
                """, caseId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_name, specimen_kind_code, source_kind_code,
                     source_reference, collection_site, collection_method_code, label_code, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'SP-V36-UPGRADE', 'SP-V36-UPGRADE', '升级测试部位', 'TISSUE', 'APPLICATION',
                        'APP-V36-UPGRADE', '升级测试部位', 'SURGERY', 'LBL-V36-UPGRADE', 0,
                        'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, specimenId, caseId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.grossing
                    (id, case_id, grossing_no, source_type, gross_description, grossing_instruction,
                     grossing_doctor_id, recorder_id, started_at, completed_at, completed_by_ref,
                     concurrency_version, organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'G-V36-UPGRADE', 'INITIAL', '升级前大体描述', '升级前取材说明',
                        'doctor', 'recorder', ?, ?, 'doctor', 1, 'LOCAL_HOSPITAL',
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, grossingId, caseId, now, now, now, now);
        jdbc.update("INSERT INTO pis_v2.grossing_specimen (grossing_id, specimen_id, sequence_no, material_description, concurrency_version) VALUES (?, ?, 1, '升级前标本描述', 0)",
                grossingId, specimenId);
        jdbc.update("""
                INSERT INTO pis_v2.block
                    (id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                     concurrency_version, organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'V36-A1', 'ROUTINE', FALSE, 0, 'LOCAL_HOSPITAL',
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, blockId, caseId, grossingId, specimenId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.slide
                    (id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                     source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                     concurrency_version, organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'V36-A1-HE', 'HE', 'INITIAL', ?, 'ROUTINE-HE', 1, TRUE,
                        ?, 'technician', 1, 'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, slideId, caseId, blockId, specimenId, grossingId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.material_process_fact
                    (id, case_id, slide_id, phase_code, started_at, completed_at, operator_ref,
                     concurrency_version, organization_reference, created_at, updated_at)
                VALUES (?, ?, ?, 'DEHYDRATION', ?, ?, 'technician', 1, 'LOCAL_HOSPITAL', ?, ?)
                """, UUID.randomUUID(), caseId, slideId, now, now, now, now);
        UUID secondSlideId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.slide
                    (id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                     source_context_id, rule_code, occurrence_no, required, completed_at, completed_by_ref,
                     concurrency_version, organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'V36-A1-HE-2', 'HE', 'INITIAL', ?, 'ROUTINE-HE', 2, FALSE,
                        ?, 'technician', 1, 'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, secondSlideId, caseId, blockId, specimenId, grossingId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.material_process_fact
                    (id, case_id, slide_id, phase_code, started_at, completed_at, operator_ref,
                     concurrency_version, organization_reference, created_at, updated_at)
                VALUES (?, ?, ?, 'DEHYDRATION', ?, ?, 'technician-2', 1, 'LOCAL_HOSPITAL', ?, ?)
                """, UUID.randomUUID(), caseId, secondSlideId, now, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.diagnosis
                    (id, case_id, context_type, context_id, template_version_id, structured_data,
                     diagnosis_text, concurrency_version, organization_reference, created_at, created_by_ref,
                     updated_at, updated_by_ref)
                VALUES (?, ?, 'CASE', ?, ?, '{}'::jsonb, '升级测试诊断', 0, 'LOCAL_HOSPITAL',
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, diagnosisId, caseId, caseId, diagnosisTemplateVersionId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.report
                    (id, report_no, organization_reference, case_id, diagnosis_id, template_version_id,
                     report_nature_code, status_code, diagnosis_snapshot, responsibility_snapshot, case_snapshot,
                     material_snapshot, technical_result_snapshot, rendered_content, rendered_content_hash,
                     pdf_file_reference, pdf_content_hash, signed_by_ref, signed_at, concurrency_version,
                     created_at, created_by_ref)
                VALUES (?, 'RP-V36-UPGRADE', 'LOCAL_HOSPITAL', ?, ?, ?, 'ORIGINAL', 'EFFECTIVE',
                        '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
                        '升级测试报告', 'render-hash', 'synthetic/v36.pdf', 'pdf-hash', 'doctor', ?, 0, ?, 'upgrade-test')
                """, reportId, caseId, diagnosisId, reportTemplateVersionId, now, now);
        return new Seed(caseId, specimenId, blockId);
    }

    private record Seed(UUID caseId, UUID specimenId, UUID blockId) { }
}
