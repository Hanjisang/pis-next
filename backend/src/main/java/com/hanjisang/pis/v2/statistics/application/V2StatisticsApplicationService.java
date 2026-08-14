package com.hanjisang.pis.v2.statistics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.v2.statistics.infrastructure.JdbcV2StatisticsRepository;
import com.hanjisang.pis.v2.statistics.infrastructure.JdbcV2StatisticsRepository.ReportTatRow;

@Service
public class V2StatisticsApplicationService {

    private final JdbcV2StatisticsRepository repository;
    private final P15AuthorizationService authorization;

    public V2StatisticsApplicationService(JdbcV2StatisticsRepository repository, P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public StatisticsResult summary() {
        var actor = authorization.require("P14-PERM-048");
        Instant now = Instant.now();
        List<ReportTatRow> tatRows = repository.reportTatRows(actor.hospitalScope());
        return new StatisticsResult(repository.counts(actor.hospitalScope()),
                repository.businessTypes(actor.hospitalScope()), reportTat(tatRows, now));
    }

    private static ReportTatStatistics reportTat(List<ReportTatRow> rows, Instant now) {
        List<TatCase> cases = rows.stream().map(row -> tatCase(row, now)).toList();
        List<TatCase> completed = cases.stream().filter(item -> item.signedAt() != null).toList();
        long completedOnTime = completed.stream().filter(item -> "COMPLETED_ON_TIME".equals(item.status())).count();
        long completedOverdue = completed.stream().filter(item -> "COMPLETED_OVERDUE".equals(item.status())).count();
        long averageMinutes = completed.isEmpty() ? 0 : Math.round(completed.stream()
                .mapToLong(TatCase::elapsedMinutes).average().orElse(0));
        BigDecimal complianceRate = completed.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(completedOnTime * 100.0 / completed.size()).setScale(1, RoundingMode.HALF_UP);
        List<TatCase> overdueCases = cases.stream().filter(item -> "OVERDUE".equals(item.status()))
                .sorted(Comparator.comparing(TatCase::dueAt)).toList();
        return new ReportTatStatistics(rows.size(), completed.size(), completedOnTime, completedOverdue,
                cases.stream().filter(item -> "WARNING".equals(item.status())).count(), overdueCases.size(),
                cases.stream().filter(TatCase::delayed).count(), averageMinutes, complianceRate, overdueCases);
    }

    private static TatCase tatCase(ReportTatRow row, Instant now) {
        Instant warningAt = row.startedAt().plus(Duration.ofMinutes(row.warningMinutes()));
        Instant dueAt = row.startedAt().plus(Duration.ofMinutes(row.targetMinutes()));
        Instant end = row.signedAt() == null ? now : row.signedAt();
        String status;
        if (row.signedAt() != null) status = end.isAfter(dueAt) ? "COMPLETED_OVERDUE" : "COMPLETED_ON_TIME";
        else if (now.isAfter(dueAt)) status = "OVERDUE";
        else if (!now.isBefore(warningAt)) status = "WARNING";
        else status = "NORMAL";
        return new TatCase(row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(), status,
                Math.max(0, Duration.between(row.startedAt(), end).toMinutes()), row.startedAt(), warningAt, dueAt,
                row.signedAt(), row.delayed());
    }

    public record StatisticsResult(JdbcV2StatisticsRepository.SummaryCounts counts,
            List<JdbcV2StatisticsRepository.BusinessTypeCount> businessTypeDistribution,
            ReportTatStatistics reportTat) { }
    public record ReportTatStatistics(long policyCaseCount, long completedCount, long completedOnTime,
            long completedOverdue, long activeWarning, long activeOverdue, long activeDelayed,
            long averageCompletedMinutes, BigDecimal complianceRate, List<TatCase> overdueCases) { }
    public record TatCase(UUID caseId, String pathologyNo, String patientReference, String businessTypeCode,
            String status, long elapsedMinutes, Instant startedAt, Instant warningAt, Instant dueAt,
            Instant signedAt, boolean delayed) { }
}
