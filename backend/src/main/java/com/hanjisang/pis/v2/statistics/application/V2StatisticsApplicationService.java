package com.hanjisang.pis.v2.statistics.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanjisang.pis.security.P15AuthorizationService;
import com.hanjisang.pis.v2.statistics.infrastructure.JdbcV2StatisticsRepository;

@Service
public class V2StatisticsApplicationService {

    private final JdbcV2StatisticsRepository repository;
    private final P15AuthorizationService authorization;

    public V2StatisticsApplicationService(JdbcV2StatisticsRepository repository, P15AuthorizationService authorization) {
        this.repository = repository; this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public StatisticsResult summary() {
        var actor = authorization.require("P14-PERM-048");
        return new StatisticsResult(repository.counts(actor.hospitalScope()), repository.businessTypes(actor.hospitalScope()));
    }

    public record StatisticsResult(JdbcV2StatisticsRepository.SummaryCounts counts,
            List<JdbcV2StatisticsRepository.BusinessTypeCount> businessTypeDistribution) { }
}
