package com.hanjisang.pis.v2.material.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.integration.device.GrossImagingDevicePort;
import com.hanjisang.pis.security.ActorContext;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2GrossingImageRepository;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2GrossingImageRepository.AnnotationRow;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2GrossingImageRepository.GrossingContext;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2GrossingImageRepository.ImageRow;
import com.hanjisang.pis.v2.material.infrastructure.JdbcV2GrossingImageRepository.MeasurementRow;

@Service
public class V2GrossingImageApplicationService {

    private static final String MATERIAL_PERMISSION = "P14-PERM-013";
    private static final String QUERY_PERMISSION = "P14-PERM-048";
    private final JdbcV2GrossingImageRepository repository;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;
    private final GrossImagingDevicePort imagingDevice;

    public V2GrossingImageApplicationService(JdbcV2GrossingImageRepository repository,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit,
            GrossImagingDevicePort imagingDevice) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.imagingDevice = imagingDevice;
    }

    @Transactional(readOnly = true)
    public List<ImageResult> images(UUID grossingId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        requireContext(grossingId, actor);
        return repository.images(grossingId, actor.hospitalScope()).stream().map(this::image).toList();
    }

    @Transactional
    public ImageResult upload(UploadImageCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        GrossingContext context = requireContext(command.grossingId(), actor);
        if (command.specimenId() != null
                && !repository.specimenBelongs(command.specimenId(), context.caseId(), context.grossingId())) {
            throw new P15BusinessException("V2-GROSSING-SPECIMEN-NOT-FOUND",
                    "标本未关联当前取材，不能绑定图像", 409);
        }
        require(command.imageName(), "imageName");
        require(command.mediaType(), "mediaType");
        require(command.storageReference(), "storageReference");
        Instant now = Instant.now();
        UUID imageId = repository.insertImage(context.caseId(), context.grossingId(), command.specimenId(),
                command.imageName(), command.mediaType(), command.storageReference(), command.metadataJson(),
                command.capturedAt() == null ? now : command.capturedAt(), actor.actorId(), actor.hospitalScope());
        audit.append("PIS-V2-GROSSING-IMAGE-UPLOAD", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", imageId,
                "V2-GROSSING-IMAGE", UUID.randomUUID().toString(), "Grossing image uploaded");
        return repository.images(context.grossingId(), actor.hospitalScope()).stream()
                .filter(value -> value.imageId().equals(imageId)).map(this::image).findFirst().orElseThrow();
    }

    @Transactional
    public ImageResult capture(CaptureCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        requireContext(command.grossingId(), actor);
        try {
            GrossImagingDevicePort.CaptureResult result = imagingDevice.capture(
                    new GrossImagingDevicePort.CaptureRequest(command.grossingId(), command.specimenId(),
                            command.deviceReference(), actor.actorId()));
            return upload(new UploadImageCommand(command.grossingId(), command.specimenId(), result.imageName(),
                    result.mediaType(), result.storageReference(), result.metadataJson(), result.capturedAt()));
        } catch (RuntimeException exception) {
            audit.append("PIS-V2-GROSSING-IMAGE-CAPTURE", MATERIAL_PERMISSION, actor, "ALLOWED", "FAILED",
                    command.grossingId(), "V2-GROSSING", UUID.randomUUID().toString(), "大体图像采集设备调用失败");
            throw new P15BusinessException("V2-GROSSING-CAMERA-UNAVAILABLE", "拍摄设备暂不可用，请重试或稍后上传", 503);
        }
    }

    @Transactional(readOnly = true)
    public DeviceStatusResult deviceStatus(String deviceReference) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        GrossImagingDevicePort.DeviceStatus status = imagingDevice.deviceStatus(deviceReference);
        return new DeviceStatusResult(status.deviceReference(), status.statusCode(), status.detail(), status.checkedAt(),
                actor.actorId());
    }

    @Transactional
    public AnnotationResult annotate(AnnotationCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        requireImage(command.imageId(), actor);
        require(command.annotationTypeCode(), "annotationTypeCode");
        require(command.geometryJson(), "geometryJson");
        if (!List.of("POINT", "RECTANGLE", "POLYGON", "FREEHAND").contains(command.annotationTypeCode())) {
            throw new P15BusinessException("V2-GROSSING-ANNOTATION-TYPE-INVALID", "不支持该标注类型", 400);
        }
        if (!List.of("POINT", "RECTANGLE", "POLYGON", "FREEHAND").contains(command.annotationTypeCode())) {
            throw new P15BusinessException("V2-GROSSING-ANNOTATION-TYPE-INVALID", "不支持该标注类型", 400);
        }
        Instant now = Instant.now();
        UUID id = repository.insertAnnotation(command.imageId(), command.annotationTypeCode(), command.geometryJson(),
                command.label(), command.note(), actor.actorId(), now);
        audit.append("PIS-V2-GROSSING-IMAGE-ANNOTATION-CREATE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                id, "V2-GROSSING-ANNOTATION", UUID.randomUUID().toString(), "Grossing image annotation created");
        return new AnnotationResult(id, command.imageId(), command.annotationTypeCode(), command.geometryJson(),
                command.label(), command.note(), now, actor.actorId(), null);
    }

    @Transactional(readOnly = true)
    public List<AnnotationResult> annotations(UUID imageId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        requireImage(imageId, actor);
        return repository.annotations(imageId).stream().map(this::annotation).toList();
    }

    @Transactional
    public AnnotationResult updateAnnotation(UpdateAnnotationCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        requireImage(command.imageId(), actor);
        require(command.annotationTypeCode(), "annotationTypeCode");
        require(command.geometryJson(), "geometryJson");
        Instant now = Instant.now();
        if (!repository.updateAnnotation(command.annotationId(), command.imageId(), command.annotationTypeCode(),
                command.geometryJson(), command.label(), command.note(), actor.actorId(), now)) {
            throw reject("V2-GROSSING-ANNOTATION-NOT-FOUND", "Annotation is already deleted or missing");
        }
        audit.append("PIS-V2-GROSSING-IMAGE-ANNOTATION-UPDATE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                command.annotationId(), "V2-GROSSING-ANNOTATION", UUID.randomUUID().toString(),
                "Grossing image annotation updated");
        return annotations(command.imageId()).stream()
                .filter(value -> value.annotationId().equals(command.annotationId())).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteAnnotation(UUID annotationId, UUID imageId) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        requireImage(imageId, actor);
        if (!repository.deleteAnnotation(annotationId, imageId, actor.actorId(), Instant.now())) {
            throw reject("V2-GROSSING-ANNOTATION-NOT-FOUND", "Annotation is already deleted or missing");
        }
        audit.append("PIS-V2-GROSSING-IMAGE-ANNOTATION-DELETE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED",
                annotationId, "V2-GROSSING-ANNOTATION", UUID.randomUUID().toString(), "Grossing annotation deleted");
    }

    @Transactional
    public MeasurementResult measure(MeasurementCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        requireImage(command.imageId(), actor);
        require(command.geometryJson(), "geometryJson");
        require(command.unitCode(), "unitCode");
        require(command.measurementModeCode(), "measurementModeCode");
        String unitCode = command.unitCode().toUpperCase();
        if (!List.of("MM", "CM").contains(unitCode)) {
            throw new P15BusinessException("V2-GROSSING-MEASUREMENT-UNIT-INVALID", "测量单位仅支持 mm 或 cm", 400);
        }
        if (command.value() == null || command.value().signum() < 0) {
            throw reject("V2-GROSSING-MEASUREMENT-INVALID", "Measurement value must be non-negative");
        }
        Instant now = Instant.now();
        UUID id = repository.insertMeasurement(command.imageId(), command.geometryJson(), command.value(),
                unitCode, command.measurementModeCode(), actor.actorId(), now);
        audit.append("PIS-V2-GROSSING-IMAGE-MEASUREMENT", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", id,
                "V2-GROSSING-MEASUREMENT", UUID.randomUUID().toString(), "Grossing image measurement created");
        return new MeasurementResult(id, command.imageId(), command.geometryJson(), command.value(), unitCode,
                command.measurementModeCode(), now, actor.actorId());
    }

    @Transactional(readOnly = true)
    public List<MeasurementResult> measurements(UUID imageId) {
        ActorContext actor = authorization.require(QUERY_PERMISSION);
        requireImage(imageId, actor);
        return repository.measurements(imageId).stream().map(this::measurement).toList();
    }

    @Transactional
    public void deleteImage(UUID imageId, DeleteImageCommand command) {
        ActorContext actor = authorization.require(MATERIAL_PERMISSION);
        require(command.reason(), "reason");
        if (repository.imageCase(imageId, actor.hospitalScope()).isEmpty()) {
            throw reject("V2-GROSSING-IMAGE-NOT-FOUND", "Grossing image is missing or deleted");
        }
        repository.softDeleteImage(imageId, command.reason(), actor.actorId(), Instant.now(), actor.hospitalScope());
        audit.append("PIS-V2-GROSSING-IMAGE-DELETE", MATERIAL_PERMISSION, actor, "ALLOWED", "COMPLETED", imageId,
                "V2-GROSSING-IMAGE", UUID.randomUUID().toString(), command.reason());
    }

    private GrossingContext requireContext(UUID grossingId, ActorContext actor) {
        return repository.context(grossingId, actor.hospitalScope())
                .orElseThrow(() -> reject("V2-GROSSING-NOT-FOUND", "Grossing is missing or deleted"));
    }

    private void requireImage(UUID imageId, ActorContext actor) {
        if (repository.imageCase(imageId, actor.hospitalScope()).isEmpty()) {
            throw reject("V2-GROSSING-IMAGE-NOT-FOUND", "Grossing image is missing or deleted");
        }
    }

    private ImageResult image(ImageRow row) {
        return new ImageResult(row.imageId(), row.caseId(), row.grossingId(), row.specimenId(), row.imageName(),
                row.mediaType(), row.storageReference(), row.metadataJson(), row.capturedAt(), row.capturedByRef(),
                row.deletedAt(), row.deletionReason());
    }

    private AnnotationResult annotation(AnnotationRow row) {
        return new AnnotationResult(row.annotationId(), row.imageId(), row.typeCode(), row.geometryJson(), row.label(),
                row.note(), row.createdAt(), row.createdByRef(), row.deletedAt());
    }

    private MeasurementResult measurement(MeasurementRow row) {
        return new MeasurementResult(row.measurementId(), row.imageId(), row.geometryJson(), row.value(),
                row.unitCode(), row.measurementModeCode(), row.createdAt(), row.createdByRef());
    }

    private static void require(Object value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw reject("V2-INVALID-REQUEST", field + " is required");
        }
    }

    private static P15BusinessException reject(String code, String message) {
        return new P15BusinessException(code, message);
    }

    public record UploadImageCommand(UUID grossingId, UUID specimenId, String imageName, String mediaType,
            String storageReference, String metadataJson, Instant capturedAt) { }
    public record CaptureCommand(UUID grossingId, UUID specimenId, String deviceReference) { }
    public record AnnotationCommand(UUID imageId, String annotationTypeCode, String geometryJson, String label,
            String note) { }
    public record UpdateAnnotationCommand(UUID annotationId, UUID imageId, String annotationTypeCode,
            String geometryJson, String label, String note) { }
    public record MeasurementCommand(UUID imageId, String geometryJson, BigDecimal value, String unitCode,
            String measurementModeCode) { }
    public record DeleteImageCommand(String reason) { }
    public record ImageResult(UUID imageId, UUID caseId, UUID grossingId, UUID specimenId, String imageName,
            String mediaType, String storageReference, String metadataJson, Instant capturedAt, String capturedByRef,
            Instant deletedAt, String deletionReason) { }
    public record AnnotationResult(UUID annotationId, UUID imageId, String annotationTypeCode, String geometryJson,
            String label, String note, Instant createdAt, String createdByRef, Instant deletedAt) { }
    public record MeasurementResult(UUID measurementId, UUID imageId, String geometryJson, BigDecimal value,
            String unitCode, String measurementModeCode, Instant createdAt, String createdByRef) { }
    public record DeviceStatusResult(String deviceReference, String statusCode, String detail, Instant checkedAt,
            String checkedBy) { }
}
