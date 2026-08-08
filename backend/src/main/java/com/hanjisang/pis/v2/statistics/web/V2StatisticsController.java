package com.hanjisang.pis.v2.statistics.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.statistics.application.V2StatisticsApplicationService;

@RestController
@RequestMapping("/api/v2/statistics")
public class V2StatisticsController {

    private final V2StatisticsApplicationService service;
    public V2StatisticsController(V2StatisticsApplicationService service) { this.service = service; }

    @GetMapping("/summary")
    public V2StatisticsApplicationService.StatisticsResult summary() { return service.summary(); }
}
