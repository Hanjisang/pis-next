package com.hanjisang.pis.v2.search.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.v2.search.infrastructure.JdbcV2SearchRepository;

@Service
public class V2SearchApplicationService {

    private final JdbcV2SearchRepository repository;
    private final P15AuthorizationService authorization;

    public V2SearchApplicationService(JdbcV2SearchRepository repository, P15AuthorizationService authorization) {
        this.repository = repository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        var actor = authorization.require("P14-PERM-048");
        return repository.search(query, actor.hospitalScope()).stream()
                .map(row -> new SearchResult(row.id(), row.caseId(), row.resultKind(), row.displayCode(), row.summary()))
                .toList();
    }

    public record SearchResult(java.util.UUID id, java.util.UUID caseId, String resultKind, String displayCode,
            String summary) { }
}
