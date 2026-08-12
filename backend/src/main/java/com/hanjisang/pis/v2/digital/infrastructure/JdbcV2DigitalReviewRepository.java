package com.hanjisang.pis.v2.digital.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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

    public UUID insertScreenshot(UUID digitalSlideId, String viewportJson, String storageReference,
            String actorRef, Instant now, String organizationReference) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pis_v2.digital_slide_screenshot
                    (id, digital_slide_id, viewport_json, storage_reference, created_at, created_by_ref,
                     organization_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, digitalSlideId, viewportJson, storageReference, Timestamp.from(now), actorRef,
                organizationReference);
        return id;
    }

    public record AnnotationRow(UUID annotationId, UUID digitalSlideId, String annotationTypeCode,
            String geometryJson, String label, String note, Instant createdAt, String createdByRef,
            Instant updatedAt, String updatedByRef) { }
    public record MeasurementRow(UUID measurementId, UUID digitalSlideId, String geometryJson, BigDecimal value,
            String unitCode, String measurementModeCode, Instant createdAt, String createdByRef) { }
}
