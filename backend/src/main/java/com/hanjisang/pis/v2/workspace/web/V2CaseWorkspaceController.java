package com.hanjisang.pis.v2.workspace.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.workspace.application.V2CaseWorkspaceApplicationService;

@RestController
@RequestMapping("/api/v2/case-workspaces")
public class V2CaseWorkspaceController {

    private final V2CaseWorkspaceApplicationService service;

    public V2CaseWorkspaceController(V2CaseWorkspaceApplicationService service) { this.service = service; }

    @GetMapping("/{caseId}")
    public V2CaseWorkspaceApplicationService.CaseWorkspaceResult workspace(@PathVariable UUID caseId) {
        return service.workspace(caseId);
    }
}
