package com.hanjisang.pis.v2.molecular.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.molecular.domain.MolecularResult;
import com.hanjisang.pis.v2.molecular.infrastructure.JdbcV2MolecularResultRepository;
import com.hanjisang.pis.v2.molecular.infrastructure.JdbcV2MolecularWorkflowRepository;
import com.hanjisang.pis.v2.molecular.infrastructure.JdbcV2MolecularWorkflowRepository.TestRow;
import com.hanjisang.pis.v2.molecular.api.MolecularInstrumentPort;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2MolecularApplicationService {

    private static final String MOLECULAR_PERMISSION = "P14-PERM-014";

    private final JdbcV2MolecularResultRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MolecularWorkflowRepository workflowRepository;
    private final List<MolecularInstrumentPort> instrumentPorts;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;

    public V2MolecularApplicationService(JdbcV2MolecularResultRepository repository,
            JdbcV2RegistrationRepository registrationRepository, JdbcV2MolecularWorkflowRepository workflowRepository,
            List<MolecularInstrumentPort> instrumentPorts, P15AuthorizationService authorization,
            JdbcAuditEventRepository audit, OutboxPort outbox) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.workflowRepository = workflowRepository;
        this.instrumentPorts = instrumentPorts;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public MolecularResultResult completeResult(UUID caseId, CompleteResultCommand command) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        require(caseId, "病例不能为空");
        require(command.resultCode(), "分子结果编码不能为空");
        require(command.resultData(), "分子结果数据不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        Case pathologyCase = registrationRepository.findCase(caseId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围"));
        if (!"MOLECULAR".equals(pathologyCase.businessTypeCode())) {
            throw reject("V2-MOLECULAR-CASE-REQUIRED", "独立分子结果只能进入分子病例");
        }
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能录入分子结果");
        }
        if (command.specimenId() != null) {
            registrationRepository.findSpecimen(command.specimenId(), actor.hospitalScope())
                    .filter(specimen -> specimen.caseId().equals(caseId) && !specimen.deleted())
                    .orElseThrow(() -> reject("V2-SOURCE-NOT-FOUND", "分子结果关联标本不属于当前病例"));
        }
        String operation = "PIS-V2-I06-MOLECULAR-RESULT";
        String digest = digest(caseId, command.specimenId(), command.resultCode(), command.resultData());
        var replay = repository.findIdempotency(operation, command.idempotencyKey()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) {
                throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的分子结果摘要冲突");
            }
            return result(repository.find(replay.resultId(), actor.hospitalScope()).orElseThrow(), true);
        }
        UUID resultId = UUID.randomUUID();
        Instant now = Instant.now();
        MolecularResult result = MolecularResult.completed(resultId, caseId, command.specimenId(),
                command.resultCode(), command.resultData(), now, actor.actorId());
        repository.insert(result, actor.hospitalScope(), actor.actorId(), now);
        if (!repository.insertIdempotency(operation, command.idempotencyKey(), digest, resultId, actor.actorId(), now)) {
            throw reject("V2-IDEMPOTENCY-RETRY", "分子结果命令正在由其他请求处理，请重试");
        }
        audit.append(operation, MOLECULAR_PERMISSION, actor, "ALLOWED", "COMPLETED", resultId,
                "V2-MOLECULAR-RESULT", UUID.randomUUID().toString(), "独立分子结果已记录");
        outbox.append("V2-I06-MOLECULAR-RESULT-COMPLETED", resultId, "V2-MOLECULAR-RESULT", 0,
                UUID.randomUUID().toString(), digest, actor.actorId());
        return result(result, false);
    }

    @Transactional(readOnly = true)
    public MolecularResultResult getResult(UUID resultId) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        return result(repository.find(resultId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-MOLECULAR-RESULT-NOT-FOUND", "分子结果不存在")), false);
    }

    @Transactional(readOnly = true)
    public MolecularWorkbenchResult workbench() {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        return new MolecularWorkbenchResult(Instant.now(), workflowRepository.projects(actor.hospitalScope()),
                workflowRepository.instruments(actor.hospitalScope()), workflowRepository.reagents(actor.hospitalScope()),
                workflowRepository.tests(actor.hospitalScope()), workflowRepository.attachments(actor.hospitalScope()),
                workflowRepository.attempts(actor.hospitalScope()));
    }

    @Transactional
    public TestCommandResult createTest(CreateTestCommand command) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        require(command.caseId(), "病例不能为空");
        require(command.projectId(), "检测项目不能为空");
        require(command.specimenId(), "检测标本不能为空");
        require(command.instrumentId(), "检测设备不能为空");
        require(command.reagentKitId(), "试剂批次不能为空");
        require(command.rawDataReference(), "原始数据引用不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        String detectionNo = command.detectionNo() == null || command.detectionNo().isBlank()
                ? "MOL-" + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneOffset.UTC)) + "-"
                        + UUID.nameUUIDFromBytes((actor.hospitalScope() + "|" + command.idempotencyKey())
                                .getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8).toUpperCase()
                : command.detectionNo().trim();
        String digest = digest(command.caseId(), command.specimenId(), command.projectId(), detectionNo,
                command.instrumentId(), command.reagentKitId(), command.rawDataReference());
        if (!workflowRepository.lockCase(command.caseId(), actor.hospitalScope()))
            throw reject("V2-CASE-NOT-FOUND", "病例不存在或不在当前数据范围");
        var replay = workflowRepository.commandReplay("MOLECULAR_TEST_CREATE", command.idempotencyKey(), actor.hospitalScope()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的分子申请摘要冲突");
            return new TestCommandResult(replay.resultEntityId(), true);
        }
        Case pathologyCase = registrationRepository.findCase(command.caseId(), actor.hospitalScope()).orElseThrow();
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) throw reject("V2-CASE-CANCELLED", "已取消病例不能创建分子申请");
        registrationRepository.findSpecimen(command.specimenId(), actor.hospitalScope())
                .filter(item -> item.caseId().equals(command.caseId()) && !item.deleted())
                .orElseThrow(() -> reject("V2-MOLECULAR-SPECIMEN", "检测标本不属于当前病例"));
        var project = workflowRepository.project(command.projectId(), actor.hospitalScope())
                .filter(item -> item.enabled()).orElseThrow(() -> reject("V2-MOLECULAR-PROJECT", "检测项目不存在或已停用"));
        workflowRepository.instrument(command.instrumentId(), actor.hospitalScope()).filter(item -> item.enabled())
                .orElseThrow(() -> reject("V2-MOLECULAR-INSTRUMENT", "检测设备不存在或已停用"));
        var reagent = workflowRepository.reagent(command.reagentKitId(), actor.hospitalScope()).filter(item -> item.enabled())
                .orElseThrow(() -> reject("V2-MOLECULAR-REAGENT", "试剂批次不存在或已停用"));
        if (reagent.expiryDate() != null && reagent.expiryDate().isBefore(LocalDate.now()))
            throw reject("V2-MOLECULAR-REAGENT-EXPIRED", "过期试剂不能绑定分子检测");
        Instant now = Instant.now();
        UUID id = workflowRepository.insertTest(command.caseId(), command.specimenId(), project.id(), detectionNo,
                command.instrumentId(), command.reagentKitId(), command.rawDataReference(), actor.hospitalScope(), actor.actorId(), now);
        workflowRepository.insertCommand("MOLECULAR_TEST_CREATE", command.idempotencyKey(), digest, id,
                actor.hospitalScope(), actor.actorId(), now);
        appendWorkflowAudit("PIS-V2-MOLECULAR-TEST-CREATE", actor, id, "分子申请已登记并绑定标本、设备和试剂");
        return new TestCommandResult(id, false);
    }

    @Transactional(noRollbackFor = P15BusinessException.class)
    public StartTestResult startTest(UUID id, StartTestCommand command) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        require(command.idempotencyKey(), "幂等键不能为空");
        String digest = digest(id);
        var replay = workflowRepository.commandReplay("MOLECULAR_TEST_START", command.idempotencyKey(), actor.hospitalScope()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的启动命令冲突");
            TestRow row = workflowRepository.lockTest(id, actor.hospitalScope()).orElseThrow();
            return new StartTestResult(row.id(), row.statusCode(), true, null);
        }
        TestRow test = workflowRepository.lockTest(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-MOLECULAR-TEST-NOT-FOUND", "分子检测不存在"));
        replay = workflowRepository.commandReplay("MOLECULAR_TEST_START", command.idempotencyKey(), actor.hospitalScope()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的启动命令冲突");
            return new StartTestResult(test.id(), test.statusCode(), true, null);
        }
        if (!"REQUESTED".equals(test.statusCode())) throw reject("V2-MOLECULAR-TEST-STATE", "只有待执行检测可以启动");
        if (test.instrumentId() == null) throw reject("V2-MOLECULAR-INSTRUMENT", "启动前必须绑定设备");
        String requestReference = UUID.randomUUID().toString();
        MolecularInstrumentPort port = instrumentPorts.stream().filter(item -> item.supports(test.adapterCode())).findFirst().orElse(null);
        var response = port == null
                ? new MolecularInstrumentPort.StartResponse(false, null, "ADAPTER_NOT_CONNECTED", "真实设备适配器尚未连接")
                : port.start(new MolecularInstrumentPort.StartRequest(test.id(), test.detectionNo(), test.projectCode(), test.rawDataReference(), requestReference));
        Instant now = Instant.now();
        workflowRepository.insertAttempt(test, workflowRepository.nextAttempt(test.id()), requestReference,
                response.accepted(), response.responseReference(), response.errorCode(), response.errorMessage(),
                actor.hospitalScope(), actor.actorId(), now);
        if (!response.accepted()) {
            appendWorkflowAudit("PIS-V2-MOLECULAR-INSTRUMENT-FAILED", actor, id, response.errorCode());
            throw reject("V2-MOLECULAR-INSTRUMENT-UNAVAILABLE", response.errorMessage());
        }
        if (!workflowRepository.start(id, test.concurrencyVersion(), actor.actorId(), now, actor.hospitalScope()))
            throw reject("V2-MOLECULAR-TEST-CONFLICT", "分子检测已被其他用户更新");
        workflowRepository.insertCommand("MOLECULAR_TEST_START", command.idempotencyKey(), digest, id,
                actor.hospitalScope(), actor.actorId(), now);
        appendWorkflowAudit("PIS-V2-MOLECULAR-TEST-START", actor, id, "设备已接收分子检测");
        return new StartTestResult(id, "RUNNING", false, response.responseReference());
    }

    @Transactional
    public CompleteTestResult completeTest(UUID id, CompleteTestCommand command) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        require(command.structuredResult(), "结构化结果不能为空");
        require(command.analysisResult(), "分析结果不能为空");
        require(command.idempotencyKey(), "幂等键不能为空");
        String digest = digest(id, command.structuredResult(), command.analysisResult());
        var replay = workflowRepository.commandReplay("MOLECULAR_TEST_COMPLETE", command.idempotencyKey(), actor.hospitalScope()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的完成命令冲突");
            TestRow row = workflowRepository.lockTest(id, actor.hospitalScope()).orElseThrow();
            return new CompleteTestResult(id, row.resultId(), true);
        }
        TestRow test = workflowRepository.lockTest(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-MOLECULAR-TEST-NOT-FOUND", "分子检测不存在"));
        replay = workflowRepository.commandReplay("MOLECULAR_TEST_COMPLETE", command.idempotencyKey(), actor.hospitalScope()).orElse(null);
        if (replay != null) {
            if (!replay.payloadDigest().equals(digest)) throw reject("V2-IDEMPOTENCY-CONFLICT", "相同幂等键对应的完成命令冲突");
            return new CompleteTestResult(id, test.resultId(), true);
        }
        if (!"RUNNING".equals(test.statusCode())) throw reject("V2-MOLECULAR-TEST-STATE", "只有执行中的检测可以完成");
        Instant now = Instant.now();
        UUID resultId = UUID.randomUUID();
        String resultData = jsonResult(test, command);
        MolecularResult result = MolecularResult.completed(resultId, test.caseId(), test.specimenId(), test.detectionNo(), resultData, now, actor.actorId());
        repository.insert(result, actor.hospitalScope(), actor.actorId(), now);
        if (!workflowRepository.complete(id, test.concurrencyVersion(), command.structuredResult(), command.analysisResult(),
                resultId, actor.actorId(), now, actor.hospitalScope())) throw reject("V2-MOLECULAR-TEST-CONFLICT", "分子检测已被其他用户更新");
        workflowRepository.insertCommand("MOLECULAR_TEST_COMPLETE", command.idempotencyKey(), digest, id,
                actor.hospitalScope(), actor.actorId(), now);
        appendWorkflowAudit("PIS-V2-MOLECULAR-TEST-COMPLETE", actor, id, "分子检测完成并生成不可变结果");
        outbox.append("V2-I06-MOLECULAR-RESULT-COMPLETED", resultId, "V2-MOLECULAR-RESULT", 0,
                UUID.randomUUID().toString(), digest, actor.actorId());
        return new CompleteTestResult(id, resultId, false);
    }

    @Transactional
    public TestCommandResult addAttachment(UUID id, AttachmentCommand command) {
        ActorContext actor = authorization.require(MOLECULAR_PERMISSION);
        boolean hasSlide = command.digitalSlideId() != null;
        boolean hasReference = command.attachmentReference() != null && !command.attachmentReference().isBlank();
        if (hasSlide == hasReference) throw reject("V2-MOLECULAR-ATTACHMENT-SOURCE", "数字切片和附件引用必须且只能提供一种");
        TestRow test = workflowRepository.lockTest(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-MOLECULAR-TEST-NOT-FOUND", "分子检测不存在"));
        if (hasSlide && !workflowRepository.digitalSlideBelongsToCase(command.digitalSlideId(), test.caseId(), actor.hospitalScope()))
            throw reject("V2-MOLECULAR-DIGITAL-SLIDE", "数字切片不属于当前检测病例");
        UUID attachmentId = workflowRepository.insertAttachment(id, command.digitalSlideId(), command.attachmentReference(),
                command.description(), actor.hospitalScope(), actor.actorId(), Instant.now());
        appendWorkflowAudit("PIS-V2-MOLECULAR-ATTACHMENT", actor, attachmentId, "分子检测支持材料已关联");
        return new TestCommandResult(attachmentId, false);
    }

    private static String jsonResult(TestRow test, CompleteTestCommand command) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                    "detectionNo", test.detectionNo(), "projectCode", test.projectCode(),
                    "rawDataReference", test.rawDataReference(), "structuredResult", command.structuredResult(),
                    "analysisResult", command.analysisResult()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void appendWorkflowAudit(String operation, ActorContext actor, UUID id, String detail) {
        audit.append(operation, MOLECULAR_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-MOLECULAR-TEST", UUID.randomUUID().toString(), detail);
    }

    private MolecularResultResult result(MolecularResult result, boolean duplicate) {
        return new MolecularResultResult(result.id(), result.caseId(), result.specimenId(), result.resultCode(),
                result.resultData(), result.statusCode(), result.completedAt(), result.completedBy(), duplicate);
    }

    private static void require(Object value, String message) {
        if (value == null || value.toString().isBlank()) {
            throw reject("V2-INVALID-REQUEST", message);
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    private static String digest(Object... values) {
        try {
            String payload = java.util.Arrays.stream(values).map(value -> value == null ? "<null>" : value.toString())
                    .reduce((left, right) -> left + "|" + right).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record CompleteResultCommand(UUID specimenId, String resultCode, String resultData,
            String idempotencyKey) { }

    public record MolecularResultResult(UUID resultId, UUID caseId, UUID specimenId, String resultCode,
            String resultData, String statusCode, Instant completedAt, String completedBy, boolean duplicate) { }
    public record CreateTestCommand(UUID caseId, UUID specimenId, UUID projectId, String detectionNo,
            UUID instrumentId, UUID reagentKitId, String rawDataReference, String idempotencyKey) { }
    public record StartTestCommand(String idempotencyKey) { }
    public record CompleteTestCommand(String structuredResult, String analysisResult, String idempotencyKey) { }
    public record AttachmentCommand(UUID digitalSlideId, String attachmentReference, String description) { }
    public record TestCommandResult(UUID id, boolean duplicate) { }
    public record StartTestResult(UUID id, String statusCode, boolean duplicate, String deviceRunReference) { }
    public record CompleteTestResult(UUID id, UUID resultId, boolean duplicate) { }
    public record MolecularWorkbenchResult(Instant refreshedAt,
            List<JdbcV2MolecularWorkflowRepository.ProjectRow> projects,
            List<JdbcV2MolecularWorkflowRepository.InstrumentRow> instruments,
            List<JdbcV2MolecularWorkflowRepository.ReagentRow> reagents,
            List<JdbcV2MolecularWorkflowRepository.TestRow> tests,
            List<JdbcV2MolecularWorkflowRepository.AttachmentRow> attachments,
            List<JdbcV2MolecularWorkflowRepository.AttemptRow> attempts) { }
}
