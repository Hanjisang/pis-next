package com.hanjisang.pis.security;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalTestEnhancedAuthenticationProvider implements EnhancedAuthenticationPort {

    private final String runtimeEnvironment;

    public LocalTestEnhancedAuthenticationProvider(
            @Value("${pis.runtime-environment:local}") String runtimeEnvironment) {
        this.runtimeEnvironment = runtimeEnvironment;
    }

    @Override
    public EnhancedAuthenticationProof prove(ActorContext actor, String operationCode) {
        if (!"local".equalsIgnoreCase(runtimeEnvironment) && !"test".equalsIgnoreCase(runtimeEnvironment)) {
            throw new P15BusinessException("P12-ERR-076", "正式运行环境缺少已接入的增强认证证明", 403);
        }
        return new EnhancedAuthenticationProof("DEV-ENHANCED-AUTH-" + UUID.randomUUID(), actor.actorId(), operationCode);
    }
}
