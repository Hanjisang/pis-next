package com.hanjisang.pis.v2.configuration.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.configuration.application.V2ConfigurationApplicationService;
import com.hanjisang.pis.v2.configuration.application.V2ConfigurationApplicationService.UpdateBusinessType;
import com.hanjisang.pis.v2.configuration.application.V2ConfigurationApplicationService.UpdateMapping;
import com.hanjisang.pis.v2.configuration.application.V2ConfigurationApplicationService.UpdateNumberRule;
import com.hanjisang.pis.v2.configuration.application.V2ConfigurationApplicationService.UpdateTechnicalProject;
import com.hanjisang.pis.v2.configuration.application.V2ConfigurationApplicationService.UpdateTemplate;

@RestController
@RequestMapping("/api/v2/configuration")
public class V2ConfigurationController {

    private final V2ConfigurationApplicationService service;

    public V2ConfigurationController(V2ConfigurationApplicationService service) { this.service = service; }

    @GetMapping
    public Object snapshot() { return service.snapshot(); }

    @PutMapping("/business-types/{id}")
    public Object businessType(@PathVariable UUID id, @RequestBody UpdateBusinessType request) {
        return service.updateBusinessType(id, request);
    }

    @PutMapping("/application-item-mappings/{id}")
    public Object applicationMapping(@PathVariable UUID id, @RequestBody UpdateMapping request) {
        return service.updateMapping(id, request);
    }

    @PutMapping("/pathology-number-rules/{id}")
    public Object numberRule(@PathVariable UUID id, @RequestBody UpdateNumberRule request) {
        return service.updateNumberRule(id, request);
    }

    @PutMapping("/technical-projects/{id}")
    public Object technicalProject(@PathVariable UUID id, @RequestBody UpdateTechnicalProject request) {
        return service.updateTechnicalProject(id, request);
    }

    @PutMapping("/diagnosis-templates/{id}")
    public Object diagnosisTemplate(@PathVariable UUID id, @RequestBody UpdateTemplate request) {
        return service.updateDiagnosisTemplate(id, request);
    }

    @PutMapping("/report-templates/{id}")
    public Object reportTemplate(@PathVariable UUID id, @RequestBody UpdateTemplate request) {
        return service.updateReportTemplate(id, request);
    }
}
