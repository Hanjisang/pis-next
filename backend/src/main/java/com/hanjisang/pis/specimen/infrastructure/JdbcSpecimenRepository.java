package com.hanjisang.pis.specimen.infrastructure;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.specimen.domain.Specimen;

@Repository
public class JdbcSpecimenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSpecimenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Specimen insertExpected(UUID caseId, String specimenNo, String barcode, String specimenKindCode,
            String collectionSite, String collectionMethodCode, int expectedQuantity, String organizationReference,
            String actorRef, Instant now) {
        UUID specimenId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis.specimen
                (id, case_id, specimen_no, specimen_kind_code, specimen_source_code, collection_site_text,
                 collection_method_code, specimen_lifecycle_state_code, record_version_no, concurrency_version,
                 organization_reference, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, 'LOCAL_EXPECTED', ?, ?, ?, 1, 0, ?, ?, ?)
                """, specimenId, caseId, specimenNo, specimenKindCode, collectionSite, collectionMethodCode,
                Specimen.EXPECTED, organizationReference, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.specimen_container
                (id, specimen_id, container_barcode, expected_quantity, container_state_code, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, 'EXPECTED', ?, ?)
                """, UUID.randomUUID(), specimenId, barcode, expectedQuantity, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.clinical_state_current
                (object_id, object_kind_code, state_machine_code, state_code, concurrency_version, updated_at, updated_by_ref)
                VALUES (?, 'OBJ-003', 'P08-SM-003', ?, 0, ?, ?)
                """, specimenId, Specimen.EXPECTED, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.state_transition_history
                (id, object_id, object_kind_code, state_machine_code, source_state_code, target_state_code,
                 transition_event_code, resulting_version, occurred_at, recorded_by_ref, reason)
                VALUES (?, ?, 'OBJ-003', 'P08-SM-003', 'P08-SM-003-ST-01', 'P08-SM-003-ST-01',
                        'P12-API-008', 0, ?, ?, 'expected specimen registered')
                """, UUID.randomUUID(), specimenId, Timestamp.from(now), actorRef);
        return Specimen.expected(specimenId, caseId, specimenNo, specimenKindCode, collectionSite);
    }

    public Optional<Specimen> findById(UUID specimenId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, specimen_no, specimen_kind_code, collection_site_text,
                       specimen_lifecycle_state_code, concurrency_version
                FROM pis.specimen
                WHERE id = ? AND organization_reference = ?
                """, rs -> rs.next() ? Optional.of(Specimen.persisted(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getLong(7)))
                        : Optional.empty(), specimenId, organizationReference);
    }

    public Optional<Specimen> findByBarcode(String barcode, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT s.id, s.case_id, s.specimen_no, s.specimen_kind_code, s.collection_site_text,
                       s.specimen_lifecycle_state_code, s.concurrency_version
                FROM pis.specimen s JOIN pis.specimen_container c ON c.specimen_id = s.id
                WHERE c.container_barcode = ? AND s.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(Specimen.persisted(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getLong(7)))
                        : Optional.empty(), barcode, organizationReference);
    }

    public boolean hasReceivingFact(UUID specimenId, String digest) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis.handoff_record
                WHERE object_id = ? AND object_kind_code = 'OBJ-003' AND handoff_state_code = 'RECEIVED'
                  AND handoff_digest = ?
                """, Integer.class, specimenId, digest);
        return count != null && count > 0;
    }

    public int expectedQuantity(UUID specimenId) {
        Integer quantity = jdbcTemplate.queryForObject("""
                SELECT expected_quantity
                FROM pis.specimen_container
                WHERE specimen_id = ?
                """, Integer.class, specimenId);
        if (quantity == null) {
            throw new IllegalStateException("Expected quantity is missing for specimen container");
        }
        return quantity;
    }

    public boolean transitionToReceived(UUID specimenId, long expectedVersion, int actualQuantity, String actorRef,
            String digest, Instant now) {
        int changed = jdbcTemplate.update("""
                UPDATE pis.specimen
                   SET specimen_lifecycle_state_code = ?, received_at = ?, received_by_ref = ?,
                       record_version_no = record_version_no + 1,
                       concurrency_version = concurrency_version + 1
                 WHERE id = ? AND specimen_lifecycle_state_code = ? AND concurrency_version = ?
                """, Specimen.RECEIVED, Timestamp.from(now), actorRef, specimenId, Specimen.EXPECTED, expectedVersion);
        if (changed != 1) {
            return false;
        }
        jdbcTemplate.update("""
                UPDATE pis.specimen_container SET actual_quantity = ?, container_state_code = 'RECEIVED'
                WHERE specimen_id = ?
                """, actualQuantity, specimenId);
        int stateChanged = jdbcTemplate.update("""
                UPDATE pis.clinical_state_current SET state_code = ?, concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                WHERE object_id = ? AND state_machine_code = 'P08-SM-003' AND concurrency_version = ?
                """, Specimen.RECEIVED, expectedVersion + 1, Timestamp.from(now), actorRef, specimenId, expectedVersion);
        if (stateChanged != 1) {
            throw new IllegalStateException("Specimen clinical state transition was not applied");
        }
        jdbcTemplate.update("""
                INSERT INTO pis.state_transition_history
                (id, object_id, object_kind_code, state_machine_code, source_state_code, target_state_code,
                 transition_event_code, expected_version, resulting_version, occurred_at, recorded_by_ref, reason)
                VALUES (?, ?, 'OBJ-003', 'P08-SM-003', 'P08-SM-003-ST-01', 'P08-SM-003-ST-02',
                        'ARRIVAL', ?, ?, ?, ?, 'barcode arrival and identity check')
                """, UUID.randomUUID(), specimenId, expectedVersion, expectedVersion + 1, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.state_transition_history
                (id, object_id, object_kind_code, state_machine_code, source_state_code, target_state_code,
                 transition_event_code, expected_version, resulting_version, occurred_at, recorded_by_ref, reason)
                VALUES (?, ?, 'OBJ-003', 'P08-SM-003', 'P08-SM-003-ST-02', 'P08-SM-003-ST-03',
                        'P12-API-009', ?, ?, ?, ?, 'identity, source and quantity verified')
                """, UUID.randomUUID(), specimenId, expectedVersion, expectedVersion + 1, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.handoff_record
                (id, object_id, object_kind_code, from_actor_ref, to_actor_ref, handoff_state_code,
                 handoff_digest, occurred_at, recorded_by_ref)
                VALUES (?, ?, 'OBJ-003', 'SYNTHETIC-SENDER', ?, 'RECEIVED', ?, ?, ?)
                """, UUID.randomUUID(), specimenId, actorRef, digest, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.operation_responsibility
                (id, object_id, object_kind_code, responsibility_type_code, responsible_actor_ref,
                 actual_actor_ref, created_at, created_by_ref)
                VALUES (?, ?, 'OBJ-003', 'P14-TASK-002', ?, ?, ?, ?)
                """, UUID.randomUUID(), specimenId, actorRef, actorRef, Timestamp.from(now), actorRef);
        return true;
    }

    public boolean isolate(UUID specimenId, long expectedVersion, String reason, String actorRef, Instant now) {
        int changed = jdbcTemplate.update("""
                UPDATE pis.specimen
                   SET specimen_lifecycle_state_code = ?, specimen_difference_code = ?,
                       record_version_no = record_version_no + 1, concurrency_version = concurrency_version + 1
                 WHERE id = ? AND specimen_lifecycle_state_code IN (?, ?) AND concurrency_version = ?
                """, Specimen.ISOLATED, reason, specimenId, Specimen.RECEIVED, Specimen.WAITING_VERIFICATION,
                expectedVersion);
        if (changed != 1) {
            return false;
        }
        int stateChanged = jdbcTemplate.update("""
                UPDATE pis.clinical_state_current SET state_code = ?, concurrency_version = ?, updated_at = ?, updated_by_ref = ?
                WHERE object_id = ? AND state_machine_code = 'P08-SM-003' AND concurrency_version = ?
                """, Specimen.ISOLATED, expectedVersion + 1, Timestamp.from(now), actorRef, specimenId, expectedVersion);
        if (stateChanged != 1) {
            throw new IllegalStateException("Specimen clinical state isolation was not applied");
        }
        jdbcTemplate.update("""
                INSERT INTO pis.business_exception
                (id, object_id, object_kind_code, error_code, exception_state_code, reason, created_at, created_by_ref)
                VALUES (?, ?, 'OBJ-003', 'P12-ERR-025', 'OPEN', ?, ?, ?)
                """, UUID.randomUUID(), specimenId, reason, Timestamp.from(now), actorRef);
        jdbcTemplate.update("""
                INSERT INTO pis.state_transition_history
                (id, object_id, object_kind_code, state_machine_code, source_state_code, target_state_code,
                 transition_event_code, expected_version, resulting_version, occurred_at, recorded_by_ref, reason)
                VALUES (?, ?, 'OBJ-003', 'P08-SM-003', 'P08-SM-003-ST-03', 'P08-SM-003-ST-04',
                        'ISOLATE', ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), specimenId, expectedVersion, expectedVersion + 1, Timestamp.from(now), actorRef, reason);
        return true;
    }

    public boolean appendHandoff(UUID specimenId, String organizationReference, String fromActor, String toActor,
            String digest, String actorRef, Instant now) {
        int changed = jdbcTemplate.update("""
                INSERT INTO pis.handoff_record
                (id, object_id, object_kind_code, from_actor_ref, to_actor_ref, handoff_state_code,
                 handoff_digest, occurred_at, recorded_by_ref)
                SELECT ?, ?, 'OBJ-003', ?, ?, 'SIGNED', ?, ?, ?
                WHERE EXISTS (SELECT 1 FROM pis.specimen
                              WHERE id = ? AND organization_reference = ? AND specimen_lifecycle_state_code = ?)
                ON CONFLICT (object_id, handoff_digest) DO NOTHING
                """, UUID.randomUUID(), specimenId, fromActor, toActor, digest, Timestamp.from(now), actorRef, specimenId,
                organizationReference, Specimen.RECEIVED);
        return changed == 1;
    }

    public List<Map<String, Object>> receivingQueue(String organizationReference) {
        return jdbcTemplate.queryForList("""
                SELECT s.id AS specimen_id, s.case_id, s.specimen_no, c.container_barcode,
                       s.specimen_lifecycle_state_code, s.concurrency_version, s.created_at
                FROM pis.specimen s JOIN pis.specimen_container c ON c.specimen_id = s.id
                WHERE s.organization_reference = ? AND s.specimen_lifecycle_state_code IN (?, ?, ?)
                ORDER BY s.created_at ASC LIMIT 200
                """, organizationReference, Specimen.EXPECTED, Specimen.WAITING_VERIFICATION, Specimen.ISOLATED);
    }

    public List<Map<String, Object>> trace(UUID caseId, String organizationReference) {
        return jdbcTemplate.queryForList("""
                SELECT s.id AS specimen_id, s.specimen_no, s.specimen_kind_code, s.collection_site_text,
                       s.specimen_lifecycle_state_code, s.concurrency_version, c.container_barcode,
                       c.container_state_code, c.expected_quantity, c.actual_quantity
                FROM pis.specimen s JOIN pis.specimen_container c ON c.specimen_id = s.id
                WHERE s.case_id = ? AND s.organization_reference = ? ORDER BY s.created_at ASC
                """, caseId, organizationReference);
    }
}
