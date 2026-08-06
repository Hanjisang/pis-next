package com.hanjisang.pis.security;

import java.util.Set;

public record ActorContext(String actorId, String subjectTypeCode, String runtimeEnvironment, Set<String> permissions,
        String hospitalScope, String departmentScope, String taskScope) {
}
