package com.hanjisang.pis.v2.history.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.history.infrastructure.JdbcV2PatientHistoryRepository;

@Service
public class V2PatientHistoryApplicationService {

    private static final String QUERY_PERMISSION = "P14-PERM-048";

    private final JdbcV2PatientHistoryRepository repository;
    private final P15AuthorizationService authorization;

    public V2PatientHistoryApplicationService(JdbcV2PatientHistoryRepository repository,
            P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public PatientHistoryResult find(String patientReference) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        if (patientReference == null || patientReference.isBlank()) {
            throw new P15BusinessException("V2-PATIENT-HISTORY-INVALID", "患者引用不能为空");
        }
        List<PatientHistoryItem> items = repository.find(actor.hospitalScope(), patientReference.trim()).stream()
                .map(row -> new PatientHistoryItem(row.caseId(), row.pathologyNo(), row.businessTypeCode(),
                        row.businessTypeName(), row.occurredAt(), firstText(row.diagnosisText(), row.microscopicDescription()),
                        row.reportId(), row.reportNo(), row.reportStatus(), row.signedAt()))
                .toList();
        return new PatientHistoryResult(patientReference.trim(), items, Instant.now());
    }

    private static String firstText(String diagnosis, String microscopic) {
        if (diagnosis != null && !diagnosis.isBlank()) return diagnosis;
        return microscopic == null ? null : microscopic;
    }

    public record PatientHistoryResult(String patientReference, List<PatientHistoryItem> items,
            Instant refreshedAt) { }

    public record PatientHistoryItem(UUID caseId, String pathologyNo, String businessTypeCode,
            String businessTypeName, Instant occurredAt, String diagnosisSummary, UUID reportId,
            String reportNo, String reportStatus, Instant signedAt) { }
}
