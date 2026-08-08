package com.hanjisang.pis.v2.digital.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public record CreateRequest(UUID caseId, UUID blockId, UUID slideId, String bindingModeCode,
            String viewerReference, String sourcePlatform) { }
    public record RebindRequest(UUID blockId, UUID slideId) { }
}
