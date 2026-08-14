package com.hanjisang.pis.v2.statistics.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2StatisticsRepository {

    private final JdbcTemplate jdbcTemplate;
    public JdbcV2StatisticsRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public SummaryCounts counts(String organizationReference) {
        return new SummaryCounts(count("pathology_case", organizationReference), count("specimen", organizationReference),
                count("grossing", organizationReference), count("block", organizationReference), count("slide", organizationReference),
                countDiagnosis("INITIAL", organizationReference), countDiagnosis("REVIEW", organizationReference),
                countDiagnosis("AUDIT", organizationReference), count("report", organizationReference),
                countBusiness("FROZEN", organizationReference), count("technical_order", organizationReference));
    }

    public List<BusinessTypeCount> businessTypes(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT bt.business_type_code, COUNT(*) FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE c.organization_reference = ? GROUP BY bt.business_type_code ORDER BY bt.business_type_code
                """, (rs, rowNum) -> new BusinessTypeCount(rs.getString(1), rs.getLong(2)), organizationReference);
    }

    public List<ReportTatRow> reportTatRows(String organizationReference) {
        return jdbcTemplate.query("""
                SELECT c.id AS case_id, c.case_no, COALESCE(s.patient_reference, '未填写') AS patient_reference,
                       bt.business_type_code, c.created_at AS started_at,
                       p.warning_minutes, p.target_minutes,
                       (SELECT MAX(r.signed_at)
                          FROM pis_v2.report r
                          JOIN pis_v2.diagnosis report_diagnosis ON report_diagnosis.id = r.diagnosis_id
                         WHERE r.case_id = c.id AND report_diagnosis.context_type = 'CASE'
                           AND r.report_nature_code = 'ORIGINAL' AND r.status_code = 'EFFECTIVE') AS signed_at,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM pis_v2.report_delay_declaration dd
                           JOIN pis_v2.diagnosis dd_diagnosis ON dd_diagnosis.id = dd.diagnosis_id
                           WHERE dd_diagnosis.case_id = c.id AND dd.resolved_at IS NULL
                       ) THEN TRUE ELSE FALSE END AS delayed
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                JOIN pis_v2.report_tat_policy p ON p.business_type_id = c.business_type_id
                    AND p.organization_reference = c.organization_reference AND p.enabled = TRUE
                LEFT JOIN pis_v2.case_context_snapshot s ON s.case_id = c.id
                    AND s.snapshot_version_no = (SELECT MAX(s2.snapshot_version_no)
                        FROM pis_v2.case_context_snapshot s2 WHERE s2.case_id = c.id)
                WHERE c.organization_reference = ? AND c.lifecycle_state_code <> 'CANCELLED'
                ORDER BY c.created_at DESC, c.id
                """, (rs, rowNum) -> new ReportTatRow(rs.getObject("case_id", UUID.class),
                        rs.getString("case_no"), rs.getString("patient_reference"),
                        rs.getString("business_type_code"), rs.getTimestamp("started_at").toInstant(),
                        rs.getInt("warning_minutes"), rs.getInt("target_minutes"),
                        rs.getTimestamp("signed_at") == null ? null : rs.getTimestamp("signed_at").toInstant(),
                        rs.getBoolean("delayed")), organizationReference);
    }

    private long count(String table, String organizationReference) {
        String sql = switch (table) {
            case "pathology_case" -> "SELECT COUNT(*) FROM pis_v2.pathology_case WHERE organization_reference = ?";
            case "specimen" -> "SELECT COUNT(*) FROM pis_v2.specimen WHERE organization_reference = ? AND deleted_at IS NULL";
            case "grossing" -> "SELECT COUNT(*) FROM pis_v2.grossing WHERE organization_reference = ? AND deleted_at IS NULL";
            case "block" -> "SELECT COUNT(*) FROM pis_v2.block WHERE organization_reference = ? AND deleted_at IS NULL";
            case "slide" -> "SELECT COUNT(*) FROM pis_v2.slide WHERE organization_reference = ? AND deleted_at IS NULL";
            case "report" -> "SELECT COUNT(*) FROM pis_v2.report WHERE organization_reference = ? AND status_code <> 'WITHDRAWN'";
            case "technical_order" -> "SELECT COUNT(*) FROM pis_v2.technical_order WHERE organization_reference = ?";
            case "FROZEN" -> "SELECT COUNT(*) FROM pis_v2.pathology_case c JOIN pis_v2.business_type bt ON bt.id = c.business_type_id WHERE c.organization_reference = ? AND bt.business_type_code = 'FROZEN'";
            default -> throw new IllegalArgumentException("Unsupported V2 statistics table: " + table);
        };
        Long value = jdbcTemplate.queryForObject(sql, Long.class, organizationReference);
        return value == null ? 0 : value;
    }

    private long countDiagnosis(String role, String organizationReference) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.responsibility_unit r
                JOIN pis_v2.diagnosis d ON d.id = r.diagnosis_id
                JOIN pis_v2.pathology_case c ON c.id = d.case_id
                WHERE r.role_code = ? AND c.organization_reference = ?
                """, Long.class, role, organizationReference);
        return value == null ? 0 : value;
    }

    private long countBusiness(String code, String organizationReference) { return count(code, organizationReference); }

    public record SummaryCounts(long registrationCount, long specimenCount, long grossingCount, long blockCount,
            long slideCount, long diagnosisInitialCount, long diagnosisReviewCount, long diagnosisAuditCount,
            long reportSignOutCount, long frozenCount, long technicalOrderCount) { }
    public record BusinessTypeCount(String businessTypeCode, long count) { }
    public record ReportTatRow(UUID caseId, String pathologyNo, String patientReference, String businessTypeCode,
            Instant startedAt, int warningMinutes, int targetMinutes, Instant signedAt, boolean delayed) { }
}
