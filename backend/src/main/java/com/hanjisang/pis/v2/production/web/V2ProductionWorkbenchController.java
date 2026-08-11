package com.hanjisang.pis.v2.production.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.production.application.V2ProductionWorkbenchApplicationService;

@RestController
@RequestMapping("/api/v2/production-workbench")
public class V2ProductionWorkbenchController {

    private final V2ProductionWorkbenchApplicationService service;

    public V2ProductionWorkbenchController(V2ProductionWorkbenchApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public V2ProductionWorkbenchApplicationService.ProductionWorkbenchResult workbench() {
        return service.workbench();
    }
}
