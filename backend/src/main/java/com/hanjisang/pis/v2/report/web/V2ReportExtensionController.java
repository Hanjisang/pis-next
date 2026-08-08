package com.hanjisang.pis.v2.report.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.report.api.V2ReportExtensionRegistry;

@RestController
@RequestMapping("/api/v2/report-extensions")
public class V2ReportExtensionController {

    private final V2ReportExtensionRegistry registry;
    public V2ReportExtensionController(V2ReportExtensionRegistry registry) { this.registry = registry; }

    @GetMapping
    public List<String> registeredCodes() { return registry.registeredCodes(); }
}
