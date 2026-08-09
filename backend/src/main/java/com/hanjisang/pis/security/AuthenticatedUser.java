package com.hanjisang.pis.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, String username, String displayName, String roleCode,
        String hospitalScope, String departmentScope, String taskScope, Set<String> permissions,
        DoctorIdentity doctorIdentity, OrganizationContext organization) {
}
