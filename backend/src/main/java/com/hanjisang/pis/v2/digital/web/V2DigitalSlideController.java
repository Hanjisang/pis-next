package com.hanjisang.pis.v2.digital.web;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.hanjisang.pis.v2.digital.application.V2DigitalSlideApplicationService;
import com.hanjisang.pis.v2.digital.application.V2DigitalSlideApplicationService.CreateDigitalSlideCommand;
import com.hanjisang.pis.v2.digital.application.V2DigitalSlideApplicationService.RebindCommand;

@RestController
@RequestMapping("/api/v2/digital-slides")
public class V2DigitalSlideController {

    private final V2DigitalSlideApplicationService service;

    public V2DigitalSlideController(V2DigitalSlideApplicationService service) { this.service = service; }

    @PostMapping
    public V2DigitalSlideApplicationService.DigitalSlideResult create(@RequestBody CreateRequest request) {
        return service.create(new CreateDigitalSlideCommand(request.caseId(), request.blockId(), request.slideId(),
                request.bindingModeCode(), request.viewerReference(), request.sourcePlatform()));
    }

    @PostMapping("/{digitalSlideId}/rebind")
    public V2DigitalSlideApplicationService.DigitalSlideResult rebind(@PathVariable UUID digitalSlideId,
            @RequestBody RebindRequest request) {
        return service.rebind(digitalSlideId, new RebindCommand(request.blockId(), request.slideId()));
    }

    @PostMapping("/{digitalSlideId}/unbind")
    public V2DigitalSlideApplicationService.DigitalSlideResult unbind(@PathVariable UUID digitalSlideId) {
        return service.unbind(digitalSlideId);
    }

    @GetMapping("/{digitalSlideId}")
    public V2DigitalSlideApplicationService.DigitalSlideResult get(@PathVariable UUID digitalSlideId) {
        return service.get(digitalSlideId);
    }

    @GetMapping("/cases/{caseId}")
    public List<V2DigitalSlideApplicationService.DigitalSlideResult> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }

    @GetMapping("/{digitalSlideId}/annotations")
    public List<V2DigitalSlideApplicationService.AnnotationResult> annotations(@PathVariable UUID digitalSlideId) {
        return service.annotations(digitalSlideId);
    }

    @PostMapping("/{digitalSlideId}/annotations")
    public V2DigitalSlideApplicationService.AnnotationResult annotate(@PathVariable UUID digitalSlideId,
            @RequestBody AnnotationRequest request) {
        return service.annotate(digitalSlideId, new V2DigitalSlideApplicationService.AnnotationCommand(
                request.annotationTypeCode(), request.geometryJson(), request.label(), request.note(),
                request.idempotencyKey()));
    }

    @GetMapping("/{digitalSlideId}/measurements")
    public List<V2DigitalSlideApplicationService.MeasurementResult> measurements(@PathVariable UUID digitalSlideId) {
        return service.measurements(digitalSlideId);
    }

    @PostMapping("/{digitalSlideId}/measurements")
    public V2DigitalSlideApplicationService.MeasurementResult measure(@PathVariable UUID digitalSlideId,
            @RequestBody MeasurementRequest request) {
        return service.measure(digitalSlideId, new V2DigitalSlideApplicationService.MeasurementCommand(
                request.geometryJson(), request.value(), request.unitCode(), request.measurementModeCode(),
                request.idempotencyKey()));
    }

    @GetMapping("/{digitalSlideId}/screenshots")
    public List<V2DigitalSlideApplicationService.ScreenshotResult> screenshots(@PathVariable UUID digitalSlideId) {
        return service.screenshots(digitalSlideId);
    }

    @PostMapping("/{digitalSlideId}/screenshots")
    public V2DigitalSlideApplicationService.ScreenshotResult screenshot(@PathVariable UUID digitalSlideId,
            @RequestBody ScreenshotRequest request) {
        return service.screenshot(digitalSlideId, new V2DigitalSlideApplicationService.ScreenshotCommand(
                request.viewportJson(), request.mediaType(), request.imageDataBase64(), request.idempotencyKey()));
    }

    @GetMapping("/screenshots/{screenshotId}/content")
    public ResponseEntity<byte[]> screenshotContent(@PathVariable UUID screenshotId) {
        var result = service.screenshotContent(screenshotId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(result.mediaType()))
                .header("X-Content-SHA256", result.contentHash()).body(result.content());
    }

    public record CreateRequest(UUID caseId, UUID blockId, UUID slideId, String bindingModeCode,
            String viewerReference, String sourcePlatform) { }
    public record RebindRequest(UUID blockId, UUID slideId) { }
    public record AnnotationRequest(String annotationTypeCode, String geometryJson, String label, String note,
            String idempotencyKey) { }
    public record MeasurementRequest(String geometryJson, BigDecimal value, String unitCode,
            String measurementModeCode, String idempotencyKey) { }
    public record ScreenshotRequest(String viewportJson, String mediaType, String imageDataBase64,
            String idempotencyKey) { }
}
