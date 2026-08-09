package com.hanjisang.pis.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class P15AuthorizationService {

    private final String runtimeEnvironment;
    private final boolean requireAuthentication;
    private final ActorContext configuredActor;
    private final DoctorIdentityResolver doctorIdentityResolver;

    public P15AuthorizationService(
            @Value("${pis.runtime-environment:local}") String runtimeEnvironment,
            @Value("${pis.actor-id:p15-local-registration-actor}") String actorId,
            @Value("${pis.actor-permissions:}") String permissions,
            @Value("${pis.actor-task-scope:P15-REGISTRATION-RECEIVING}") String taskScope,
            @Value("${pis.subject-type-code:HUMAN_USER}") String subjectTypeCode,
            @Value("${pis.require-auth:false}") boolean requireAuthentication,
            DoctorIdentityResolver doctorIdentityResolver) {
        this.runtimeEnvironment = runtimeEnvironment;
        this.requireAuthentication = requireAuthentication;
        this.doctorIdentityResolver = doctorIdentityResolver;
        Set<String> permissionSet = Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.configuredActor = new ActorContext(actorId, subjectTypeCode, runtimeEnvironment, permissionSet,
                "LOCAL_HOSPITAL", "PATHOLOGY", taskScope);
    }

    public AuthorizationDecision decide(String permissionCode) {
        boolean authenticated = AuthenticationContext.current().isPresent();
        ActorContext actor = currentActor();
        boolean environmentAllowed = "local".equalsIgnoreCase(runtimeEnvironment)
                || "test".equalsIgnoreCase(runtimeEnvironment);
        boolean permissionAllowed = actor.permissions().contains(permissionCode);
        if (requireAuthentication && !authenticated) {
            return new AuthorizationDecision(false, permissionCode, "P14-AUTHENTICATION-REQUIRED", actor);
        }
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

    private ActorContext currentActor() {
        return AuthenticationContext.current()
                .map(user -> new ActorContext(doctorIdentityResolver.actorReference(user), "HUMAN_USER",
                        runtimeEnvironment, user.permissions(), user.hospitalScope(), user.departmentScope(),
                        user.taskScope()))
                .orElse(configuredActor);
    }

    public ActorContext requireTask(String permissionCode, String taskCode) {
        ActorContext authorized = require(permissionCode);
        boolean taskAllowed = Arrays.stream(authorized.taskScope().split(","))
                .map(String::trim)
                .anyMatch(taskCode::equals);
        if (!taskAllowed) {
            throw new P15BusinessException("P12-ERR-077", "当前主体不承担该业务任务", 403);
        }
        return authorized;
    }
}
