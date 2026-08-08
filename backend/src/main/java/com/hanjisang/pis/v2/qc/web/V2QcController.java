package com.hanjisang.pis.v2.qc.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.qc.application.V2QcApplicationService;

@RestController
@RequestMapping("/api/v2/qc")
public class V2QcController {

    private final V2QcApplicationService service;
    public V2QcController(V2QcApplicationService service) { this.service = service; }

    @GetMapping("/rules")
    public List<V2QcApplicationService.QcRuleResult> rules() { return service.rules(); }

    @PostMapping("/evaluate")
    public List<V2QcApplicationService.QcEvaluationResult> evaluate(@RequestBody(required = false) EvaluateRequest request) {
        return service.evaluate(request == null ? null : request.caseId());
    }

    @GetMapping("/evaluations")
    public List<V2QcApplicationService.QcEvaluationResult> evaluations(@RequestParam(required = false) UUID caseId) {
        return service.evaluations(caseId);
    }

    public record EvaluateRequest(UUID caseId) { }
}
