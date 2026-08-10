package com.hanjisang.pis.v2.report.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.v2.report.infrastructure.JdbcV2ReportCenterRepository;

@Service
public class V2ReportCenterApplicationService {

    private final JdbcV2ReportCenterRepository repository;
    private final P15AuthorizationService authorization;

    public V2ReportCenterApplicationService(JdbcV2ReportCenterRepository repository,
            P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public ReportCenterResult get() {
        var actor = authorization.require(V2ReportApplicationService.REPORT_QUERY);
        List<QueueItem> items = repository.find(actor.hospitalScope()).stream()
                .map(row -> new QueueItem(row.diagnosisId(), row.caseId(), row.pathologyNo(), row.patientReference(),
                        row.queueCode(), row.reportId(), row.reportNo(), row.statusCode(), row.occurredAt(),
                        target(row.queueCode())))
                .toList();
        return new ReportCenterResult(items,
                new Counts(count(items, "WAITING_SIGN"), count(items, "SIGNED"), count(items, "WITHDRAWN"),
                        count(items, "SUPPLEMENTAL"), items.stream().filter(item -> "SIGNED".equals(item.queueCode()))
                                .limit(20).count()),
                Instant.now());
    }

    private static long count(List<QueueItem> items, String queueCode) {
        return items.stream().filter(item -> queueCode.equals(item.queueCode())).count();
    }

    private static String target(String queueCode) {
        return "WAITING_SIGN".equals(queueCode) ? "待签发" : "打开病例报告上下文";
    }

    public record ReportCenterResult(List<QueueItem> items, Counts counts, Instant refreshedAt) { }
    public record Counts(long waitingSign, long signed, long withdrawn, long supplemental, long recentSigned) { }
    public record QueueItem(java.util.UUID diagnosisId, java.util.UUID caseId, String pathologyNo,
            String patientReference, String queueCode, java.util.UUID reportId, String reportNo, String statusCode,
            Instant occurredAt, String targetLabel) { }
}
