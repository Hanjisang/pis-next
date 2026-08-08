package com.hanjisang.pis.v2.search.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.v2.search.application.V2SearchApplicationService;

@RestController
@RequestMapping("/api/v2/search")
public class V2SearchController {

    private final V2SearchApplicationService service;
    public V2SearchController(V2SearchApplicationService service) { this.service = service; }

    @GetMapping
    public List<V2SearchApplicationService.SearchResult> search(@RequestParam String q) { return service.search(q); }
}
