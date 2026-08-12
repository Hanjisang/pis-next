package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.hanjisang.pis.v2.diagnosis.domain.Diagnosis;
import com.hanjisang.pis.v2.diagnosis.infrastructure.JdbcV2DiagnosisRepository;
import com.hanjisang.pis.presentation.configuration.HospitalProfileApplicationService;
import com.hanjisang.pis.presentation.configuration.JdbcHospitalProfileRepository;
import com.hanjisang.pis.security.AuthIdentityRepository;

@Testcontainers
class V2RegistrationPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("pis")
            .withUsername("pis")
            .withPassword(UUID.randomUUID().toString());

    @Test
    void postgresMigrationCreatesV2CoreGateASchemaAndSeedConfiguration() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("pis")
                .defaultSchema("pis")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("SELECT version_code FROM pis_v2.schema_metadata WHERE schema_code = 'PIS_V2'",
                String.class)).isEqualTo("PX01-HISTOLOGY-FACTS");
        assertThat(jdbc.queryForObject("SELECT version FROM pis.flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("33");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.business_type", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.application_item_mapping", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_number_rule", Integer.class))
                .isEqualTo(36);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide_rule", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.print_rule", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT printer_profile_code FROM pis_v2.print_rule", String.class))
                .isEqualTo("MOCK://SYNTH-PRINTER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.diagnosis_template", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.diagnosis_template_version", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.technical_project", Integer.class)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.technical_project WHERE produces_slide", Integer.class))
                .isEqualTo(16);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.report_template", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.report_template_version", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.grossing", Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'pis_v2' AND table_name IN ('auth_user', 'auth_user_permission', 'doctor_identity')", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name IN
                    ('hospital_profile', 'hospital_campus', 'hospital_department',
                     'hospital_business_type_configuration', 'hospital_workflow_configuration',
                     'label_template', 'printer_mapping', 'print_strategy',
                     'hospital_report_configuration', 'device_configuration', 'integration_configuration')
                """, Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name IN
                    ('integration_message_log', 'integration_attempt', 'integration_dead_letter',
                     'integration_replay_request', 'integration_reconciliation', 'external_identifier_mapping')
                """, Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name IN
                    ('identity_provider_configuration', 'external_identity_link', 'external_authentication_event')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.identity_provider_configuration",
                Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'pis_v2' AND table_name IN
                    ('migration_run', 'migration_source_manifest', 'migration_staging_record',
                     'migration_exception', 'migration_checkpoint', 'migration_validation_report')
                """, Integer.class)).isEqualTo(6);

        AuthIdentityRepository identities = new AuthIdentityRepository(jdbc);
        identities.seedSyntheticAccounts(UUID.randomUUID().toString());
        var doctorA = identities.authenticate("doctor-a", "not-the-random-password");
        assertThat(doctorA).isEmpty();
        var storedDoctorA = jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.auth_user WHERE username = 'doctor-a' AND hospital_profile_id IS NOT NULL AND department_id IS NOT NULL",
                Integer.class);
        assertThat(storedDoctorA).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.doctor_identity di
                JOIN pis_v2.auth_user u ON u.id = di.user_id
                WHERE u.username = 'doctor-a' AND di.department_id = u.department_id
                """, Integer.class)).isEqualTo(1);

        HospitalProfileApplicationService hospitalProfiles = new HospitalProfileApplicationService(
                new JdbcHospitalProfileRepository(jdbc));
        var hospitalARoutine = hospitalProfiles.registrationConfiguration("HOSPITAL_A", "ROUTINE");
        var hospitalBRoutine = hospitalProfiles.registrationConfiguration("HOSPITAL_B", "ROUTINE");
        var hospitalAMolecular = hospitalProfiles.registrationConfiguration("HOSPITAL_A", "MOLECULAR");
        var hospitalBMolecular = hospitalProfiles.registrationConfiguration("HOSPITAL_B", "MOLECULAR");
        assertThat(hospitalARoutine.caseNumberPrefix()).isEqualTo("A-P-");
        assertThat(hospitalBRoutine.caseNumberPrefix()).isEqualTo("B-P-");
        assertThat(hospitalAMolecular.enabled()).isTrue();
        assertThat(hospitalBMolecular.enabled()).isFalse();
        assertThat(hospitalProfiles.requireProfile("HOSPITAL_A").printStrategies().getFirst().copies()).isEqualTo(1);
        assertThat(hospitalProfiles.requireProfile("HOSPITAL_B").printStrategies().getFirst().copies()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('case_state_history', 'specimen_state_history',
                                     'specimen_receipt_fact', 'specimen_exception')
                """, Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('grossing', 'grossing_specimen', 'block', 'slide',
                                     'slide_rule', 'print_rule', 'print_log', 'material_command_idempotency')
                """, Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('report_template', 'report_template_version', 'report', 'report_pdf_output')
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('diagnosis', 'diagnosis_template', 'diagnosis_template_version',
                                     'responsibility_unit', 'assignment_rule', 'diagnosis_command_idempotency')
                """, Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('technical_project', 'technical_order_sequence', 'technical_order',
                                     'technical_order_item', 'technical_order_target', 'technical_order_item_result',
                                     'technical_order_output', 'technical_order_idempotency')
                """, Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('frozen_round', 'frozen_round_specimen', 'frozen_end')
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('molecular_result', 'molecular_result_idempotency', 'send_out',
                                     'send_out_idempotency')
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'pis_v2'
                  AND table_name IN ('digital_slide', 'archive_location', 'material_archive_history',
                                     'block_archive_current', 'slide_archive_current', 'loan', 'loan_item',
                                     'material_destruction', 'custody_command_idempotency', 'qc_rule', 'qc_evaluation')
                """, Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'pis_v2'
                  AND indexname IN ('uq_v2_specimen_code_active', 'uq_v2_specimen_label_active',
                                    'uq_v2_block_code_active', 'uq_v2_slide_code_active',
                                    'uq_v2_slide_rule_output_active')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname = 'pis_v2' AND indexname = 'uq_v2_slide_rule_output_active'",
                String.class)).contains("source_context_id");

        UUID caseResult = UUID.randomUUID();
        int inserted = jdbc.update("""
                MERGE INTO pis_v2.idempotency_record AS target
                USING (VALUES (?, ?, ?, ?, ?, CAST(? AS UUID), CAST(? AS UUID),
                               CAST(? AS TIMESTAMP WITH TIME ZONE), ?)) AS incoming
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code,
                     result_case_id, result_specimen_id, created_at, created_by_ref)
                ON target.operation_code = incoming.operation_code
                   AND target.idempotency_key = incoming.idempotency_key
                WHEN NOT MATCHED THEN INSERT
                    (id, operation_code, idempotency_key, payload_digest, result_kind_code,
                     result_case_id, result_specimen_id, created_at, created_by_ref)
                VALUES (incoming.id, incoming.operation_code, incoming.idempotency_key, incoming.payload_digest,
                        incoming.result_kind_code, incoming.result_case_id, incoming.result_specimen_id,
                        incoming.created_at, incoming.created_by_ref)
                """, UUID.randomUUID(), "V2-TEST", "same-key", "digest", "CASE", caseResult, null,
                java.sql.Timestamp.from(java.time.Instant.now()), "TEST");
        assertThat(inserted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.idempotency_record WHERE operation_code = 'V2-TEST'",
                Integer.class)).isEqualTo(1);

        UUID businessTypeId = jdbc.queryForObject("SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'",
                UUID.class);
        UUID publishedTemplateVersionId = jdbc.queryForObject("""
                SELECT tv.id
                FROM pis_v2.diagnosis_template_version tv
                JOIN pis_v2.diagnosis_template t ON t.id = tv.template_id
                WHERE t.business_type_id = ? AND tv.status_code = 'PUBLISHED'
                ORDER BY tv.version_no DESC LIMIT 1
                """, UUID.class, businessTypeId);
        UUID diagnosisCaseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'SYNTH-HIS', ?, 'SYNTH-HISTOLOGY', ?, 'ACTIVE', TRUE, 0, 'LOCAL_HOSPITAL', ?, 'TEST')
                """, diagnosisCaseId, "H-I03-JSON", "APP-I03-JSON", businessTypeId,
                java.sql.Timestamp.from(java.time.Instant.now()));
        JdbcV2DiagnosisRepository diagnosisRepository = new JdbcV2DiagnosisRepository(jdbc);
        Diagnosis diagnosis = Diagnosis.create(UUID.randomUUID(), diagnosisCaseId, publishedTemplateVersionId,
                "{\"marker\":\"synthetic\"}", "synthetic microscopic", "synthetic diagnosis", "synthetic comment",
                java.time.Instant.now(), "TEST");
        diagnosisRepository.insertDiagnosis(diagnosis, "LOCAL_HOSPITAL", java.time.Instant.now(), "TEST");
        assertThat(jdbc.queryForObject("SELECT structured_data::text FROM pis_v2.diagnosis WHERE id = ?", String.class,
                diagnosis.id())).contains("marker");

        UUID templateId = jdbc.queryForObject("SELECT template_id FROM pis_v2.diagnosis_template_version WHERE id = ?",
                UUID.class, publishedTemplateVersionId);
        UUID draftVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.diagnosis_template_version
                    (id, template_id, version_no, schema_definition, status_code, created_at, created_by_ref,
                     concurrency_version)
                VALUES (?, ?, 2, '{}'::jsonb, 'DRAFT', ?, 'TEST', 0)
                """, draftVersionId, templateId, java.sql.Timestamp.from(java.time.Instant.now()));
        assertThat(diagnosisRepository.publishTemplateVersion(draftVersionId, "LOCAL_HOSPITAL",
                java.time.Instant.now(), "TEST")).isTrue();
        assertThatThrownBy(() -> jdbc.update("UPDATE pis_v2.diagnosis_template_version SET version_no = 99 WHERE id = ?",
                publishedTemplateVersionId)).isInstanceOf(DataAccessException.class);
    }
}
