package com.hanjisang.pis.v2.registration.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.registration.domain.ApplicationItemMapping;
import com.hanjisang.pis.v2.registration.domain.BusinessType;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.PathologyNumberRule;
import com.hanjisang.pis.v2.registration.domain.Specimen;

@Repository
public class JdbcV2RegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2RegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Routing> findRouting(String applicationItemCode) {
        return jdbcTemplate.query("""
                SELECT bt.id AS business_type_id, bt.business_type_code, bt.display_name, bt.modality_code,
                       bt.active AS business_type_active, bt.configuration_version,
                       m.id AS mapping_id, m.application_item_code, m.default_specimen_kind_code,
                       m.required, m.sequence_no, m.active AS mapping_active
                FROM pis_v2.application_item_mapping m
                JOIN pis_v2.business_type bt ON bt.id = m.business_type_id
                WHERE m.application_item_code = ? AND m.active = TRUE AND bt.active = TRUE
                """, rs -> rs.next() ? Optional.of(new Routing(
                        BusinessType.define(rs.getObject("business_type_id", UUID.class),
                                rs.getString("business_type_code"), rs.getString("display_name"),
                                rs.getString("modality_code"), rs.getBoolean("business_type_active"),
                                rs.getInt("configuration_version")),
                        ApplicationItemMapping.map(rs.getObject("mapping_id", UUID.class),
                                rs.getString("application_item_code"), rs.getObject("business_type_id", UUID.class),
                                rs.getString("business_type_code"), rs.getString("default_specimen_kind_code"),
                                rs.getBoolean("required"), rs.getInt("sequence_no"),
                                rs.getBoolean("mapping_active")))) : Optional.empty(), applicationItemCode);
    }

    public Optional<BusinessType> findBusinessType(String businessTypeCode) {
        return jdbcTemplate.query("""
                SELECT id, business_type_code, display_name, modality_code, active, configuration_version
                FROM pis_v2.business_type WHERE business_type_code = ?
                """, rs -> rs.next() ? Optional.of(BusinessType.define(rs.getObject("id", UUID.class),
                rs.getString("business_type_code"), rs.getString("display_name"), rs.getString("modality_code"),
                rs.getBoolean("active"), rs.getInt("configuration_version"))) : Optional.empty(), businessTypeCode);
    }

    public Optional<String> findActiveApplicationItemCode(String businessTypeCode) {
        return jdbcTemplate.query("""
                SELECT m.application_item_code
                FROM pis_v2.application_item_mapping m
                JOIN pis_v2.business_type bt ON bt.id = m.business_type_id
                WHERE bt.business_type_code = ? AND m.active = TRUE AND bt.active = TRUE
                ORDER BY m.sequence_no, m.application_item_code
                LIMIT 1
                """, rs -> rs.next() ? Optional.of(rs.getString("application_item_code")) : Optional.empty(),
                businessTypeCode);
    }

    public List<ApplicationMappingOption> findActiveApplicationMappings(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT m.application_item_code, m.default_specimen_kind_code,
                       bt.business_type_code, bt.display_name, bt.modality_code
                FROM pis_v2.application_item_mapping m
                JOIN pis_v2.business_type bt ON bt.id = m.business_type_id
                WHERE m.active = TRUE AND bt.active = TRUE
                ORDER BY m.sequence_no, m.application_item_code
                """, (rs, rowNum) -> new ApplicationMappingOption(rs.getString("application_item_code"),
                rs.getString("default_specimen_kind_code"), rs.getString("business_type_code"),
                rs.getString("display_name"), rs.getString("modality_code")));
    }

    public List<RegistrationCaseRow> findRecentRegistrations(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT c.id, c.case_no, c.external_application_id, c.application_item_code,
                       bt.business_type_code, bt.display_name, ctx.patient_reference, c.created_at
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.case_context_snapshot ctx
                  ON ctx.case_id = c.id
                 AND ctx.snapshot_version_no = (
                     SELECT MAX(ctx2.snapshot_version_no)
                     FROM pis_v2.case_context_snapshot ctx2 WHERE ctx2.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code = 'ACTIVE'
                  AND c.created_at >= CURRENT_DATE
                ORDER BY c.created_at DESC, c.id DESC
                FETCH FIRST 50 ROWS ONLY
                """, (rs, rowNum) -> new RegistrationCaseRow(rs.getObject("id", UUID.class),
                rs.getString("case_no"), rs.getString("external_application_id"),
                rs.getString("application_item_code"), rs.getString("business_type_code"),
                rs.getString("display_name"), rs.getString("patient_reference"),
                rs.getTimestamp("created_at").toInstant()), organizationReference);
    }

    public String allocateNumber(String organizationReference, String businessTypeCode, String numberKindCode,
            Instant now) {
        NumberRuleRow row = jdbcTemplate.query("""
                SELECT r.business_type_id, r.prefix, r.scope_code, r.padding_width, r.active, r.next_serial
                FROM pis_v2.pathology_number_rule r
                JOIN pis_v2.business_type bt ON bt.id = r.business_type_id
                WHERE r.organization_reference = ? AND bt.business_type_code = ?
                  AND r.number_kind_code = ? AND r.active = TRUE
                FOR UPDATE
                """, rs -> rs.next() ? new NumberRuleRow(rs.getObject("business_type_id", UUID.class),
                        rs.getString("prefix"), rs.getString("scope_code"), rs.getInt("padding_width"),
                        rs.getBoolean("active"), rs.getLong("next_serial")) : null,
                organizationReference, businessTypeCode, numberKindCode);
        if (row == null) {
            throw new IllegalStateException("未找到生效的V2编号规则：" + businessTypeCode + "/" + numberKindCode);
        }
        PathologyNumberRule rule = PathologyNumberRule.configure(organizationReference, businessTypeCode,
                numberKindCode, row.prefix(), row.scopeCode(), row.paddingWidth(), row.active());
        String number = rule.format(row.nextSerial());
        int changed = jdbcTemplate.update("""
                UPDATE pis_v2.pathology_number_rule
                   SET next_serial = next_serial + 1, updated_at = ?
                 WHERE business_type_id = ? AND organization_reference = ? AND number_kind_code = ?
                   AND active = TRUE AND next_serial = ?
                """, Timestamp.from(now), row.businessTypeId(), organizationReference, numberKindCode,
                row.nextSerial());
        if (changed != 1) {
            throw new IllegalStateException("V2编号规则并发更新失败");
        }
        return number;
    }

    public Optional<IdempotencyResult> findIdempotency(String operationCode, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT payload_digest, result_kind_code, result_case_id, result_specimen_id
                FROM pis_v2.idempotency_record
                WHERE operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyResult(rs.getString("payload_digest"),
                        rs.getString("result_kind_code"), rs.getObject("result_case_id", UUID.class),
                        rs.getObject("result_specimen_id", UUID.class))) : Optional.empty(), operationCode,
                idempotencyKey);
    }

    public boolean insertIdempotency(String operationCode, String idempotencyKey, String payloadDigest,
            String resultKindCode, UUID resultCaseId, UUID resultSpecimenId, String actorRef, Instant now) {
        return jdbcTemplate.update("""
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
                """, UUID.randomUUID(), operationCode, idempotencyKey, payloadDigest, resultKindCode, resultCaseId,
                resultSpecimenId, Timestamp.from(now), actorRef) == 1;
    }

    public void insertCase(Case pathologyCase, String organizationReference, Instant now, String actorRef) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     frozen_source_case_id, organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, pathologyCase.id(), pathologyCase.caseNo(), pathologyCase.sourceSystemCode(),
                pathologyCase.externalApplicationId(), pathologyCase.applicationItemCode(), pathologyCase.businessTypeId(),
                pathologyCase.lifecycleStateCode(), pathologyCase.numberBindingActive(),
                pathologyCase.concurrencyVersion(), pathologyCase.frozenSourceCaseId(), organizationReference,
                Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.case_context_snapshot
                    (id, case_id, patient_reference, visit_reference, snapshot_version_no, captured_at, captured_by_ref)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """, UUID.randomUUID(), pathologyCase.id(), pathologyCase.patientReference(),
                pathologyCase.visitReference(), Timestamp.from(now), actorRef);
    }

    public Optional<Case> findCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT c.id, c.case_no, c.source_system_code, c.external_application_id, c.application_item_code,
                       c.business_type_id, bt.business_type_code, s.patient_reference, s.visit_reference,
                       c.frozen_source_case_id, c.lifecycle_state_code, c.number_binding_active, c.concurrency_version
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id AND s.snapshot_version_no = 1
                WHERE c.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(Case.persistedWithFrozenSource(rs.getObject("id", UUID.class),
                        rs.getString("case_no"), rs.getString("source_system_code"),
                        rs.getString("external_application_id"), rs.getString("application_item_code"),
                        rs.getObject("business_type_id", UUID.class), rs.getString("business_type_code"),
                        rs.getString("patient_reference"), rs.getString("visit_reference"),
                        rs.getObject("frozen_source_case_id", UUID.class), rs.getString("lifecycle_state_code"),
                        rs.getBoolean("number_binding_active"),
                        rs.getLong("concurrency_version"))) : Optional.empty(), caseId, organizationReference);
    }

    public void insertSpecimen(Specimen specimen, String organizationReference, String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_kind_code, source_kind_code, source_reference,
                     collection_site, collection_method_code, label_code, concurrency_version, organization_reference,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, specimen.id(), specimen.caseId(), specimen.specimenNo(), specimen.specimenCode(),
                specimen.specimenKindCode(), specimen.sourceKindCode(), specimen.sourceReference(),
                specimen.collectionSite(), specimen.collectionMethodCode(), specimen.labelCode(),
                specimen.concurrencyVersion(), organizationReference, Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef);
    }

    public Optional<Specimen> findSpecimen(UUID specimenId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, specimen_no, specimen_code, specimen_kind_code, source_kind_code, source_reference,
                       collection_site, collection_method_code, label_code, deleted_at, deletion_reason,
                       concurrency_version
                FROM pis_v2.specimen
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(Specimen.persisted(rs.getObject("id", UUID.class),
                        rs.getObject("case_id", UUID.class), rs.getString("specimen_no"),
                        rs.getString("specimen_code"), rs.getString("specimen_kind_code"),
                        rs.getString("source_kind_code"), rs.getString("source_reference"),
                        rs.getString("collection_site"), rs.getString("collection_method_code"),
                        rs.getString("label_code"), rs.getTimestamp("deleted_at") == null ? null
                                : rs.getTimestamp("deleted_at").toInstant(),
                        rs.getString("deletion_reason"), rs.getLong("concurrency_version"))) : Optional.empty(),
                specimenId, organizationReference);
    }

    public Optional<UUID> findSpecimenIdByCode(UUID caseId, String specimenCode) {
        return jdbcTemplate.query("""
                SELECT id
                FROM pis_v2.specimen
                WHERE case_id = ? AND specimen_code = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty(), caseId,
                specimenCode);
    }

    public Optional<UUID> findSpecimenIdByLabel(String organizationReference, String labelCode) {
        return jdbcTemplate.query("""
                SELECT id
                FROM pis_v2.specimen
                WHERE organization_reference = ? AND label_code = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty(),
                organizationReference, labelCode);
    }

    public boolean updateSpecimen(Specimen specimen, String organizationReference, long expectedVersion,
            String actorRef, Instant now) {
        int changed = jdbcTemplate.update("""
                UPDATE pis_v2.specimen
                   SET specimen_code = ?, specimen_kind_code = ?, source_kind_code = ?, source_reference = ?,
                       collection_site = ?, collection_method_code = ?, label_code = ?,
                       concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND deleted_at IS NULL
                   AND concurrency_version = ?
                """, specimen.specimenCode(), specimen.specimenKindCode(), specimen.sourceKindCode(),
                specimen.sourceReference(), specimen.collectionSite(), specimen.collectionMethodCode(),
                specimen.labelCode(), Timestamp.from(now), actorRef, specimen.id(), organizationReference,
                expectedVersion);
        return changed == 1;
    }

    public boolean softDeleteSpecimen(UUID specimenId, String organizationReference, long expectedVersion,
            String reason, String actorRef, Instant now) {
        int changed = jdbcTemplate.update("""
                UPDATE pis_v2.specimen
                   SET deleted_at = ?, deleted_by_ref = ?, deletion_reason = ?,
                       concurrency_version = concurrency_version + 1, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND organization_reference = ? AND deleted_at IS NULL
                   AND concurrency_version = ?
                """, Timestamp.from(now), actorRef, reason, Timestamp.from(now), actorRef, specimenId,
                organizationReference, expectedVersion);
        return changed == 1;
    }

    public record Routing(BusinessType businessType, ApplicationItemMapping mapping) { }

    public record ApplicationMappingOption(String applicationItemCode, String defaultSpecimenKindCode,
            String businessTypeCode, String businessTypeName, String modalityCode) { }

    public record RegistrationCaseRow(UUID caseId, String caseNo, String applicationNo,
            String applicationItemCode, String businessTypeCode, String businessTypeName,
            String patientReference, Instant registeredAt) { }

    public record IdempotencyResult(String payloadDigest, String resultKindCode, UUID resultCaseId,
            UUID resultSpecimenId) { }

    private record NumberRuleRow(UUID businessTypeId, String prefix, String scopeCode, int paddingWidth,
            boolean active, long nextSerial) { }
}
