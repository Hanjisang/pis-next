package com.hanjisang.pis.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class P15AuthorizationService {

    private final String runtimeEnvironment;
    private final ActorContext actor;

    public P15AuthorizationService(
            @Value("${pis.runtime-environment:local}") String runtimeEnvironment,
            @Value("${pis.actor-id:p15-local-registration-actor}") String actorId,
            @Value("${pis.actor-permissions:}") String permissions) {
        this.runtimeEnvironment = runtimeEnvironment;
        Set<String> permissionSet = Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.actor = new ActorContext(actorId, "HUMAN_USER", runtimeEnvironment, permissionSet,
                "LOCAL_HOSPITAL", "PATHOLOGY", "P15-REGISTRATION-RECEIVING");
    }

    public AuthorizationDecision decide(String permissionCode) {
        boolean environmentAllowed = "local".equalsIgnoreCase(runtimeEnvironment)
                || "test".equalsIgnoreCase(runtimeEnvironment);
        boolean permissionAllowed = actor.permissions().contains(permissionCode);
        if (!environmentAllowed) {
            return new AuthorizationDecision(false, permissionCode, "P14-ENVIRONMENT-NOT-TRUSTED", actor);
        }
        if (!permissionAllowed) {
            return new AuthorizationDecision(false, permissionCode, "P14-PERMISSION-DENIED", actor);
        }
        return new AuthorizationDecision(true, permissionCode, "P14-ALLOWED", actor);
    }

    public ActorContext require(String permissionCode) {
        AuthorizationDecision decision = decide(permissionCode);
        if (!decision.allowed()) {
            throw new P15BusinessException("P12-ERR-075", "授权拒绝：" + decision.reason(), 403);
        }
        return decision.actor();
    }
}
