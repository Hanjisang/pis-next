package com.hanjisang.pis.v2.workbench.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.AuthenticationContext;
import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2WorkbenchRepository;
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2WorkbenchRepository.QueueCounts;
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2WorkbenchRepository.WorkbenchRow;

@Service
public class V2WorkbenchApplicationService {

    private static final String WORKBENCH_QUERY = "P14-PERM-048";
    private static final String DIAGNOSIS_INITIAL = "P14-PERM-034";
    private static final String DIAGNOSIS_AUDIT = "P14-PERM-035";
    private static final String REPORT_SIGN_OUT = "P14-PERM-036";
    private static final String TECHNICAL_EXECUTION = "P14-PERM-017";

    private final JdbcV2WorkbenchRepository repository;
    private final P15AuthorizationService authorization;

    public V2WorkbenchApplicationService(JdbcV2WorkbenchRepository repository,
            P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public WorkbenchResult myWorkbench() {
        ActorContext actor = authorization.require(WORKBENCH_QUERY);
        AuthenticatedUser user = AuthenticationContext.current().orElse(null);
        String actorReference = actor.actorId();
        List<WorkItem> myWork = new ArrayList<>();
        if (hasAny(user, DIAGNOSIS_INITIAL, DIAGNOSIS_AUDIT, REPORT_SIGN_OUT)) {
            myWork.addAll(repository.findPersonal(actor.hospitalScope(), actorReference).stream()
                    .map(row -> workItem(row, availableActions(row.workCode(), user))).toList());
        }
        if (hasAny(user, DIAGNOSIS_INITIAL, DIAGNOSIS_AUDIT)) {
            myWork.addAll(repository.findTechnicalAttention(actor.hospitalScope(), Instant.now()).stream()
                    .map(row -> workItem(row, availableActions(row.workCode(), user))).toList());
        }
        if (hasAny(user, REPORT_SIGN_OUT)) {
            myWork.addAll(repository.findWithdrawnReports(actor.hospitalScope()).stream()
                    .map(row -> workItem(row, availableActions(row.workCode(), user))).toList());
        }
        List<WorkbenchRow> publicRows = hasAny(user, DIAGNOSIS_INITIAL)
                ? repository.findPublicPool(actor.hospitalScope()) : List.of();
        List<WorkItem> publicPool = publicRows.stream()
                .map(row -> workItem(row, Set.of("CLAIM"))).toList();
        QueueCounts queueCounts = repository.findQueueCounts(actor.hospitalScope());
        Counts counts = new Counts(
                count(myWork, "INITIAL"), count(myWork, "REVIEW"), count(myWork, "AUDIT"),
                count(myWork, "TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION"),
                count(myWork, "WITHDRAWN_REPORT_REQUIRES_ATTENTION"), publicPool.size());
        return new WorkbenchResult(Instant.now(), myWork, publicPool, counts,
                QueueSummary.from(queueCounts, user));
    }

    private static WorkItem workItem(WorkbenchRow row, Set<String> actions) {
        return new WorkItem(row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(),
                row.businessTypeName(), row.workCode(), row.workLabel(), row.responsibilityName(),
                row.occurredAt(), row.caseCreatedAt(), actions,
                row.workCode().equals("PUBLIC_POOL") ? "/v2/diagnosis/" + row.caseId()
                        : "/v2/cases/" + row.caseId());
    }

    private static Set<String> availableActions(String workCode, AuthenticatedUser user) {
        Set<String> actions = new LinkedHashSet<>();
        if ("INITIAL".equals(workCode) && hasAny(user, DIAGNOSIS_INITIAL)) {
            actions.addAll(List.of("OPEN", "EDIT", "SUBMIT_REVIEW"));
        } else if ("REVIEW".equals(workCode) && hasAny(user, DIAGNOSIS_INITIAL)) {
            actions.addAll(List.of("OPEN", "EDIT", "SUBMIT_AUDIT"));
        } else if ("AUDIT".equals(workCode) && hasAny(user, DIAGNOSIS_AUDIT, REPORT_SIGN_OUT)) {
            actions.addAll(List.of("OPEN", "AUDIT", "SIGN"));
        } else if ("TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION".equals(workCode)
                && hasAny(user, DIAGNOSIS_INITIAL)) {
            actions.addAll(List.of("OPEN", "REVIEW_RESULT"));
        } else if ("WITHDRAWN_REPORT_REQUIRES_ATTENTION".equals(workCode)
                && hasAny(user, REPORT_SIGN_OUT)) {
            actions.addAll(List.of("OPEN", "HANDLE_WITHDRAWAL"));
        }
        return Set.copyOf(actions);
    }

    private static boolean hasAny(AuthenticatedUser user, String... permissions) {
        if (user == null) return true;
        for (String permission : permissions) if (user.permissions().contains(permission)) return true;
        return false;
    }

    private static int count(List<WorkItem> items, String workCode) {
        return (int) items.stream().filter(item -> workCode.equals(item.workCode())).count();
    }

    public record WorkbenchResult(Instant refreshedAt, List<WorkItem> myWork, List<WorkItem> publicPool,
            Counts counts, QueueSummary queues) { }

    public record Counts(int initial, int review, int audit, int technicalResultReturned,
            int withdrawnReport, int publicPool) { }

    public record WorkItem(UUID caseId, String pathologyNo, String patientReference, String businessTypeCode,
            String businessTypeName, String workCode, String workLabel, String responsibilityName,
            Instant occurredAt, Instant caseCreatedAt, Set<String> availableActions, String deepLink) { }

    public record QueueSummary(int histology, int dehydration, int embedding, int cutting, int staining,
            int coverslipping, int technical, int frozen, int withdrawn) {
        static QueueSummary from(QueueCounts counts, AuthenticatedUser user) {
            if (user == null) return new QueueSummary(counts.histology(), counts.dehydration(), counts.embedding(),
                    counts.cutting(), counts.staining(), counts.coverslipping(), counts.technical(), counts.frozen(),
                    counts.withdrawn());
            return new QueueSummary(hasAny(user, "P14-PERM-014") ? counts.histology() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.dehydration() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.embedding() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.cutting() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.staining() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.coverslipping() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.technical() : 0,
                    hasAny(user, "P14-PERM-008", DIAGNOSIS_INITIAL) ? counts.frozen() : 0,
                    hasAny(user, REPORT_SIGN_OUT) ? counts.withdrawn() : 0);
        }
    }
}
