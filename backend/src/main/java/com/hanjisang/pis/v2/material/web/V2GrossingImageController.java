package com.hanjisang.pis.v2.material.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService;
import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService.AnnotationCommand;
import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService.CaptureCommand;
import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService.DeleteImageCommand;
import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService.MeasurementCommand;
import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService.UpdateAnnotationCommand;
import com.hanjisang.pis.v2.material.application.V2GrossingImageApplicationService.UploadImageCommand;

@RestController
@RequestMapping("/api/v2/material/grossings")
public class V2GrossingImageController {

    private final V2GrossingImageApplicationService service;

    public V2GrossingImageController(V2GrossingImageApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{grossingId}/images")
    public List<V2GrossingImageApplicationService.ImageResult> images(@PathVariable UUID grossingId) {
        return service.images(grossingId);
    }

    @PostMapping("/{grossingId}/images")
    public V2GrossingImageApplicationService.ImageResult upload(@PathVariable UUID grossingId,
            @RequestBody UploadImageRequest request) {
        return service.upload(new UploadImageCommand(grossingId, request.specimenId(), request.imageName(),
                request.mediaType(), request.storageReference(), request.metadataJson(), request.capturedAt()));
    }

    @PostMapping("/{grossingId}/images/capture")
    public V2GrossingImageApplicationService.ImageResult capture(@PathVariable UUID grossingId,
            @RequestBody CaptureRequest request) {
        return service.capture(new CaptureCommand(grossingId, request.specimenId(), request.deviceReference()));
    }

    @GetMapping("/images/{imageId}/annotations")
    public List<V2GrossingImageApplicationService.AnnotationResult> annotations(@PathVariable UUID imageId) {
        return service.annotations(imageId);
    }

    @PostMapping("/images/{imageId}/annotations")
    public V2GrossingImageApplicationService.AnnotationResult annotate(@PathVariable UUID imageId,
            @RequestBody AnnotationRequest request) {
        return service.annotate(new AnnotationCommand(imageId, request.annotationTypeCode(), request.geometryJson(),
                request.label(), request.note()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/images/{imageId}/annotations/{annotationId}")
    public V2GrossingImageApplicationService.AnnotationResult updateAnnotation(@PathVariable UUID imageId,
            @PathVariable UUID annotationId, @RequestBody AnnotationRequest request) {
        return service.updateAnnotation(new UpdateAnnotationCommand(annotationId, imageId,
                request.annotationTypeCode(), request.geometryJson(), request.label(), request.note()));
    }

    @DeleteMapping("/images/{imageId}/annotations/{annotationId}")
    public void deleteAnnotation(@PathVariable UUID imageId, @PathVariable UUID annotationId) {
        service.deleteAnnotation(annotationId, imageId);
    }

    @PostMapping("/images/{imageId}/measurements")
    public V2GrossingImageApplicationService.MeasurementResult measure(@PathVariable UUID imageId,
            @RequestBody MeasurementRequest request) {
        return service.measure(new MeasurementCommand(imageId, request.geometryJson(), request.value(),
                request.unitCode(), request.measurementModeCode()));
    }

    @GetMapping("/images/{imageId}/measurements")
    public List<V2GrossingImageApplicationService.MeasurementResult> measurements(@PathVariable UUID imageId) {
        return service.measurements(imageId);
    }

    @PostMapping("/images/{imageId}/delete")
    public void deleteImage(@PathVariable UUID imageId, @RequestBody DeleteRequest request) {
        service.deleteImage(imageId, new DeleteImageCommand(request.reason()));
    }

    @GetMapping("/device-status")
    public V2GrossingImageApplicationService.DeviceStatusResult deviceStatus(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String deviceReference) {
        return service.deviceStatus(deviceReference == null || deviceReference.isBlank()
                ? "SIMULATOR-GROSS-IMAGING" : deviceReference);
    }

    public record UploadImageRequest(UUID specimenId, String imageName, String mediaType, String storageReference,
            String metadataJson, Instant capturedAt) { }
    public record CaptureRequest(UUID specimenId, String deviceReference) { }
    public record AnnotationRequest(String annotationTypeCode, String geometryJson, String label, String note) { }
    public record MeasurementRequest(String geometryJson, BigDecimal value, String unitCode,
            String measurementModeCode) { }
    public record DeleteRequest(String reason) { }
}
