package com.hanjisang.pis.v2.material.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2GrossingImageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2GrossingImageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GrossingContext> context(UUID grossingId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT g.id AS grossing_id, g.case_id, g.organization_reference
                FROM pis_v2.grossing g
                WHERE g.id = ? AND g.organization_reference = ? AND g.deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(new GrossingContext(rs.getObject("grossing_id", UUID.class),
                rs.getObject("case_id", UUID.class), rs.getString("organization_reference"))) : Optional.empty(),
                grossingId, organizationReference);
    }

    public boolean specimenBelongs(UUID specimenId, UUID caseId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pis_v2.specimen WHERE id = ? AND case_id = ? AND deleted_at IS NULL)
                """, Boolean.class, specimenId, caseId));
    }

    public UUID insertImage(UUID caseId, UUID grossingId, UUID specimenId, String imageName, String mediaType,
            String storageReference, String metadataJson, Instant capturedAt, String actorReference,
            String organizationReference) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing_image
                    (id, case_id, grossing_id, specimen_id, image_name, media_type, storage_reference,
                     metadata_json, captured_at, captured_by_ref, organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, caseId, grossingId, specimenId, imageName, mediaType, storageReference, metadataJson,
                Timestamp.from(capturedAt), actorReference, organizationReference);
        return id;
    }

    public List<ImageRow> images(UUID grossingId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT id, case_id, grossing_id, specimen_id, image_name, media_type, storage_reference,
                       metadata_json, captured_at, captured_by_ref, deleted_at, deletion_reason
                FROM pis_v2.grossing_image
                WHERE grossing_id = ? AND organization_reference = ?
                ORDER BY captured_at, id
                """, (rs, rowNum) -> new ImageRow(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("grossing_id", UUID.class), rs.getObject("specimen_id", UUID.class),
                rs.getString("image_name"), rs.getString("media_type"), rs.getString("storage_reference"),
                rs.getString("metadata_json"), rs.getTimestamp("captured_at").toInstant(),
                rs.getString("captured_by_ref"), rs.getTimestamp("deleted_at") == null ? null
                        : rs.getTimestamp("deleted_at").toInstant(), rs.getString("deletion_reason")),
                grossingId, organizationReference);
    }

    public Optional<UUID> imageCase(UUID imageId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT case_id FROM pis_v2.grossing_image
                WHERE id = ? AND organization_reference = ? AND deleted_at IS NULL
                """, rs -> rs.next() ? Optional.of(rs.getObject("case_id", UUID.class)) : Optional.empty(),
                imageId, organizationReference);
    }

    public void softDeleteImage(UUID imageId, String reason, String actorReference, Instant now,
            String organizationReference) {
        jdbcTemplate.update("""
                UPDATE pis_v2.grossing_image
                   SET deleted_at = ?, deleted_by_ref = ?, deletion_reason = ?
                 WHERE id = ? AND organization_reference = ? AND deleted_at IS NULL
                """, Timestamp.from(now), actorReference, reason, imageId, organizationReference);
    }

    public UUID insertAnnotation(UUID imageId, String typeCode, String geometryJson, String label, String note,
            String actorReference, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing_image_annotation
                    (id, image_id, annotation_type_code, geometry_json, label, note, created_at, created_by_ref,
                     updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, imageId, typeCode, geometryJson, label, note, Timestamp.from(now), actorReference,
                Timestamp.from(now), actorReference);
        return id;
    }

    public boolean updateAnnotation(UUID annotationId, UUID imageId, String typeCode, String geometryJson,
            String label, String note, String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.grossing_image_annotation
                   SET annotation_type_code = ?, geometry_json = ?, label = ?, note = ?,
                       updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND image_id = ? AND deleted_at IS NULL
                """, typeCode, geometryJson, label, note, Timestamp.from(now), actorReference,
                annotationId, imageId) == 1;
    }

    public List<AnnotationRow> annotations(UUID imageId) {
        return jdbcTemplate.query("""
                SELECT id, image_id, annotation_type_code, geometry_json, label, note, created_at, created_by_ref,
                       updated_at, updated_by_ref, deleted_at
                FROM pis_v2.grossing_image_annotation
                WHERE image_id = ? ORDER BY created_at, id
                """, (rs, rowNum) -> new AnnotationRow(rs.getObject("id", UUID.class),
                rs.getObject("image_id", UUID.class), rs.getString("annotation_type_code"),
                rs.getString("geometry_json"), rs.getString("label"), rs.getString("note"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("created_by_ref"),
                rs.getTimestamp("updated_at").toInstant(), rs.getString("updated_by_ref"),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toInstant()), imageId);
    }

    public boolean deleteAnnotation(UUID annotationId, String actorReference, Instant now) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.grossing_image_annotation
                   SET deleted_at = ?, deleted_by_ref = ?, updated_at = ?, updated_by_ref = ?
                 WHERE id = ? AND deleted_at IS NULL
                """, Timestamp.from(now), actorReference, Timestamp.from(now), actorReference, annotationId) == 1;
    }

    public UUID insertMeasurement(UUID imageId, String geometryJson, BigDecimal value, String unitCode,
            String measurementModeCode, String actorReference, Instant now) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO pis_v2.grossing_image_measurement
                    (id, image_id, geometry_json, "value", unit_code, measurement_mode_code, created_at, created_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, imageId, geometryJson, value, unitCode, measurementModeCode, Timestamp.from(now),
                actorReference);
        return id;
    }

    public record GrossingContext(UUID grossingId, UUID caseId, String organizationReference) { }
    public record ImageRow(UUID imageId, UUID caseId, UUID grossingId, UUID specimenId, String imageName,
            String mediaType, String storageReference, String metadataJson, Instant capturedAt, String capturedByRef,
            Instant deletedAt, String deletionReason) { }
    public record AnnotationRow(UUID annotationId, UUID imageId, String typeCode, String geometryJson, String label,
            String note, Instant createdAt, String createdByRef, Instant updatedAt, String updatedByRef,
            Instant deletedAt) { }
}
