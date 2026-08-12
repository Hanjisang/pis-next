package com.hanjisang.pis.v2.digital.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.OutboxPort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.digital.domain.DigitalSlide;
import com.hanjisang.pis.v2.digital.infrastructure.JdbcV2DigitalSlideRepository;
import com.hanjisang.pis.v2.digital.infrastructure.JdbcV2DigitalReviewRepository;
import com.hanjisang.pis.v2.digital.infrastructure.JdbcV2DigitalReviewRepository.AnnotationRow;
import com.hanjisang.pis.v2.digital.infrastructure.JdbcV2DigitalReviewRepository.MeasurementRow;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2MaterialRepository;
import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.infrastructure.JdbcV2RegistrationRepository;

@Service
public class V2DigitalSlideApplicationService {

    private static final String MATERIAL_PERMISSION = "P14-PERM-014";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2DigitalSlideRepository repository;
    private final JdbcV2RegistrationRepository registrationRepository;
    private final JdbcV2MaterialRepository materialRepository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final OutboxPort outbox;
    private final JdbcV2DigitalReviewRepository reviewRepository;

    public V2DigitalSlideApplicationService(JdbcV2DigitalSlideRepository repository,
            JdbcV2RegistrationRepository registrationRepository, JdbcV2MaterialRepository materialRepository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit, OutboxPort outbox,
            JdbcV2DigitalReviewRepository reviewRepository) {
        this.repository = repository;
        this.registrationRepository = registrationRepository;
        this.materialRepository = materialRepository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public DigitalSlideResult create(CreateDigitalSlideCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        Case pathologyCase = activeCase(command.caseId(), actor);
        require(command.viewerReference(), "阅片器引用不能为空");
        require(command.sourcePlatform(), "数字切片来源平台不能为空");
        requireMode(command.bindingModeCode());
        validateBindings(pathologyCase.id(), command.blockId(), command.slideId(), actor);
        Instant now = Instant.now();
        DigitalSlide digitalSlide = new DigitalSlide(UUID.randomUUID(), pathologyCase.id(), command.blockId(),
                command.slideId(), command.bindingModeCode(), DigitalSlide.ACTIVE, command.viewerReference(),
                command.sourcePlatform(), now, actor.actorId(), now, actor.actorId());
        repository.insert(digitalSlide);
        audit.append("PIS-V2-I06-DIGITAL-SLIDE-CREATE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                digitalSlide.id(), "V2-DIGITAL-SLIDE", UUID.randomUUID().toString(), "数字切片元数据已登记");
        outbox.append("V2-I06-DIGITAL-SLIDE-CREATED", digitalSlide.id(), "V2-DIGITAL-SLIDE", 0,
                UUID.randomUUID().toString(), digitalSlide.viewerReference(), actor.actorId());
        return result(digitalSlide);
    }

    @Transactional
    public DigitalSlideResult rebind(UUID digitalSlideId, RebindCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        DigitalSlide existing = find(digitalSlideId, actor);
        if (DigitalSlide.UNBOUND.equals(existing.statusCode())) {
            throw reject("V2-DIGITAL-SLIDE-CLOSED", "已解除绑定的数字切片不能继续改绑");
        }
        validateBindings(existing.caseId(), command.blockId(), command.slideId(), actor);
        if (!repository.updateBinding(existing.id(), command.blockId(), command.slideId(), DigitalSlide.ACTIVE,
                Instant.now(), actor.actorId(), actor.hospitalScope())) {
            throw reject("V2-DIGITAL-SLIDE-CONFLICT", "数字切片绑定已被其他请求改变");
        }
        audit.append("PIS-V2-I06-DIGITAL-SLIDE-REBIND", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                digitalSlideId, "V2-DIGITAL-SLIDE", UUID.randomUUID().toString(), "数字切片已改绑");
        return result(repository.find(digitalSlideId, actor.hospitalScope()).orElseThrow());
    }

    @Transactional
    public DigitalSlideResult unbind(UUID digitalSlideId) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        DigitalSlide existing = find(digitalSlideId, actor);
        if (!repository.updateBinding(existing.id(), null, null, DigitalSlide.UNBOUND, Instant.now(), actor.actorId(),
                actor.hospitalScope())) {
            throw reject("V2-DIGITAL-SLIDE-CONFLICT", "数字切片绑定已被其他请求改变");
        }
        audit.append("PIS-V2-I06-DIGITAL-SLIDE-UNBIND", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                digitalSlideId, "V2-DIGITAL-SLIDE", UUID.randomUUID().toString(), "数字切片已解除绑定");
        return result(repository.find(digitalSlideId, actor.hospitalScope()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<DigitalSlideResult> byCase(UUID caseId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        activeCase(caseId, actor);
        return repository.findByCase(caseId, actor.hospitalScope()).stream().map(this::result).toList();
    }

    @Transactional(readOnly = true)
    public DigitalSlideResult get(UUID digitalSlideId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        return result(find(digitalSlideId, actor));
    }

    @Transactional(readOnly = true)
    public List<AnnotationResult> annotations(UUID digitalSlideId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        ensureReviewTarget(digitalSlideId, actor);
        return reviewRepository.annotations(digitalSlideId, actor.hospitalScope()).stream().map(this::annotation).toList();
    }

    @Transactional
    public AnnotationResult annotate(UUID digitalSlideId, AnnotationCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        ensureReviewTarget(digitalSlideId, actor);
        require(command.annotationTypeCode(), "标注类型不能为空");
        require(command.geometryJson(), "标注几何信息不能为空");
        Instant now = Instant.now();
        UUID id = reviewRepository.insertAnnotation(digitalSlideId, command.annotationTypeCode(),
                command.geometryJson(), command.label(), command.note(), actor.actorId(), now, actor.hospitalScope());
        audit.append("PIS-V2-DIGITAL-SLIDE-ANNOTATION-CREATE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                id, "V2-DIGITAL-SLIDE-ANNOTATION", UUID.randomUUID().toString(), "数字切片标注已保存");
        return annotations(digitalSlideId).stream().filter(item -> item.annotationId().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public MeasurementResult measure(UUID digitalSlideId, MeasurementCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        ensureReviewTarget(digitalSlideId, actor);
        require(command.geometryJson(), "测量几何信息不能为空");
        require(command.unitCode(), "测量单位不能为空");
        require(command.measurementModeCode(), "测量模式不能为空");
        if (command.value() == null || command.value().signum() < 0) throw reject("V2-DIGITAL-MEASUREMENT-INVALID", "测量值必须为非负数");
        Instant now = Instant.now();
        UUID id = reviewRepository.insertMeasurement(digitalSlideId, command.geometryJson(), command.value(),
                command.unitCode(), command.measurementModeCode(), actor.actorId(), now, actor.hospitalScope());
        audit.append("PIS-V2-DIGITAL-SLIDE-MEASUREMENT", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-DIGITAL-SLIDE-MEASUREMENT", UUID.randomUUID().toString(), "数字切片测量已保存");
        return new MeasurementResult(id, digitalSlideId, command.geometryJson(), command.value(), command.unitCode(),
                command.measurementModeCode(), now, actor.actorId());
    }

    @Transactional(readOnly = true)
    public List<MeasurementResult> measurements(UUID digitalSlideId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        ensureReviewTarget(digitalSlideId, actor);
        return reviewRepository.measurements(digitalSlideId, actor.hospitalScope()).stream().map(this::measurement).toList();
    }

    @Transactional
    public ScreenshotResult screenshot(UUID digitalSlideId, ScreenshotCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        ensureReviewTarget(digitalSlideId, actor);
        require(command.viewportJson(), "截图视野不能为空");
        require(command.storageReference(), "截图保存位置不能为空");
        Instant now = Instant.now();
        UUID id = reviewRepository.insertScreenshot(digitalSlideId, command.viewportJson(), command.storageReference(),
                actor.actorId(), now, actor.hospitalScope());
        audit.append("PIS-V2-DIGITAL-SLIDE-SCREENSHOT", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-DIGITAL-SLIDE-SCREENSHOT", UUID.randomUUID().toString(), "数字切片截图已保存");
        return new ScreenshotResult(id, digitalSlideId, command.viewportJson(), command.storageReference(), now,
                actor.actorId());
    }

    private void ensureReviewTarget(UUID digitalSlideId, ActorContext actor) {
        if (!reviewRepository.belongs(digitalSlideId, actor.hospitalScope())) {
            throw reject("V2-DIGITAL-SLIDE-NOT-FOUND", "数字切片不存在或不在当前数据范围");
        }
    }

    private AnnotationResult annotation(AnnotationRow row) {
        return new AnnotationResult(row.annotationId(), row.digitalSlideId(), row.annotationTypeCode(), row.geometryJson(),
                row.label(), row.note(), row.createdAt(), row.createdByRef(), row.updatedAt());
    }

    private MeasurementResult measurement(MeasurementRow row) {
        return new MeasurementResult(row.measurementId(), row.digitalSlideId(), row.geometryJson(), row.value(),
                row.unitCode(), row.measurementModeCode(), row.createdAt(), row.createdByRef());
    }

    private DigitalSlide find(UUID id, ActorContext actor) {
        return repository.find(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-DIGITAL-SLIDE-NOT-FOUND", "数字切片不存在"));
    }

    private Case activeCase(UUID id, ActorContext actor) {
        if (id == null) throw reject("V2-INVALID-REQUEST", "病例不能为空");
        Case pathologyCase = registrationRepository.findCase(id, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-CASE-NOT-FOUND", "病例不存在"));
        if (!Case.ACTIVE.equals(pathologyCase.lifecycleStateCode())) {
            throw reject("V2-CASE-CANCELLED", "已取消病例不能登记数字切片");
        }
        return pathologyCase;
    }

    private void validateBindings(UUID caseId, UUID blockId, UUID slideId, ActorContext actor) {
        if (blockId != null && materialRepository.findBlock(blockId, actor.hospitalScope())
                .filter(block -> block.caseId().equals(caseId) && !block.isDeleted()).isEmpty()) {
            throw reject("V2-DIGITAL-SLIDE-BINDING-INVALID", "蜡块不属于当前病例");
        }
        if (slideId != null && materialRepository.findSlide(slideId, actor.hospitalScope())
                .filter(slide -> slide.caseId().equals(caseId) && !slide.isDeleted()).isEmpty()) {
            throw reject("V2-DIGITAL-SLIDE-BINDING-INVALID", "切片不属于当前病例");
        }
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) throw reject("V2-INVALID-REQUEST", message);
    }

    private static void requireMode(String mode) {
        if (!"AUTOMATIC".equals(mode) && !"MANUAL".equals(mode)) {
            throw reject("V2-INVALID-REQUEST", "数字切片绑定方式不受支持");
        }
    }

    private DigitalSlideResult result(DigitalSlide item) {
        return new DigitalSlideResult(item.id(), item.caseId(), item.blockId(), item.slideId(), item.bindingModeCode(),
                item.statusCode(), item.viewerReference(), item.sourcePlatform(), item.updatedAt());
    }

    private static P15BusinessException reject(String code, String message) { return new P15BusinessException(code, message); }

    public record CreateDigitalSlideCommand(UUID caseId, UUID blockId, UUID slideId, String bindingModeCode,
            String viewerReference, String sourcePlatform) { }
    public record RebindCommand(UUID blockId, UUID slideId) { }
    public record AnnotationCommand(String annotationTypeCode, String geometryJson, String label, String note) { }
    public record MeasurementCommand(String geometryJson, BigDecimal value, String unitCode, String measurementModeCode) { }
    public record ScreenshotCommand(String viewportJson, String storageReference) { }
    public record DigitalSlideResult(UUID digitalSlideId, UUID caseId, UUID blockId, UUID slideId,
            String bindingModeCode, String statusCode, String viewerReference, String sourcePlatform,
            Instant updatedAt) { }
    public record AnnotationResult(UUID annotationId, UUID digitalSlideId, String annotationTypeCode,
            String geometryJson, String label, String note, Instant createdAt, String createdByRef, Instant updatedAt) { }
    public record MeasurementResult(UUID measurementId, UUID digitalSlideId, String geometryJson, BigDecimal value,
            String unitCode, String measurementModeCode, Instant createdAt, String createdByRef) { }
    public record ScreenshotResult(UUID screenshotId, UUID digitalSlideId, String viewportJson,
            String storageReference, Instant createdAt, String createdByRef) { }
}
