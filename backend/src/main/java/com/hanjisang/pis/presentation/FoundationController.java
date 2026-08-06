package com.hanjisang.pis.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FoundationController {

    private static final List<String> MODULES = List.of(
            "accession",
            "specimen",
            "technical",
            "frozen",
            "cytology",
            "molecular",
            "referral",
            "diagnosis",
            "multimodal",
            "digital",
            "integration",
            "quality",
            "security",
            "archive",
            "presentation");

    @GetMapping("/api/foundation")
    public FoundationResponse foundation() {
        return new FoundationResponse("PIS Next", "P15", MODULES);
    }

    public record FoundationResponse(String system, String phase, List<String> modules) {
    }
}
