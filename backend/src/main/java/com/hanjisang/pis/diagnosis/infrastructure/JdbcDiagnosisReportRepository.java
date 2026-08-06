package com.hanjisang.pis.diagnosis.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDiagnosisReportRepository {

    private final JdbcTemplate jdbc;

    public JdbcDiagnosisReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record IdempotentReference(UUID resultObjectId, String payloadDigest) { }

    public record TaskRow(UUID id, String taskNo, UUID caseId, String modalityCode, String categoryCode,
            String priorityCode, String stateCode, String assignedActor, String responsibleActor,
            String organizationReference, String dataScopeCode, long version) { }

    public record DraftRow(UUID id, UUID taskId, String ownerActor, String grossReference, String microscopic,
            String conclusion, String supplementary, String structuredItems, String terminology, long version) { }

    public record OpinionVersionRow(UUID id, UUID opinionId, int versionNo, String stateCode, UUID taskId,
            String microscopic, String conclusion, String supplementary, String evidenceSummary) { }

    public record ReportRow(UUID id, String reportNo, UUID caseId, String reportType, String stateCode,
            UUID currentVersionId, int nextVersionNo, long version) { }

    public record ContentVersionRow(UUID id, UUID reportId, int versionNo, String stateCode, String patientSnapshot,
            String encounterSnapshot, String caseNoSnapshot, String specimenSummary, String clinicalInformation,
            String specimenInformation, String grossDescription, String microscopicDescription,
            String diagnosisConclusion, String supplementaryNote, String technicalSummary, UUID diagnosisVersionId,
            String templateVersion, String formedBy, Instant formedAt, UUID priorVersionId) { }

    public record ReviewRow(UUID id, UUID opinionVersionId, UUID contentVersionId, String kindCode,
            String decisionCode, String reviewerActor, String reason) { }

    public record RevisionTargetRow(UUID id, UUID reportId, UUID originalVersionId, String reason, String stateCode) { }
    public record ReportDraftRow(UUID id, UUID reportId, String ownerActor, String clinical, String specimen,
            String gross, String microscopic, String conclusion, String supplementary, String technical, long version) { }

    public Optional<IdempotentReference> idempotent(String operation, String key) {
        return jdbc.query("SELECT result_object_id, payload_digest FROM pis.p19_command_idempotency WHERE operation_code = ? AND idempotency_key = ?",
                rs -> rs.next() ? Optional.of(new IdempotentReference((UUID) rs.getObject(1), rs.getString(2))) : Optional.empty(), operation, key);
    }

    public void recordIdempotent(String operation, String key, String digest, UUID resultId, String actor, Instant now) {
        jdbc.update("INSERT INTO pis.p19_command_idempotency (id, operation_code, idempotency_key, payload_digest, result_object_id, actor_ref, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), operation, key, digest, resultId, actor, Timestamp.from(now));
    }

    public void insertTask(TaskRow task, Instant now, String actor) {
        jdbc.update("INSERT INTO pis.p19_diagnosis_task (id, task_no, case_id, pathology_modality_code, task_category_code, priority_code, task_state_code, assigned_actor_ref, responsible_actor_ref, represented_actor_ref, organization_reference, data_scope_code, concurrency_version, created_at, created_by_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                task.id(), task.taskNo(), task.caseId(), task.modalityCode(), task.categoryCode(), task.priorityCode(), task.stateCode(), task.assignedActor(), task.responsibleActor(), actor, task.organizationReference(), task.dataScopeCode(), task.version(), Timestamp.from(now), actor);
    }

    public Optional<TaskRow> task(UUID id) {
        return jdbc.query("SELECT id, task_no, case_id, pathology_modality_code, task_category_code, priority_code, task_state_code, assigned_actor_ref, responsible_actor_ref, organization_reference, data_scope_code, concurrency_version FROM pis.p19_diagnosis_task WHERE id = ?",
                rs -> rs.next() ? Optional.of(new TaskRow((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getLong(12))) : Optional.empty(), id);
    }

    public List<TaskRow> tasks(String organization, String state) {
        String sql = "SELECT id, task_no, case_id, pathology_modality_code, task_category_code, priority_code, task_state_code, assigned_actor_ref, responsible_actor_ref, organization_reference, data_scope_code, concurrency_version FROM pis.p19_diagnosis_task WHERE organization_reference = ?" + (state == null || state.isBlank() ? "" : " AND task_state_code = ?") + " ORDER BY created_at, id";
        return state == null || state.isBlank() ? jdbc.query(sql, (rs, n) -> taskFrom(rs), organization)
                : jdbc.query(sql, (rs, n) -> taskFrom(rs), organization, state);
    }

    private TaskRow taskFrom(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TaskRow((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getLong(12));
    }

    public int updateTask(UUID id, long expected, String state, String assigned, String responsible, Instant now) {
        return jdbc.update("UPDATE pis.p19_diagnosis_task SET task_state_code = ?, assigned_actor_ref = ?, responsible_actor_ref = ?, started_at = COALESCE(started_at, ?), completed_at = CASE WHEN ? = 'P19-DIAGNOSIS-TASK-CLOSED' THEN ? ELSE completed_at END, concurrency_version = concurrency_version + 1 WHERE id = ? AND concurrency_version = ?",
                state, assigned, responsible, Timestamp.from(now), state, Timestamp.from(now), id, expected);
    }

    public void responsibility(UUID taskId, String from, String to, String action, String reason, Instant now, String actor) {
        jdbc.update("INSERT INTO pis.p19_diagnosis_responsibility_history (id, task_id, from_actor_ref, to_actor_ref, action_code, reason_text, occurred_at, recorded_by_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), taskId, from, to, action, reason, Timestamp.from(now), actor);
    }

    public void stateHistory(UUID id, String kind, String from, String to, String event, long expected, long result, String reason, Instant now, String actor) {
        jdbc.update("INSERT INTO pis.p19_state_history (id, target_object_id, target_object_kind_code, source_state_code, target_state_code, transition_event_code, expected_version, resulting_version, reason_text, occurred_at, recorded_by_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), id, kind, from, to, event, expected, result, reason, Timestamp.from(now), actor);
    }

    public Optional<DraftRow> draft(UUID taskId) {
        return jdbc.query("SELECT id, task_id, owner_actor_ref, gross_description_reference, microscopic_description, diagnosis_conclusion, supplementary_note, structured_items, terminology_reference, concurrency_version FROM pis.p19_diagnosis_work_draft WHERE task_id = ?",
                rs -> rs.next() ? Optional.of(new DraftRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getLong(10))) : Optional.empty(), taskId);
    }

    public void insertDraft(DraftRow draft, Instant now, String actor) {
        jdbc.update("INSERT INTO pis.p19_diagnosis_work_draft (id, task_id, owner_actor_ref, gross_description_reference, microscopic_description, diagnosis_conclusion, supplementary_note, structured_items, terminology_reference, concurrency_version, updated_at, updated_by_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", draft.id(), draft.taskId(), draft.ownerActor(), draft.grossReference(), draft.microscopic(), draft.conclusion(), draft.supplementary(), draft.structuredItems(), draft.terminology(), draft.version(), Timestamp.from(now), actor);
    }

    public int updateDraft(DraftRow draft, long expected, Instant now, String actor) {
        return jdbc.update("UPDATE pis.p19_diagnosis_work_draft SET owner_actor_ref = ?, gross_description_reference = ?, microscopic_description = ?, diagnosis_conclusion = ?, supplementary_note = ?, structured_items = ?, terminology_reference = ?, concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ? WHERE task_id = ? AND concurrency_version = ?",
                draft.ownerActor(), draft.grossReference(), draft.microscopic(), draft.conclusion(), draft.supplementary(), draft.structuredItems(), draft.terminology(), Timestamp.from(now), actor, draft.taskId(), expected);
    }

    public UUID insertOpinion(UUID taskId, UUID caseId, String type, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.p19_diagnosis_opinion (id, task_id, case_id, diagnosis_type_code, opinion_state_code, current_version_no, created_at, created_by_ref) VALUES (?, ?, ?, ?, 'DRAFT', 1, ?, ?)", id, taskId, caseId, type, Timestamp.from(now), actor);
        return id;
    }

    public Optional<UUID> opinion(UUID taskId, String type) {
        return jdbc.query("SELECT id FROM pis.p19_diagnosis_opinion WHERE task_id = ? AND diagnosis_type_code = ?", rs -> rs.next() ? Optional.of((UUID) rs.getObject(1)) : Optional.empty(), taskId, type);
    }

    public UUID insertOpinionVersion(UUID opinionId, int version, String state, String gross, String microscopic, String conclusion,
            String supplementary, String structured, String terminology, String evidence, String actor, Instant now, UUID prior) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.p19_diagnosis_opinion_version (id, opinion_id, version_no, version_state_code, gross_description_reference, microscopic_description, diagnosis_conclusion, supplementary_note, structured_items, terminology_reference, evidence_version_summary, submitted_by_ref, submitted_at, prior_version_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, opinionId, version, state, gross, microscopic, conclusion, supplementary, structured, terminology, evidence, actor, Timestamp.from(now), prior);
        jdbc.update("UPDATE pis.p19_diagnosis_opinion SET current_version_no = ?, current_version_id = ?, opinion_state_code = ? WHERE id = ?", version, id, state, opinionId);
        return id;
    }

    public Optional<OpinionVersionRow> opinionVersion(UUID id) {
        return jdbc.query("SELECT v.id, v.opinion_id, v.version_no, v.version_state_code, o.task_id, v.microscopic_description, v.diagnosis_conclusion, v.supplementary_note, v.evidence_version_summary FROM pis.p19_diagnosis_opinion_version v JOIN pis.p19_diagnosis_opinion o ON o.id = v.opinion_id WHERE v.id = ?",
                rs -> rs.next() ? Optional.of(new OpinionVersionRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getInt(3), rs.getString(4), (UUID) rs.getObject(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9))) : Optional.empty(), id);
    }

    public int nextOpinionVersion(UUID opinionId) {
        Integer current = jdbc.queryForObject("SELECT current_version_no FROM pis.p19_diagnosis_opinion WHERE id = ?", Integer.class, opinionId);
        UUID currentVersion = jdbc.queryForObject("SELECT current_version_id FROM pis.p19_diagnosis_opinion WHERE id = ?", UUID.class, opinionId);
        if (currentVersion == null) return current == null ? 1 : current;
        jdbc.update("UPDATE pis.p19_diagnosis_opinion SET current_version_no = current_version_no + 1 WHERE id = ?", opinionId);
        return jdbc.queryForObject("SELECT current_version_no FROM pis.p19_diagnosis_opinion WHERE id = ?", Integer.class, opinionId);
    }

    public UUID insertFollowUp(UUID taskId, UUID targetVersion, String actor, String opinion, String consistency, String returnReason, String recommendation, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.p19_diagnosis_follow_up (id, task_id, target_opinion_version_id, follow_up_actor_ref, follow_up_opinion, consistency_code, return_reason, adoption_recommendation, follow_up_state_code, version_no, created_at, created_by_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', 1, ?, ?)", id, taskId, targetVersion, actor, opinion, consistency, returnReason, recommendation, Timestamp.from(now), actor);
        return id;
    }

    public int updateFollowUp(UUID id, long expectedVersion, String state) {
        return jdbc.update("UPDATE pis.p19_diagnosis_follow_up SET follow_up_state_code = ?, version_no = version_no + 1 WHERE id = ? AND version_no = ? AND follow_up_state_code = 'SUBMITTED'", state, id, expectedVersion);
    }

    public Optional<UUID> followUpTask(UUID id) {
        return jdbc.query("SELECT task_id FROM pis.p19_diagnosis_follow_up WHERE id = ?", rs -> rs.next() ? Optional.of((UUID) rs.getObject(1)) : Optional.empty(), id);
    }

    public Optional<ReviewRow> reviewForOpinion(UUID versionId, String kind) { return review(versionId, null, kind); }
    public Optional<ReviewRow> reviewForContent(UUID versionId, String kind) { return review(null, versionId, kind); }
    private Optional<ReviewRow> review(UUID opinionVersion, UUID contentVersion, String kind) {
        String clause = opinionVersion != null ? "target_opinion_version_id = ?" : "target_report_content_version_id = ?";
        UUID target = opinionVersion != null ? opinionVersion : contentVersion;
        return jdbc.query("SELECT id, target_opinion_version_id, target_report_content_version_id, review_kind_code, decision_code, reviewer_actor_ref, review_reason FROM pis.p19_diagnosis_review WHERE " + clause + " AND review_kind_code = ?", rs -> rs.next() ? Optional.of(new ReviewRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7))) : Optional.empty(), target, kind);
    }

    public UUID insertReview(UUID opinionVersion, UUID contentVersion, String kind, String reviewer, String reason, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.p19_diagnosis_review (id, target_opinion_version_id, target_report_content_version_id, review_kind_code, decision_code, reviewer_actor_ref, review_reason, created_at) VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)", id, opinionVersion, contentVersion, kind, reviewer, reason, Timestamp.from(now));
        return id;
    }

    public int decideReview(UUID reviewId, String decision, String reason, Instant now) {
        return jdbc.update("UPDATE pis.p19_diagnosis_review SET decision_code = ?, review_reason = ?, reviewed_at = ? WHERE id = ? AND decision_code = 'PENDING'", decision, reason, Timestamp.from(now), reviewId);
    }

    public UUID insertReport(UUID caseId, String type, String organization, String actor, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.p19_report (id, report_no, case_id, report_type_code, report_state_code, next_version_no, concurrency_version, organization_reference, created_at, created_by_ref) VALUES (?, ?, ?, ?, 'DRAFT', 1, 0, ?, ?, ?)", id, "DEV-REPORT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(), caseId, type, organization, Timestamp.from(now), actor);
        return id;
    }

    public Optional<ReportDraftRow> reportDraft(UUID reportId) { return jdbc.query("SELECT id, report_id, owner_actor_ref, clinical_information, specimen_information, gross_description, microscopic_description, diagnosis_conclusion, supplementary_note, technical_result_reference_summary, concurrency_version FROM pis.p19_report_draft WHERE report_id = ?", rs -> rs.next() ? Optional.of(new ReportDraftRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getLong(11))) : Optional.empty(), reportId); }
    public void insertReportDraft(ReportDraftRow draft, Instant now, String actor) { jdbc.update("INSERT INTO pis.p19_report_draft (id, report_id, owner_actor_ref, clinical_information, specimen_information, gross_description, microscopic_description, diagnosis_conclusion, supplementary_note, technical_result_reference_summary, concurrency_version, updated_at, updated_by_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", draft.id(), draft.reportId(), draft.ownerActor(), draft.clinical(), draft.specimen(), draft.gross(), draft.microscopic(), draft.conclusion(), draft.supplementary(), draft.technical(), draft.version(), Timestamp.from(now), actor); }
    public int updateReportDraft(ReportDraftRow draft, long expected, Instant now, String actor) { return jdbc.update("UPDATE pis.p19_report_draft SET owner_actor_ref = ?, clinical_information = ?, specimen_information = ?, gross_description = ?, microscopic_description = ?, diagnosis_conclusion = ?, supplementary_note = ?, technical_result_reference_summary = ?, concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ? WHERE report_id = ? AND concurrency_version = ?", draft.ownerActor(), draft.clinical(), draft.specimen(), draft.gross(), draft.microscopic(), draft.conclusion(), draft.supplementary(), draft.technical(), Timestamp.from(now), actor, draft.reportId(), expected); }

    public Optional<ReportRow> report(UUID id) {
        return jdbc.query("SELECT id, report_no, case_id, report_type_code, report_state_code, current_effective_version_id, next_version_no, concurrency_version FROM pis.p19_report WHERE id = ?", rs -> rs.next() ? Optional.of(new ReportRow((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), (UUID) rs.getObject(6), rs.getInt(7), rs.getLong(8))) : Optional.empty(), id);
    }

    public List<ReportRow> reports(String organization, String state) {
        String sql = "SELECT id, report_no, case_id, report_type_code, report_state_code, current_effective_version_id, next_version_no, concurrency_version FROM pis.p19_report WHERE organization_reference = ?" + (state == null || state.isBlank() ? "" : " AND report_state_code = ?") + " ORDER BY created_at, id";
        return state == null || state.isBlank() ? jdbc.query(sql, (rs, n) -> reportFrom(rs), organization) : jdbc.query(sql, (rs, n) -> reportFrom(rs), organization, state);
    }

    private ReportRow reportFrom(java.sql.ResultSet rs) throws java.sql.SQLException { return new ReportRow((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), (UUID) rs.getObject(6), rs.getInt(7), rs.getLong(8)); }

    public int updateReport(UUID id, long expected, String state, UUID currentVersion, Instant now) {
        return jdbc.update("UPDATE pis.p19_report SET report_state_code = ?, current_effective_version_id = ?, concurrency_version = concurrency_version + 1 WHERE id = ? AND concurrency_version = ?", state, currentVersion, id, expected);
    }

    public int allocateVersion(UUID reportId) {
        jdbc.update("UPDATE pis.p19_report SET next_version_no = next_version_no + 1 WHERE id = ?", reportId);
        return jdbc.queryForObject("SELECT next_version_no - 1 FROM pis.p19_report WHERE id = ?", Integer.class, reportId);
    }

    public UUID insertContent(UUID reportId, int version, String state, String patient, String encounter, String caseNo, String specimen, String clinical, String specimenInfo, String gross, String microscopic, String conclusion, String supplementary, String technical, UUID diagnosisVersion, String template, String actor, Instant now, UUID prior) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pis.p19_report_content_version (id, report_id, version_no, content_state_code, patient_snapshot, encounter_snapshot, case_no_snapshot, specimen_material_summary, clinical_information, specimen_information, gross_description, microscopic_description, diagnosis_conclusion, supplementary_note, technical_result_reference_summary, diagnosis_version_id, template_logic_version, formed_by_ref, formed_at, prior_version_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, reportId, version, state, patient, encounter, caseNo, specimen, clinical, specimenInfo, gross, microscopic, conclusion, supplementary, technical, diagnosisVersion, template, actor, Timestamp.from(now), prior);
        return id;
    }

    public Optional<ContentVersionRow> content(UUID id) {
        return jdbc.query("SELECT id, report_id, version_no, content_state_code, patient_snapshot, encounter_snapshot, case_no_snapshot, specimen_material_summary, clinical_information, specimen_information, gross_description, microscopic_description, diagnosis_conclusion, supplementary_note, technical_result_reference_summary, diagnosis_version_id, template_logic_version, formed_by_ref, formed_at, prior_version_id FROM pis.p19_report_content_version WHERE id = ?", rs -> rs.next() ? Optional.of(new ContentVersionRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), (UUID) rs.getObject(16), rs.getString(17), rs.getString(18), rs.getTimestamp(19).toInstant(), (UUID) rs.getObject(20))) : Optional.empty(), id);
    }

    public int contentState(UUID id, String state) { return jdbc.update("UPDATE pis.p19_report_content_version SET content_state_code = ? WHERE id = ? AND content_state_code NOT IN ('SIGNED','WITHDRAWN')", state, id); }
    public void section(UUID contentId, String code, String body, String source, String adopted, String actor, Instant now) { jdbc.update("INSERT INTO pis.p19_report_section_version (id, report_content_version_id, section_code, body_text, source_code, adopted_state_code, formed_by_ref, formed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), contentId, code, body, source, adopted, actor, Timestamp.from(now)); }
    public void resultReference(UUID reportId, UUID contentId, UUID projectId, String result, String version, String digest, String adoption, String actor, Instant now) { jdbc.update("INSERT INTO pis.p19_report_result_reference (id, report_id, report_content_version_id, technical_project_id, result_identity, result_version_reference, result_digest, adoption_state_code, recorded_by_ref, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), reportId, contentId, projectId, result, version, digest, adoption, actor, Timestamp.from(now)); }
    public UUID insertSigning(UUID reportId, UUID contentId, String actor, String operator, String responsible, String auth, UUID review, long version, Instant now) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO pis.p19_signing_fact (id, report_id, report_content_version_id, signing_actor_ref, actual_operator_ref, responsible_actor_ref, signed_at, enhanced_authentication_reference, second_review_reference, task_responsibility_snapshot, signed_object_version, signing_result_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SIGNED')", id, reportId, contentId, actor, operator, responsible, Timestamp.from(now), auth, review, responsible, version); return id; }
    public void relation(UUID reportId, UUID original, UUID replacement, String type, String reason, boolean current, String actor, Instant now) { jdbc.update("INSERT INTO pis.p19_report_revision_relation (id, report_id, original_version_id, replacement_version_id, relation_type_code, reason_text, current_effective_flag, formed_by_ref, formed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), reportId, original, replacement, type, reason, current, actor, Timestamp.from(now)); }
    public UUID insertSupplement(UUID reportId, UUID original, String reason, String actor, Instant now) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO pis.p19_report_supplement (id, report_id, original_version_id, reason_text, request_state_code, requested_by_ref, requested_at) VALUES (?, ?, ?, ?, 'REQUESTED', ?, ?)", id, reportId, original, reason, actor, Timestamp.from(now)); return id; }
    public UUID insertCorrection(UUID reportId, UUID original, String error, String reason, String actor, Instant now) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO pis.p19_report_correction (id, report_id, original_version_id, error_type_code, reason_text, request_state_code, requested_by_ref, requested_at) VALUES (?, ?, ?, ?, ?, 'REQUESTED', ?, ?)", id, reportId, original, error, reason, actor, Timestamp.from(now)); return id; }
    public UUID insertWithdrawal(UUID reportId, UUID original, String reason, String actor, Instant now) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO pis.p19_report_withdrawal_request (id, report_id, original_version_id, reason_text, request_state_code, requested_by_ref, requested_at) VALUES (?, ?, ?, ?, 'REQUESTED', ?, ?)", id, reportId, original, reason, actor, Timestamp.from(now)); return id; }
    public int updateSupplement(UUID id, String state, UUID version) { return jdbc.update("UPDATE pis.p19_report_supplement SET request_state_code = ?, supplement_version_id = ? WHERE id = ? AND request_state_code IN ('REQUESTED','SUBMITTED','APPROVED')", state, version, id); }
    public int updateCorrection(UUID id, String state, UUID version) { return jdbc.update("UPDATE pis.p19_report_correction SET request_state_code = ?, correction_version_id = ? WHERE id = ? AND request_state_code IN ('REQUESTED','APPROVED')", state, version, id); }
    public int updateCorrectionByVersion(UUID versionId, String state) { return jdbc.update("UPDATE pis.p19_report_correction SET request_state_code = ? WHERE correction_version_id = ? AND request_state_code = 'APPROVED'", state, versionId); }
    public int updateWithdrawal(UUID id, String state) { return jdbc.update("UPDATE pis.p19_report_withdrawal_request SET request_state_code = ? WHERE id = ? AND request_state_code IN ('REQUESTED','APPROVED')", state, id); }
    public void withdrawalFact(UUID requestId, UUID reportId, UUID original, String approver, String executor, String reason, Instant now) { jdbc.update("INSERT INTO pis.p19_report_withdrawal_fact (id, withdrawal_request_id, report_id, original_version_id, approved_by_ref, executed_by_ref, reason_text, executed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), requestId, reportId, original, approver, executor, reason, Timestamp.from(now)); }
    public boolean hasBlockingTechnicalOrder(UUID caseId) { return jdbc.queryForObject("SELECT COUNT(*) FROM pis.p18_technical_order_project p JOIN pis.p18_technical_order o ON o.id = p.technical_order_id WHERE o.case_id = ? AND p.project_type_code IN ('IHC','SPECIAL_STAIN') AND p.result_state_code IN ('WAITING','NOT_EXPECTED')", Integer.class, caseId) > 0; }
    public Optional<RevisionTargetRow> supplement(UUID id) { return revision("p19_report_supplement", id); }
    public Optional<RevisionTargetRow> correction(UUID id) { return revision("p19_report_correction", id); }
    public Optional<RevisionTargetRow> withdrawal(UUID id) { return revision("p19_report_withdrawal_request", id); }
    private Optional<RevisionTargetRow> revision(String table, UUID id) { return jdbc.query("SELECT id, report_id, original_version_id, " + (table.endsWith("supplement") ? "reason_text" : "reason_text") + ", request_state_code FROM pis." + table + " WHERE id = ?", rs -> rs.next() ? Optional.of(new RevisionTargetRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3), rs.getString(4), rs.getString(5))) : Optional.empty(), id); }
}
