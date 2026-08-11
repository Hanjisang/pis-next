package com.hanjisang.pis.v2.production.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.production.application.V2HistologyApplicationService;

@RestController
@RequestMapping("/api/v2/histology-workbench")
public class V2HistologyWorkbenchQueryController {

    private final V2HistologyApplicationService service;

    public V2HistologyWorkbenchQueryController(V2HistologyApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public V2HistologyApplicationService.HistologyWorkbenchResult workbench(
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) UUID roundId) {
        return service.workbench(caseId, roundId);
    }
}
