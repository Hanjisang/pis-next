package com.hanjisang.pis.v2.operations;

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

import com.hanjisang.pis.v2.operations.infrastructure.JdbcV2BusinessOperationsRepository;

@Testcontainers
class V34ExistingDatabaseUpgradeTest {

    private static final List<String> EXISTING_TABLES = List.of(
            "pathology_application", "pathology_case", "specimen", "block", "slide", "diagnosis",
            "technical_order", "report", "digital_slide", "auth_user", "auth_user_permission");

    private static final List<String> V34_TABLES = List.of(
            "notification", "staff_schedule", "quality_document", "equipment", "equipment_event",
            "consumable_catalog", "consumable_batch", "consumable_transaction", "consumable_requisition",
            "consumable_requisition_item", "consumable_quality_evaluation", "procurement_request",
            "procurement_item", "procurement_approval", "procurement_attachment", "department_space",
            "space_environment_record", "space_safety_check", "critical_value", "critical_value_notification",
            "critical_value_feedback", "report_distribution", "report_print_record", "common_address",
            "logistics_package", "logistics_package_item", "logistics_event", "molecular_project",
            "molecular_instrument", "molecular_reagent_kit", "molecular_test", "digital_slide_archive",
            "regional_share", "regional_share_item", "regional_share_access", "income_fact", "migration_job",
            "migration_record", "migration_error");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis_v34_upgrade")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void upgradesRepresentativeV33DataToV34WithoutChangingExistingFacts() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration")
                .target("33")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        seedRepresentativeV33Data(jdbc);
        Map<String, Integer> countsBefore = counts(jdbc, EXISTING_TABLES);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForObject(
                "SELECT version FROM pis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("34");
        assertThat(counts(jdbc, EXISTING_TABLES)).isEqualTo(countsBefore);
        assertThat(countsBefore.values()).allMatch(count -> count > 0);
        String placeholders = String.join(", ", V34_TABLES.stream().map(ignored -> "?").toList());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'pis_v2' AND table_name IN (%s)
                """.formatted(placeholders), Integer.class, V34_TABLES.toArray())).isEqualTo(V34_TABLES.size());

        JdbcV2BusinessOperationsRepository repository = new JdbcV2BusinessOperationsRepository(jdbc);
        assertThat(repository.overview("LOCAL_HOSPITAL")).containsKeys("schedules", "migrationJobs");

        UUID equipmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.equipment
                    (id, equipment_code, name, category_code, status_code, organization_reference,
                     created_at, created_by_ref)
                VALUES (?, 'EQ-UPGRADE', '升级测试设备', 'MICROSCOPE', 'ACTIVE', 'LOCAL_HOSPITAL',
                        CURRENT_TIMESTAMP, 'upgrade-test')
                """, equipmentId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO pis_v2.equipment_event
                    (id, equipment_id, event_code, occurred_at, operator_reference, organization_reference)
                VALUES (?, ?, 'MAINTENANCE', CURRENT_TIMESTAMP, 'upgrade-test', 'LOCAL_HOSPITAL')
                """, UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(DataAccessException.class);
    }

    private static Map<String, Integer> counts(JdbcTemplate jdbc, List<String> tables) {
        Map<String, Integer> result = new LinkedHashMap<>();
        tables.forEach(table -> result.put(table,
                jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2." + table, Integer.class)));
        return result;
    }

    private static void seedRepresentativeV33Data(JdbcTemplate jdbc) {
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type ORDER BY business_type_code LIMIT 1", UUID.class);
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
        UUID caseId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID grossingId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID slideId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbc.update("""
                INSERT INTO pis_v2.auth_user
                    (id, username, display_name, password_digest, role_code, hospital_scope,
                     department_scope, task_scope, enabled, created_at, updated_at)
                VALUES (?, 'upgrade-user', '升级测试用户', 'not-a-real-password', 'ADMIN',
                        'LOCAL_HOSPITAL', 'PATHOLOGY', NULL, TRUE, ?, ?)
                """, userId, now, now);
        jdbc.update("INSERT INTO pis_v2.auth_user_permission (user_id, permission_code) VALUES (?, 'P14-PERM-001')",
                userId);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_application
                    (id, application_no, source_type_code, source_system_code, patient_reference,
                     applied_at, status_code, organization_reference, concurrency_version,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, 'APP-UPGRADE-001', 'MANUAL', 'UPGRADE-TEST', 'SYNTHETIC-PATIENT', ?,
                        'RECEIVED', 'LOCAL_HOSPITAL', 0, ?, 'upgrade-test', ?, 'upgrade-test')
                """, applicationId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, concurrency_version, organization_reference,
                     created_at, created_by_ref)
                VALUES (?, 'CASE-UPGRADE-001', 'UPGRADE-TEST', 'APP-UPGRADE-001', 'ITEM-001', ?,
                        'ACTIVE', 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test')
                """, caseId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_kind_code, source_kind_code,
                     source_reference, collection_site, collection_method_code, label_code,
                     concurrency_version, organization_reference, created_at, created_by_ref,
                     updated_at, updated_by_ref)
                VALUES (?, ?, 'SP-UPGRADE-001', 'SP-UPGRADE-001', 'TISSUE', 'APPLICATION',
                        'APP-UPGRADE-001', '合成部位', 'SURGERY', 'LBL-UPGRADE-001', 0,
                        'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, specimenId, caseId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.grossing
                    (id, case_id, grossing_no, source_type, gross_description, grossing_doctor_id,
                     recorder_id, started_at, concurrency_version, organization_reference,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'GR-UPGRADE-001', 'INITIAL', '合成取材描述', 'doctor-upgrade',
                        'recorder-upgrade', ?, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, grossingId, caseId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.block
                    (id, case_id, grossing_id, specimen_id, block_code, block_type, external_source_flag,
                     concurrency_version, organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'B-UPGRADE-001', 'ROUTINE', FALSE, 0, 'LOCAL_HOSPITAL',
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, blockId, caseId, grossingId, specimenId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.slide
                    (id, case_id, block_id, specimen_id, slide_code, slide_type, source_context_type,
                     source_context_id, rule_code, occurrence_no, required, concurrency_version,
                     organization_reference, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'SL-UPGRADE-001', 'ROUTINE', 'INITIAL', ?, 'UPGRADE-RULE', 1,
                        TRUE, 0, 'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, slideId, caseId, blockId, specimenId, grossingId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.diagnosis
                    (id, case_id, context_type, context_id, template_version_id, structured_data,
                     microscopic_description, diagnosis_text, concurrency_version, organization_reference,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'CASE', ?, ?, '{}'::jsonb, '合成镜下描述', '合成诊断', 0,
                        'LOCAL_HOSPITAL', ?, 'upgrade-test', ?, 'upgrade-test')
                """, diagnosisId, caseId, caseId, diagnosisTemplateVersionId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.technical_order
                    (id, organization_reference, order_no, diagnosis_id, case_id, required_before_sign_out,
                     status_code, concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, 'LOCAL_HOSPITAL', 'TO-UPGRADE-001', ?, ?, FALSE, 'COMPLETED', 0,
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, UUID.randomUUID(), diagnosisId, caseId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.report
                    (id, report_no, organization_reference, case_id, diagnosis_id, template_version_id,
                     report_nature_code, status_code, diagnosis_snapshot, responsibility_snapshot,
                     case_snapshot, material_snapshot, technical_result_snapshot, rendered_content,
                     rendered_content_hash, pdf_file_reference, pdf_content_hash, signed_by_ref,
                     signed_at, concurrency_version, created_at, created_by_ref)
                VALUES (?, 'RP-UPGRADE-001', 'LOCAL_HOSPITAL', ?, ?, ?, 'ORIGINAL', 'EFFECTIVE',
                        '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb,
                        '合成报告', 'hash-rendered', 'synthetic/report.pdf', 'hash-pdf',
                        'doctor-upgrade', ?, 0, ?, 'upgrade-test')
                """, reportId, caseId, diagnosisId, reportTemplateVersionId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.digital_slide
                    (id, case_id, block_id, slide_id, binding_mode_code, status_code, viewer_reference,
                     source_platform, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'AUTOMATIC', 'ACTIVE', 'mock://upgrade-slide', 'UPGRADE-TEST',
                        ?, 'upgrade-test', ?, 'upgrade-test')
                """, UUID.randomUUID(), caseId, blockId, slideId, now, now);
    }
}
