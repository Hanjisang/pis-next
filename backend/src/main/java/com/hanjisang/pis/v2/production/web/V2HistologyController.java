package com.hanjisang.pis.v2.production.web;

import java.util.UUID;
import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.production.application.V2HistologyApplicationService;

@RestController
@RequestMapping("/api/v2/histology")
public class V2HistologyController {

    private final V2HistologyApplicationService service;

    public V2HistologyController(V2HistologyApplicationService service) { this.service = service; }

    @PostMapping("/slides/{slideId}/phases/{phaseCode}/start")
    public V2HistologyApplicationService.PhaseFact start(@PathVariable UUID slideId, @PathVariable String phaseCode,
            @RequestBody PhaseRequest request) { return service.start(slideId, phaseCode, request.deviceReference(), request.batchReference()); }

    @PostMapping("/slides/{slideId}/phases/{phaseCode}/complete")
    public V2HistologyApplicationService.PhaseFact complete(@PathVariable UUID slideId, @PathVariable String phaseCode) {
        return service.complete(slideId, phaseCode);
    }

    @PostMapping("/phases/{phaseCode}/start-batch")
    public List<V2HistologyApplicationService.PhaseFact> startBatch(@PathVariable String phaseCode,
            @RequestBody BatchRequest request) { return service.startBatch(request.slideIds(), phaseCode); }

    @PostMapping("/phases/{phaseCode}/complete-batch")
    public List<V2HistologyApplicationService.PhaseFact> completeBatch(@PathVariable String phaseCode,
            @RequestBody BatchRequest request) { return service.completeBatch(request.slideIds(), phaseCode); }

    @PostMapping("/slides/{slideId}/phases/{phaseCode}/exception")
    public V2HistologyApplicationService.PhaseFact exception(@PathVariable UUID slideId, @PathVariable String phaseCode,
            @RequestBody ExceptionRequest request) {
        return service.recordException(slideId, phaseCode, request.exceptionCode(), request.note());
    }

    public record PhaseRequest(String deviceReference, String batchReference) { }
    public record ExceptionRequest(String exceptionCode, String note) { }
    public record BatchRequest(List<UUID> slideIds) { }
}
