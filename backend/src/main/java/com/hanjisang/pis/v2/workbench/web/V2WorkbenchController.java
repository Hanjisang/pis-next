package com.hanjisang.pis.v2.workbench.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.workbench.application.V2WorkbenchApplicationService;

@RestController
@RequestMapping("/api/v2")
public class V2WorkbenchController {

    private final V2WorkbenchApplicationService service;

    public V2WorkbenchController(V2WorkbenchApplicationService service) { this.service = service; }

    @GetMapping("/my-workbench")
    public V2WorkbenchApplicationService.WorkbenchResult myWorkbench() { return service.myWorkbench(); }
}
