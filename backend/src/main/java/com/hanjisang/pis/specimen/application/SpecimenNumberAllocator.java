package com.hanjisang.pis.specimen.application;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hanjisang.pis.security.P15BusinessException;

@Component
public class SpecimenNumberAllocator {

    private final String environment;

    public SpecimenNumberAllocator(@Value("${pis.runtime-environment:local}") String environment) {
        this.environment = environment;
    }

    public String specimenNumber() {
        return allocate("DEV-SP");
    }

    public String containerBarcode() {
        return allocate("DEV-CNT");
    }

    private String allocate(String prefix) {
        if (!"local".equalsIgnoreCase(environment) && !"test".equalsIgnoreCase(environment)) {
            throw new P15BusinessException("P12-ERR-021", "未配置生产标本编号策略");
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
