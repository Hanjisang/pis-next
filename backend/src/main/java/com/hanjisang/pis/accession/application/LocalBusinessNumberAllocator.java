package com.hanjisang.pis.accession.application;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hanjisang.pis.security.P15BusinessException;

@Component
public class LocalBusinessNumberAllocator implements BusinessNumberAllocator {

    private final String environment;

    public LocalBusinessNumberAllocator(@Value("${pis.runtime-environment:local}") String environment) {
        this.environment = environment;
    }

    @Override
    public String applicationNumber() {
        return allocate("DEV-REQ");
    }

    @Override
    public String caseNumber() {
        return allocate("DEV-CASE");
    }

    private String allocate(String prefix) {
        if (!"local".equalsIgnoreCase(environment) && !"test".equalsIgnoreCase(environment)) {
            throw new P15BusinessException("P12-ERR-011", "未配置生产业务编号策略");
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
