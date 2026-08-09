package com.hanjisang.pis.security;

import java.util.UUID;

public record OrganizationContext(UUID hospitalProfileId, String hospitalProfileCode, UUID campusId,
        String campusCode, UUID departmentId, String departmentCode, String departmentName) {
}
