package com.hanjisang.pis.v2.digital.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2DigitalReviewRepository {

    private final JdbcTemplate jdbc;

    public JdbcV2DigitalReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean belongs(UUID digitalSlideId, String organizationReference) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.digital_slide d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE d.id = ? AND c.organization_reference = ?
                """, Integer.class, digitalSlideId, organizationReference);
        return count != null && count == 1;
    }

    public boolean lockDigitalSlide(UUID digitalSlideId, String organizationReference) {
        return !jdbc.query("""
                SELECT d.id FROM pis_v2.digital_slide d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE d.id = ? AND c.organization_reference = ?
                FOR UPDATE
                """, (rs, rowNum) -> rs.getObject(1, UUID.class), digitalSlideId,
                organizationReference).isEmpty();
    }

    public UUID insertAnnotation(UUID digitalSlideId, String typeCode, String geometryJson, String label, String note,
            String actorRef, Instant now, String organizationReference) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.digital_slide_annotation
                    (id, digital_slide_id, annotation_type_code, geometry_json, label, note,
                     created_at, created_by_ref, updated_at, updated_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, digitalSlideId, typeCode, geometryJson, label, note, Timestamp.from(now), actorRef,
                Timestamp.from(now), actorRef, organizationReference);
        return id;
    }

    public List<AnnotationRow> annotations(UUID digitalSlideId, String organizationReference) {
        return jdbc.query("""
                SELECT a.id, a.digital_slide_id, a.annotation_type_code, a.geometry_json, a.label, a.note,
                       a.created_at, a.created_by_ref, a.updated_at, a.updated_by_ref
                  FROM pis_v2.digital_slide_annotation a
                  WHERE a.digital_slide_id = ? AND a.organization_reference = ? AND a.deleted_at IS NULL
                  ORDER BY a.created_at, a.id
                """, (rs, rowNum) -> new AnnotationRow(rs.getObject("id", UUID.class),
                rs.getObject("digital_slide_id", UUID.class), rs.getString("annotation_type_code"),
                rs.getString("geometry_json"), rs.getString("label"), rs.getString("note"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by_ref"),
                rs.getTimestamp("updated_at").toInstant(), rs.getString("updated_by_ref")), digitalSlideId,
                organizationReference);
    }

    public UUID insertMeasurement(UUID digitalSlideId, String geometryJson, BigDecimal value, String unitCode,
            String modeCode, String actorRef, Instant now, String organizationReference) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.digital_slide_measurement
                    (id, digital_slide_id, geometry_json, measurement_value, unit_code, measurement_mode_code,
                     created_at, created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, digitalSlideId, geometryJson, value, unitCode, modeCode, Timestamp.from(now), actorRef,
                organizationReference);
        return id;
    }

    public List<MeasurementRow> measurements(UUID digitalSlideId, String organizationReference) {
        return jdbc.query("""
                SELECT id, digital_slide_id, geometry_json, measurement_value, unit_code, measurement_mode_code,
                       created_at, created_by_ref
                  FROM pis_v2.digital_slide_measurement
                 WHERE digital_slide_id = ? AND organization_reference = ?
                 ORDER BY created_at, id
                """, (rs, rowNum) -> new MeasurementRow(rs.getObject("id", UUID.class),
                rs.getObject("digital_slide_id", UUID.class), rs.getString("geometry_json"),
                rs.getBigDecimal("measurement_value"), rs.getString("unit_code"), rs.getString("measurement_mode_code"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by_ref")), digitalSlideId,
                organizationReference);
    }

    public void insertScreenshot(UUID id, UUID digitalSlideId, String viewportJson, String storageReference,
            String mediaType, String contentHash, byte[] contentData, String actorRef, Instant now,
            String organizationReference) {
        jdbc.update("""
                INSERT INTO pis_v2.digital_slide_screenshot
                    (id, digital_slide_id, viewport_json, storage_reference, media_type, content_hash, content_data,
                     created_at, created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, digitalSlideId, viewportJson, storageReference, mediaType, contentHash, contentData,
                Timestamp.from(now), actorRef, organizationReference);
    }

    public List<ScreenshotRow> screenshots(UUID digitalSlideId, String organizationReference) {
        return jdbc.query("""
                SELECT id, digital_slide_id, viewport_json, storage_reference, media_type, content_hash,
                       created_at, created_by_ref
                  FROM pis_v2.digital_slide_screenshot
                 WHERE digital_slide_id = ? AND organization_reference = ?
                 ORDER BY created_at, id
                """, (rs, rowNum) -> screenshot(rs), digitalSlideId, organizationReference);
    }

    public Optional<ScreenshotContentRow> screenshotContent(UUID screenshotId, String organizationReference) {
        return jdbc.query("""
                SELECT ss.id, ss.media_type, ss.content_hash, ss.content_data
                  FROM pis_v2.digital_slide_screenshot ss
                  JOIN pis_v2.digital_slide d ON d.id = ss.digital_slide_id
                  JOIN pis_v2.pathology_case c ON c.id = d.case_id
                 WHERE ss.id = ? AND ss.organization_reference = ? AND c.organization_reference = ?
                   AND ss.content_data IS NOT NULL
                """, rs -> rs.next() ? Optional.of(new ScreenshotContentRow(rs.getObject("id", UUID.class),
                rs.getString("media_type"), rs.getString("content_hash"), rs.getBytes("content_data")))
                : Optional.empty(), screenshotId, organizationReference, organizationReference);
    }

    public Optional<IdempotencyRow> findIdempotency(String operation, String key, String organizationReference) {
        return jdbc.query("""
                SELECT payload_digest, result_entity_id
                  FROM pis_v2.digital_review_command_idempotency
                 WHERE organization_reference = ? AND operation_code = ? AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IdempotencyRow(rs.getString("payload_digest"),
                rs.getObject("result_entity_id", UUID.class))) : Optional.empty(), organizationReference,
                operation, key);
    }

    public void insertIdempotency(String operation, String key, String payloadDigest, UUID resultEntityId,
            String actorRef, Instant now, String organizationReference) {
        jdbc.update("""
                INSERT INTO pis_v2.digital_review_command_idempotency
                    (id, operation_code, idempotency_key, payload_digest, result_entity_id, created_at,
                     created_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operation, key, payloadDigest, resultEntityId, Timestamp.from(now),
                actorRef, organizationReference);
    }

    public record AnnotationRow(UUID annotationId, UUID digitalSlideId, String annotationTypeCode,
            String geometryJson, String label, String note, Instant createdAt, String createdByRef,
            Instant updatedAt, String updatedByRef) { }
    public record MeasurementRow(UUID measurementId, UUID digitalSlideId, String geometryJson, BigDecimal value,
            String unitCode, String measurementModeCode, Instant createdAt, String createdByRef) { }
    public record ScreenshotRow(UUID screenshotId, UUID digitalSlideId, String viewportJson, String storageReference,
            String mediaType, String contentHash, Instant createdAt, String createdByRef) { }
    public record ScreenshotContentRow(UUID screenshotId, String mediaType, String contentHash, byte[] contentData) { }
    public record IdempotencyRow(String payloadDigest, UUID resultEntityId) { }

    private static ScreenshotRow screenshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ScreenshotRow(rs.getObject("id", UUID.class), rs.getObject("digital_slide_id", UUID.class),
                rs.getString("viewport_json"), rs.getString("storage_reference"), rs.getString("media_type"),
                rs.getString("content_hash"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"));
    }
}
