package com.hanjisang.pis.v2.digital.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.hanjisang.pis.v2.digital.domain.DigitalSlide;

@Repository
public class JdbcV2DigitalSlideRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcV2DigitalSlideRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void insert(DigitalSlide digitalSlide) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.digital_slide
                    (id, case_id, block_id, slide_id, binding_mode_code, status_code, viewer_reference,
                     source_platform, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, digitalSlide.id(), digitalSlide.caseId(), digitalSlide.blockId(), digitalSlide.slideId(),
                digitalSlide.bindingModeCode(), digitalSlide.statusCode(), digitalSlide.viewerReference(),
                digitalSlide.sourcePlatform(), Timestamp.from(digitalSlide.createdAt()), digitalSlide.createdBy(),
                Timestamp.from(digitalSlide.updatedAt()), digitalSlide.updatedBy());
    }

    public Optional<DigitalSlide> find(UUID id, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.case_id, d.block_id, d.slide_id, d.binding_mode_code, d.status_code,
                       d.viewer_reference, d.source_platform, d.created_at, d.created_by_ref, d.updated_at,
                       d.updated_by_ref
                FROM pis_v2.digital_slide d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE d.id = ? AND c.organization_reference = ?
                """, rs -> rs.next() ? Optional.of(toDigitalSlide(rs)) : Optional.empty(), id, organizationReference);
    }

    public List<DigitalSlide> findByCase(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT d.id, d.case_id, d.block_id, d.slide_id, d.binding_mode_code, d.status_code,
                       d.viewer_reference, d.source_platform, d.created_at, d.created_by_ref, d.updated_at,
                       d.updated_by_ref
                FROM pis_v2.digital_slide d
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE d.case_id = ? AND c.organization_reference = ? ORDER BY d.created_at, d.id
                """, (rs, rowNum) -> toDigitalSlide(rs), caseId, organizationReference);
    }

    public boolean updateBinding(UUID id, UUID blockId, UUID slideId, String statusCode, Instant updatedAt,
            String updatedBy, String organizationReference) {
        return jdbcTemplate.update("""
                UPDATE pis_v2.digital_slide d
                   SET block_id = ?, slide_id = ?, status_code = ?, updated_at = ?, updated_by_ref = ?
                 WHERE d.id = ? AND EXISTS (SELECT 1 FROM pis_v2.pathology_case c
                                            WHERE c.id = d.case_id AND c.organization_reference = ?)
                """, blockId, slideId, statusCode, Timestamp.from(updatedAt), updatedBy, id, organizationReference) == 1;
    }

    private static DigitalSlide toDigitalSlide(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DigitalSlide(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getObject("block_id", UUID.class), rs.getObject("slide_id", UUID.class),
                rs.getString("binding_mode_code"), rs.getString("status_code"), rs.getString("viewer_reference"),
                rs.getString("source_platform"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by_ref"), rs.getTimestamp("updated_at").toInstant(),
                rs.getString("updated_by_ref"));
    }
}
