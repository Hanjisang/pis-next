package com.hanjisang.pis.v2.history.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.history.application.V2PatientHistoryApplicationService;

@RestController
@RequestMapping("/api/v2/patient-history")
public class V2PatientHistoryController {

    private final V2PatientHistoryApplicationService service;

    public V2PatientHistoryController(V2PatientHistoryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public V2PatientHistoryApplicationService.PatientHistoryResult find(
            @RequestParam String patientReference) {
        return service.find(patientReference);
    }
}
