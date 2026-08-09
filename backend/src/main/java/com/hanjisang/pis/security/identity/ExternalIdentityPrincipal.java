package com.hanjisang.pis.security.identity;

import java.time.Instant;
import java.util.Set;

public record ExternalIdentityPrincipal(String providerCode, String externalSubject, String displayName,
        Set<String> externalGroups, String hospitalProfileCode, String campusCode, String departmentCode,
        Instant authenticatedAt) {

    public ExternalIdentityPrincipal {
        externalGroups = Set.copyOf(externalGroups);
    }
}
