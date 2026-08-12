package com.hanjisang.pis.v2.workbench.application;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
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
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2WorkbenchRepository.GrossingRow;
import com.hanjisang.pis.v2.workbench.infrastructure.JdbcV2WorkbenchRepository.RegisteredRow;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService;
import com.hanjisang.pis.v2.registration.application.V2ApplicationApplicationService.ApplicationQueueResult;
import com.hanjisang.pis.v2.production.application.V2ProductionWorkbenchApplicationService;
import com.hanjisang.pis.v2.production.application.V2ProductionWorkbenchApplicationService.ProductionItem;

@Service
public class V2WorkbenchApplicationService {

    private static final String WORKBENCH_QUERY = "P14-PERM-048";
    private static final String DIAGNOSIS_INITIAL = "P14-PERM-034";
    private static final String DIAGNOSIS_AUDIT = "P14-PERM-035";
    private static final String REPORT_SIGN_OUT = "P14-PERM-036";
    private static final String TECHNICAL_EXECUTION = "P14-PERM-017";
    private static final String REGISTRATION = "P14-PERM-004";
    private static final String GROSSING = "P14-PERM-013";

    private final JdbcV2WorkbenchRepository repository;
    private final P15AuthorizationService authorization;
    private final CaseProgressProjectionApplicationService progressProjection;
    private final V2ApplicationApplicationService applications;
    private final V2ProductionWorkbenchApplicationService production;

    public V2WorkbenchApplicationService(JdbcV2WorkbenchRepository repository,
            P15AuthorizationService authorization, CaseProgressProjectionApplicationService progressProjection,
            V2ApplicationApplicationService applications, V2ProductionWorkbenchApplicationService production) {
        this.repository = repository;
        this.authorization = authorization;
        this.progressProjection = progressProjection;
        this.applications = applications;
        this.production = production;
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
            myWork.addAll(repository.findTechnicalAttention(actor.hospitalScope(), actorReference).stream()
                    .map(row -> workItem(row, availableActions(row.workCode(), user))).toList());
        }
        if (hasAny(user, REPORT_SIGN_OUT)) {
            myWork.addAll(repository.findWithdrawnReports(actor.hospitalScope(), actorReference).stream()
                    .map(row -> workItem(row, availableActions(row.workCode(), user))).toList());
        }
        List<WorkbenchRow> publicRows = hasAny(user, DIAGNOSIS_INITIAL)
                ? repository.findPublicPool(actor.hospitalScope()) : List.of();
        List<WorkItem> publicPool = publicRows.stream()
                .map(row -> workItem(row, Set.of("CLAIM"))).toList();
        QueueCounts queueCounts = repository.findQueueCounts(actor.hospitalScope());
        List<WorkItem> cytologyPreparationCases = hasTechnicalProductionAccess(user)
                ? repository.findCytologyPreparation(actor.hospitalScope()).stream()
                        .map(row -> workItem(row, Set.of("OPEN"))).toList()
                : List.of();
        Counts counts = new Counts(
                count(myWork, "INITIAL"), count(myWork, "REVIEW"), count(myWork, "AUDIT"),
                count(myWork, "TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION"),
                count(myWork, "WITHDRAWN_REPORT_REQUIRES_ATTENTION"), publicPool.size());
        List<CapabilityQueue> capabilityQueues = capabilityQueues(actor, user, myWork, publicPool);
        return new WorkbenchResult(Instant.now(), myWork, publicPool, counts,
                QueueSummary.from(queueCounts, user, cytologyPreparationCases),
                new TrackingSummary(List.of()), capabilityQueues);
    }

    private List<CapabilityQueue> capabilityQueues(ActorContext actor, AuthenticatedUser user,
            List<WorkItem> myWork, List<WorkItem> publicPool) {
        List<CapabilityQueue> result = new ArrayList<>();
        if (hasAny(user, REGISTRATION)) {
            List<QueueItem> pending = applications.queue().stream()
                    .filter(item -> "PENDING".equals(item.itemStatusCode()))
                    .map(this::registrationItem).toList();
            result.add(queue("REGISTRATION_PENDING", "待登记", "PENDING", pending));
            result.add(queue("REGISTERED_TODAY", "我今天登记", "TRACKING",
                    repository.findRegisteredToday(actor.hospitalScope(), actor.actorId()).stream()
                            .map(this::registeredItem).toList()));
        }
        if (hasAny(user, GROSSING)) {
            result.add(queue("GROSSING_PENDING", "待取材", "PENDING",
                    repository.findPendingGrossing(actor.hospitalScope(), false).stream()
                            .map(row -> grossingItem(row, false, false)).toList()));
            result.add(queue("FROZEN_GROSSING", "冰冻待取材", "PENDING",
                    repository.findPendingGrossing(actor.hospitalScope(), true).stream()
                            .map(row -> grossingItem(row, true, false)).toList()));
            result.add(queue("GROSSED_TODAY", "我今天取材", "TRACKING",
                    repository.findGrossedToday(actor.hospitalScope(), actor.actorId()).stream()
                            .map(row -> grossingItem(row, false, true)).toList()));
        }
        if (hasTechnicalProductionAccess(user)) {
            var queues = production.workbench().queues();
            result.add(productionQueue(queues.routineProduction()));
            result.add(productionQueue(queues.cytologyProduction()));
            result.add(productionQueue(queues.incompleteSlides()));
            if (hasAny(user, "P14-PERM-008")) result.add(productionQueue(queues.frozenProduction()));
            result.add(productionQueue(queues.technicalOrders()));
            result.add(productionQueue(queues.exceptions()));
        }
        if (hasAny(user, DIAGNOSIS_INITIAL)) {
            result.add(diagnosisQueue("PUBLIC_POOL", "待接诊", publicPool));
            result.add(diagnosisQueue("INITIAL", "待初诊", myWork));
            result.add(diagnosisQueue("REVIEW", "待复诊", myWork));
            result.add(diagnosisQueue("TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION", "新技术结果", myWork));
        }
        if (hasAny(user, DIAGNOSIS_AUDIT)) result.add(diagnosisQueue("AUDIT", "待审核", myWork));
        if (hasAny(user, REPORT_SIGN_OUT)) result.add(diagnosisQueue(
                "WITHDRAWN_REPORT_REQUIRES_ATTENTION", "撤回待处理", myWork));
        return List.copyOf(result);
    }

    private CapabilityQueue diagnosisQueue(String code, String label, List<WorkItem> source) {
        return queue(code, label, "PENDING", source.stream().filter(item -> code.equals(item.workCode()))
                .map(item -> new QueueItem(code + "-" + item.caseId(), item.caseId(), null, null,
                        item.pathologyNo(), item.patientReference(), null, null, item.businessTypeName(),
                        item.workLabel(), item.responsibilityName(), item.enteredAt(), item.waitingMinutes(), false,
                        item.availableActions(), item.deepLink())).toList());
    }

    private QueueItem registrationItem(ApplicationQueueResult item) {
        String age = item.patientBirthDate() == null ? null
                : Period.between(item.patientBirthDate(), LocalDate.now()).getYears() + "岁";
        String patient = item.patientName() == null || item.patientName().isBlank()
                ? item.patientReference() : item.patientName();
        String summary = String.join(" · ", java.util.stream.Stream.of(item.patientSexCode(), age,
                item.visitReference(), item.applicationDepartment(), item.applicantReference())
                .filter(value -> value != null && !value.isBlank()).toList());
        String detail = String.join(" · ", java.util.stream.Stream.of(item.itemName(), item.externalItemCode(),
                item.specimenDescription(), item.specimenKindCode()).filter(value -> value != null && !value.isBlank())
                .toList());
        long waiting = waiting(item.appliedAt());
        String link = "/v2/registration?applicationId=" + item.applicationId() + "&applicationItemId="
                + item.applicationItemId();
        return new QueueItem("REGISTRATION-" + item.applicationItemId(), null, item.applicationId(),
                item.applicationItemId(), item.applicationNo(), patient, summary, item.visitReference(),
                item.businessTypeCode(), item.externalItemCode(), detail, item.appliedAt(), waiting, false,
                Set.of("OPEN", "REGISTER"), link);
    }

    private QueueItem registeredItem(RegisteredRow item) {
        return new QueueItem("REGISTERED-" + item.caseId(), item.caseId(), null, null, item.pathologyNo(),
                item.patientReference(), null, null, item.businessTypeName(), "登记完成",
                "本人今日登记", item.registeredAt(), waiting(item.registeredAt()), false, Set.of("OPEN_CASE"),
                "/v2/cases/" + item.caseId());
    }

    private QueueItem grossingItem(GrossingRow item, boolean frozen, boolean tracking) {
        Instant entered = item.roundStartedAt() == null ? item.enteredAt() : item.roundStartedAt();
        String task = frozen ? "第 " + item.roundNo() + " 轮取材" : tracking ? "取材已完成" : "取材";
        String detail = item.specimenCount() + " 个标本 · " + item.specimenSummary()
                + (item.sourceDepartment() == null ? "" : " · " + item.sourceDepartment());
        String link = tracking ? "/v2/cases/" + item.caseId() : "/v2/grossing/" + item.caseId()
                + (frozen ? "?roundId=" + item.roundId() : "");
        return new QueueItem((tracking ? "GROSSED-" : "GROSSING-") + item.caseId()
                + (item.roundId() == null ? "" : "-" + item.roundId()), item.caseId(), null, null,
                item.pathologyNo(), item.patientReference(), null, null, item.businessTypeName(), task, detail,
                entered, waiting(entered), false, tracking ? Set.of("OPEN_CASE") : Set.of("OPEN", "GROSS"), link);
    }

    private CapabilityQueue productionQueue(V2ProductionWorkbenchApplicationService.QueueView source) {
        return queue(source.code(), source.label(), "PENDING", source.items().stream().map(this::productionItem).toList());
    }

    private QueueItem productionItem(ProductionItem item) {
        return new QueueItem(item.productionContext() + "-" + item.caseId() + "-"
                + (item.orderId() == null ? item.slideCode() == null ? "CASE" : item.slideCode() : item.orderId()),
                item.caseId(), null, null, item.pathologyNo(), item.patientReference(), null, null,
                item.businessTypeName(), item.taskSummary(), item.materialSummary(), item.enteredAt(),
                item.waitingMinutes(), false, item.availableActions(), item.deepLink());
    }

    private static CapabilityQueue queue(String key, String label, String kind, List<QueueItem> items) {
        return new CapabilityQueue(key, label, kind, items.size(), items);
    }

    private static long waiting(Instant enteredAt) {
        return Math.max(0, Duration.between(enteredAt == null ? Instant.now() : enteredAt, Instant.now()).toMinutes());
    }

    private static WorkItem workItem(WorkbenchRow row, Set<String> actions) {
        Instant enteredAt = row.occurredAt() == null ? row.caseCreatedAt() : row.occurredAt();
        return new WorkItem(row.caseId(), row.pathologyNo(), row.patientReference(), row.businessTypeCode(),
                row.businessTypeName(), row.workCode(), row.workLabel(), row.responsibilityName(),
                row.occurredAt(), row.caseCreatedAt(), actions,
                workspaceLink(row), enteredAt,
                Math.max(0, Duration.between(enteredAt, Instant.now()).toMinutes()));
    }

    private static String workspaceLink(WorkbenchRow row) {
        String route = "WITHDRAWN_REPORT_REQUIRES_ATTENTION".equals(row.workCode()) ? "reports" : "diagnosis";
        return "/v2/" + route + "/" + row.caseId();
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
            Counts counts, QueueSummary queues, TrackingSummary tracking, List<CapabilityQueue> capabilityQueues) { }

    public record CapabilityQueue(String key, String label, String kind, int count, List<QueueItem> items) { }

    public record QueueItem(String key, UUID caseId, UUID applicationId, UUID applicationItemId,
            String businessDisplayId, String patientDisplay, String patientSummary, String visitReference,
            String businessType, String task, String detail, Instant enteredAt, long waitingMinutes, boolean urgent,
            Set<String> availableActions, String workspaceDestination) { }

    public record TrackingSummary(List<CaseProgressProjectionApplicationService.CaseProgress> registeredCases) { }

    public record Counts(int initial, int review, int audit, int technicalResultReturned,
            int withdrawnReport, int publicPool) { }

    public record WorkItem(UUID caseId, String pathologyNo, String patientReference, String businessTypeCode,
            String businessTypeName, String workCode, String workLabel, String responsibilityName,
            Instant occurredAt, Instant caseCreatedAt, Set<String> availableActions, String deepLink,
            Instant enteredAt, long waitingMinutes) { }

    public record QueueSummary(int histology, int dehydration, int embedding, int cutting, int staining,
            int coverslipping, int technical, int frozen, int withdrawn, int cytologyPreparation,
            List<WorkItem> cytologyPreparationCases) {
        static QueueSummary from(QueueCounts counts, AuthenticatedUser user, List<WorkItem> cytologyCases) {
            if (user == null) return new QueueSummary(counts.histology(), counts.dehydration(), counts.embedding(),
                    counts.cutting(), counts.staining(), counts.coverslipping(), counts.technical(), counts.frozen(),
                    counts.withdrawn(), counts.cytologyPreparation(), cytologyCases);
            return new QueueSummary(hasTechnicalProductionAccess(user) ? counts.histology() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.dehydration() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.embedding() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.cutting() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.staining() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.coverslipping() : 0,
                    hasAny(user, TECHNICAL_EXECUTION) ? counts.technical() : 0,
                    hasTechnicalProductionAccess(user) && hasAny(user, "P14-PERM-008") ? counts.frozen() : 0,
                    hasAny(user, REPORT_SIGN_OUT) ? counts.withdrawn() : 0,
                    hasTechnicalProductionAccess(user) ? counts.cytologyPreparation() : 0,
                    hasTechnicalProductionAccess(user) ? cytologyCases : List.of());
        }
    }

    private static boolean hasTechnicalProductionAccess(AuthenticatedUser user) {
        return hasAny(user, TECHNICAL_EXECUTION) && hasAny(user, "P14-PERM-014");
    }
}
