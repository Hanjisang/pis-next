package com.hanjisang.pis.v2.material.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.material.application.V2MaterialReworkApplicationService;
import com.hanjisang.pis.v2.material.application.V2MaterialReworkApplicationService.CompleteCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialReworkApplicationService.RequestCommand;
import com.hanjisang.pis.v2.material.application.V2MaterialReworkApplicationService.PerformCommand;

@RestController
@RequestMapping("/api/v2")
public class V2MaterialReworkController {

    private final V2MaterialReworkApplicationService service;

    public V2MaterialReworkController(V2MaterialReworkApplicationService service) { this.service = service; }

    @PostMapping("/slides/{slideId}/rework")
    public V2MaterialReworkApplicationService.ReworkResult request(@PathVariable UUID slideId,
            @RequestBody Request request) {
        return service.request(slideId, new RequestCommand(request.reworkTypeCode(), request.reason(),
                request.idempotencyKey()));
    }

    @PostMapping("/material-reworks/{reworkId}/complete")
    public V2MaterialReworkApplicationService.ReworkResult complete(@PathVariable UUID reworkId,
            @RequestBody CompleteRequest request) {
        return service.complete(reworkId, new CompleteCommand(request.replacementSlideId()));
    }

    @PostMapping("/slides/{slideId}/rework/perform")
    public V2MaterialReworkApplicationService.ReworkResult perform(@PathVariable UUID slideId,
            @RequestBody Request request) {
        return service.perform(slideId, new PerformCommand(request.reworkTypeCode(), request.reason(),
                request.idempotencyKey()));
    }

    @GetMapping("/cases/{caseId}/material-reworks")
    public List<V2MaterialReworkApplicationService.ReworkResult> history(@PathVariable UUID caseId) {
        return service.caseHistory(caseId);
    }

    public record Request(String reworkTypeCode, String reason, String idempotencyKey) { }
    public record CompleteRequest(UUID replacementSlideId) { }
}
