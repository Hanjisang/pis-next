package com.hanjisang.pis.v2.qc.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcV2QcRepository {

    private final JdbcTemplate jdbcTemplate;
    public JdbcV2QcRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<QcRuleRow> rules() {
        return jdbcTemplate.query("""
                SELECT id, rule_code, rule_name, metric_code, warning_threshold, overdue_threshold
                FROM pis_v2.qc_rule WHERE active = TRUE ORDER BY rule_code
                """, (rs, rowNum) -> new QcRuleRow(rs.getObject("id", UUID.class), rs.getString("rule_code"),
                rs.getString("rule_name"), rs.getString("metric_code"), rs.getBigDecimal("warning_threshold"),
                rs.getBigDecimal("overdue_threshold")));
    }

    public Optional<QcRuleRow> rule(String code) {
        return jdbcTemplate.query("""
                SELECT id, rule_code, rule_name, metric_code, warning_threshold, overdue_threshold
                FROM pis_v2.qc_rule WHERE rule_code = ? AND active = TRUE
                """, rs -> rs.next() ? Optional.of(new QcRuleRow(rs.getObject("id", UUID.class),
                rs.getString("rule_code"), rs.getString("rule_name"), rs.getString("metric_code"),
                rs.getBigDecimal("warning_threshold"), rs.getBigDecimal("overdue_threshold"))) : Optional.empty(), code);
    }

    public Optional<TatRow> tat(UUID caseId, String organizationReference) {
        return jdbcTemplate.query("""
                SELECT c.created_at, MIN(r.signed_at) AS signed_at, bt.business_type_code
                FROM pis_v2.pathology_case c
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                LEFT JOIN pis_v2.report r ON r.case_id = c.id AND r.status_code <> 'WITHDRAWN'
                WHERE c.id = ? AND c.organization_reference = ?
                GROUP BY c.created_at, bt.business_type_code
                """, rs -> rs.next() ? Optional.of(new TatRow(rs.getTimestamp("created_at").toInstant(),
                timestamp(rs, "signed_at"), rs.getString("business_type_code"))) : Optional.empty(), caseId,
                organizationReference);
    }

    public BigDecimal reportWithdrawRate(String organizationReference) {
        return jdbcTemplate.queryForObject("""
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE CAST(SUM(CASE WHEN status_code = 'WITHDRAWN' THEN 1 ELSE 0 END) AS DECIMAL) / COUNT(*) END
                FROM pis_v2.report WHERE organization_reference = ?
                """, BigDecimal.class, organizationReference);
    }

    public BigDecimal slideReprintRate(String organizationReference) {
        return jdbcTemplate.queryForObject("""
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE CAST((SELECT COUNT(*) FROM pis_v2.print_log p
                                       WHERE p.entity_kind_code = 'SLIDE' AND p.result_code = 'PRINTED') AS DECIMAL)
                                      / COUNT(*) END
                FROM pis_v2.slide s JOIN pis_v2.pathology_case c ON c.id = s.case_id
                WHERE c.organization_reference = ?
                """, BigDecimal.class, organizationReference);
    }

    public void insertEvaluation(UUID id, UUID ruleId, UUID caseId, BigDecimal measure, String status,
            String actorRef, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO pis_v2.qc_evaluation
                    (id, rule_id, case_id, measure_value, status_code, evaluated_at, evaluated_by_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, ruleId, caseId, measure, status, Timestamp.from(now), actorRef);
    }

    public List<EvaluationRow> evaluations(UUID caseId) {
        String condition = caseId == null ? "" : " WHERE e.case_id = ?";
        Object[] args = caseId == null ? new Object[0] : new Object[] { caseId };
        return jdbcTemplate.query("""
                SELECT e.id, e.rule_id, e.case_id, r.rule_code, e.measure_value, e.status_code, e.evaluated_at
                FROM pis_v2.qc_evaluation e JOIN pis_v2.qc_rule r ON r.id = e.rule_id
                """ + condition + " ORDER BY e.evaluated_at DESC, e.id DESC", (rs, rowNum) -> new EvaluationRow(
                rs.getObject("id", UUID.class), rs.getObject("rule_id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("rule_code"), rs.getBigDecimal("measure_value"), rs.getString("status_code"),
                rs.getTimestamp("evaluated_at").toInstant()), args);
    }

    private static Instant timestamp(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }

    public record QcRuleRow(UUID id, String code, String name, String metricCode, BigDecimal warningThreshold,
            BigDecimal overdueThreshold) { }
    public record TatRow(Instant createdAt, Instant signedAt, String businessTypeCode) { }
    public record EvaluationRow(UUID id, UUID ruleId, UUID caseId, String ruleCode, BigDecimal measureValue,
            String statusCode, Instant evaluatedAt) { }
}
