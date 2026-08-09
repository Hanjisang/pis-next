package com.hanjisang.pis.integration.migration;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.integration.migration.legacy.LegacyFact;
import com.hanjisang.pis.integration.migration.legacy.MigrationSourceAdapter.SourceManifest;

@Repository
public class JdbcMigrationStagingRepository implements MigrationStagingStore {

    private final JdbcTemplate jdbc;

    public JdbcMigrationStagingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID beginOrResume(UUID requestedRunId, String sourceAdapterCode, SourceManifest manifest,
            String mappingRuleVersion, String startedByRef, Instant now) {
        UUID runId = requestedRunId == null ? UUID.randomUUID() : requestedRunId;
        int inserted = jdbc.update("""
                INSERT INTO pis_v2.migration_run
                    (id, source_adapter_code, source_dataset_version, source_schema_hash,
                     mapping_rule_version, status_code, started_at, started_by_ref)
                VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, runId, sourceAdapterCode, manifest.datasetVersion(), manifest.schemaHash(), mappingRuleVersion,
                Timestamp.from(now), startedByRef);
        if (inserted == 1) {
            jdbc.update("""
                    INSERT INTO pis_v2.migration_source_manifest
                        (id, run_id, source_reference, source_dataset_version, source_schema_hash,
                         captured_at, record_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), runId, manifest.sourceReference(), manifest.datasetVersion(),
                    manifest.schemaHash(), Timestamp.from(manifest.capturedAt()), manifest.recordCount());
        } else {
            Integer matching = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pis_v2.migration_run
                    WHERE id = ? AND source_adapter_code = ? AND source_dataset_version = ?
                      AND source_schema_hash = ? AND mapping_rule_version = ?
                    """, Integer.class, runId, sourceAdapterCode, manifest.datasetVersion(), manifest.schemaHash(),
                    mappingRuleVersion);
            if (matching == null || matching != 1) {
                throw new IllegalArgumentException("迁移 runId 与来源快照或映射规则不一致");
            }
        }
        return runId;
    }

    @Override
    public boolean stage(UUID runId, LegacyFact fact, String targetObjectType, UUID targetObjectId,
            String mappingDecisionCode, String evidenceSnapshot, Instant now) {
        return jdbc.update("""
                INSERT INTO pis_v2.migration_staging_record
                    (id, run_id, source_object_type, source_object_id, source_parent_type, source_parent_id,
                     target_object_type, target_object_id, mapping_decision_code, business_reference,
                     payload_reference, payload_digest, evidence_snapshot, staged_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (run_id, source_object_type, source_object_id) DO UPDATE SET
                    source_parent_type = EXCLUDED.source_parent_type,
                    source_parent_id = EXCLUDED.source_parent_id,
                    target_object_type = EXCLUDED.target_object_type,
                    target_object_id = EXCLUDED.target_object_id,
                    mapping_decision_code = EXCLUDED.mapping_decision_code,
                    business_reference = EXCLUDED.business_reference,
                    payload_reference = EXCLUDED.payload_reference,
                    payload_digest = EXCLUDED.payload_digest,
                    evidence_snapshot = EXCLUDED.evidence_snapshot,
                    staged_at = EXCLUDED.staged_at
                """, UUID.randomUUID(), runId, fact.objectType().name(), fact.legacyId(),
                fact.parentType() == null ? null : fact.parentType().name(), fact.parentLegacyId(), targetObjectType,
                targetObjectId, mappingDecisionCode, fact.businessReference(), fact.payloadReference(),
                fact.payloadDigest(), evidenceSnapshot, Timestamp.from(now)) == 1;
    }

    @Override
    public void addIssue(MigrationIssue issue, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.migration_exception
                    (id, run_id, exception_code, severity_code, source_object_type, source_object_id,
                     reason, manual_action, status_code, evidence_reference, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, issue.id(), issue.runId(), issue.exceptionCode(), issue.severity().name(),
                issue.sourceObjectType().name(), issue.sourceObjectId(), issue.reason(), issue.manualAction(),
                issue.status().name(), issue.evidenceReference(), Timestamp.from(now));
    }

    @Override
    public void saveCheckpoint(UUID runId, String checkpointCode, String lastObjectType, String lastObjectId,
            long stagedCount, long exceptionCount, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.migration_checkpoint
                    (id, run_id, checkpoint_code, last_source_object_type, last_source_object_id,
                     staged_count, exception_count, saved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (run_id, checkpoint_code) DO UPDATE SET
                    last_source_object_type = EXCLUDED.last_source_object_type,
                    last_source_object_id = EXCLUDED.last_source_object_id,
                    staged_count = EXCLUDED.staged_count,
                    exception_count = EXCLUDED.exception_count,
                    saved_at = EXCLUDED.saved_at
                """, UUID.randomUUID(), runId, checkpointCode, lastObjectType, lastObjectId, stagedCount,
                exceptionCount, Timestamp.from(now));
    }

    @Override
    public void complete(MigrationValidationReport report, Instant now) {
        jdbc.update("""
                INSERT INTO pis_v2.migration_validation_report
                    (id, run_id, case_source_count, case_staged_count, specimen_source_count,
                     specimen_staged_count, block_source_count, block_staged_count, slide_source_count,
                     slide_staged_count, diagnosis_source_count, diagnosis_staged_count, report_source_count,
                     report_staged_count, case_specimen_difference, specimen_block_difference,
                     block_slide_difference, case_report_difference, exception_count,
                     validation_status_code, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (run_id) DO UPDATE SET
                    case_source_count = EXCLUDED.case_source_count,
                    case_staged_count = EXCLUDED.case_staged_count,
                    specimen_source_count = EXCLUDED.specimen_source_count,
                    specimen_staged_count = EXCLUDED.specimen_staged_count,
                    block_source_count = EXCLUDED.block_source_count,
                    block_staged_count = EXCLUDED.block_staged_count,
                    slide_source_count = EXCLUDED.slide_source_count,
                    slide_staged_count = EXCLUDED.slide_staged_count,
                    diagnosis_source_count = EXCLUDED.diagnosis_source_count,
                    diagnosis_staged_count = EXCLUDED.diagnosis_staged_count,
                    report_source_count = EXCLUDED.report_source_count,
                    report_staged_count = EXCLUDED.report_staged_count,
                    case_specimen_difference = EXCLUDED.case_specimen_difference,
                    specimen_block_difference = EXCLUDED.specimen_block_difference,
                    block_slide_difference = EXCLUDED.block_slide_difference,
                    case_report_difference = EXCLUDED.case_report_difference,
                    exception_count = EXCLUDED.exception_count,
                    validation_status_code = EXCLUDED.validation_status_code,
                    generated_at = EXCLUDED.generated_at
                """, UUID.randomUUID(), report.runId(), report.caseCount().sourceCount(),
                report.caseCount().stagedCount(), report.specimenCount().sourceCount(),
                report.specimenCount().stagedCount(), report.blockCount().sourceCount(),
                report.blockCount().stagedCount(), report.slideCount().sourceCount(),
                report.slideCount().stagedCount(), report.diagnosisCount().sourceCount(),
                report.diagnosisCount().stagedCount(), report.reportCount().sourceCount(),
                report.reportCount().stagedCount(), report.caseSpecimen().difference(),
                report.specimenBlock().difference(), report.blockSlide().difference(),
                report.caseReport().difference(), report.exceptions().size(), report.status().name(),
                Timestamp.from(report.generatedAt()));
        jdbc.update("UPDATE pis_v2.migration_run SET status_code = ?, completed_at = ? WHERE id = ?",
                report.status().name(), Timestamp.from(now), report.runId());
    }
}
