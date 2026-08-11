package com.hanjisang.pis.v2.workbench.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.workbench.application.V2WorkbenchApplicationService;
import com.hanjisang.pis.v2.workbench.application.CaseProgressProjectionApplicationService;

@RestController
@RequestMapping("/api/v2")
public class V2WorkbenchController {

    private final V2WorkbenchApplicationService service;
    private final CaseProgressProjectionApplicationService progressService;

    public V2WorkbenchController(V2WorkbenchApplicationService service,
            CaseProgressProjectionApplicationService progressService) {
        this.service = service;
        this.progressService = progressService;
    }

    @GetMapping("/my-workbench")
    public V2WorkbenchApplicationService.WorkbenchResult myWorkbench() { return service.myWorkbench(); }

    @GetMapping("/cases/{caseId}/progress")
    public CaseProgressProjectionApplicationService.CaseProgress caseProgress(@PathVariable UUID caseId) {
        return progressService.progress(caseId);
    }

    @GetMapping("/my-registered-cases")
    public List<CaseProgressProjectionApplicationService.CaseProgress> myRegisteredCases() {
        return progressService.registeredCases();
    }
}
