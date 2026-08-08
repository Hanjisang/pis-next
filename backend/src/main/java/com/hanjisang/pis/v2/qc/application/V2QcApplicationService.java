package com.hanjisang.pis.v2.qc.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.qc.infrastructure.JdbcV2QcRepository;
import com.hanjisang.pis.v2.qc.infrastructure.JdbcV2QcRepository.EvaluationRow;
import com.hanjisang.pis.v2.qc.infrastructure.JdbcV2QcRepository.QcRuleRow;

@Service
public class V2QcApplicationService {

    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2QcRepository repository;
    private final P15AuthorizationService authorization;

    public V2QcApplicationService(JdbcV2QcRepository repository, P15AuthorizationService authorization) {
        this.repository = repository; this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<QcRuleResult> rules() {
        authorization.require(QUERY_PERMISSION);
        return repository.rules().stream().map(this::rule).toList();
    }

    @Transactional
    public List<QcEvaluationResult> evaluate(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        Instant now = Instant.now();
        for (QcRuleRow rule : repository.rules()) {
            BigDecimal measure = measure(rule, caseId, actor);
            if (measure == null) continue;
            repository.insertEvaluation(UUID.randomUUID(), rule.id(), caseId, measure, status(rule, measure),
                    actor.actorId(), now);
        }
        return evaluations(caseId, actor);
    }

    @Transactional(readOnly = true)
    public List<QcEvaluationResult> evaluations(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return evaluations(caseId, actor);
    }

    private List<QcEvaluationResult> evaluations(UUID caseId, ActorContext actor) {
        return repository.evaluations(caseId).stream().map(this::evaluation).toList();
    }

    private BigDecimal measure(QcRuleRow rule, UUID caseId, ActorContext actor) {
        return switch (rule.code()) {
            case "ROUTINE_TAT", "FROZEN_TAT" -> caseId == null ? null : repository.tat(caseId, actor.hospitalScope())
                    .filter(row -> "FROZEN_TAT".equals(rule.code()) == "FROZEN".equals(row.businessTypeCode()))
                    .map(row -> BigDecimal.valueOf(Duration.between(row.createdAt(), row.signedAt() == null ? Instant.now() : row.signedAt()).toSeconds() / 3600.0))
                    .orElse(null);
            case "REPORT_WITHDRAW_RATE" -> repository.reportWithdrawRate(actor.hospitalScope());
            case "SLIDE_REPRINT_RATE" -> repository.slideReprintRate(actor.hospitalScope());
            default -> null;
        };
    }

    private static String status(QcRuleRow rule, BigDecimal value) {
        if (value.compareTo(rule.overdueThreshold()) >= 0) return "OVERDUE";
        if (value.compareTo(rule.warningThreshold()) >= 0) return "WARNING";
        return "NORMAL";
    }

    private QcRuleResult rule(QcRuleRow row) { return new QcRuleResult(row.code(), row.name(), row.metricCode(), row.warningThreshold(), row.overdueThreshold()); }
    private QcEvaluationResult evaluation(EvaluationRow row) { return new QcEvaluationResult(row.id(), row.caseId(), row.ruleCode(), row.measureValue(), row.statusCode(), row.evaluatedAt()); }

    public record QcRuleResult(String ruleCode, String ruleName, String metricCode, BigDecimal warningThreshold, BigDecimal overdueThreshold) { }
    public record QcEvaluationResult(UUID evaluationId, UUID caseId, String ruleCode, BigDecimal measureValue, String statusCode, Instant evaluatedAt) { }
}
