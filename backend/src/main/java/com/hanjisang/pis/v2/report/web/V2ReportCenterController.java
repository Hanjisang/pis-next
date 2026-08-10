package com.hanjisang.pis.v2.report.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.report.application.V2ReportCenterApplicationService;

@RestController
@RequestMapping("/api/v2/report-center")
public class V2ReportCenterController {

    private final V2ReportCenterApplicationService service;

    public V2ReportCenterController(V2ReportCenterApplicationService service) { this.service = service; }

    @GetMapping
    public V2ReportCenterApplicationService.ReportCenterResult get() { return service.get(); }
}
