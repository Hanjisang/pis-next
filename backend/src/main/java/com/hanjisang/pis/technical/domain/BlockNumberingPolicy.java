package com.hanjisang.pis.technical.domain;

import java.util.UUID;

public interface BlockNumberingPolicy {

    String nextBlockNumber();

    static BlockNumberingPolicy dev(String runtimeEnvironment) {
        return () -> {
            if (!"local".equalsIgnoreCase(runtimeEnvironment) && !"test".equalsIgnoreCase(runtimeEnvironment)) {
                throw new IllegalStateException("未配置正式蜡块编号策略");
            }
            return "DEV-BLOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        };
    }
}
