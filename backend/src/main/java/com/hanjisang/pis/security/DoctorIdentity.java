package com.hanjisang.pis.security;

import java.util.UUID;

public record DoctorIdentity(UUID id, UUID userId, String doctorCode, String displayName, String title,
        String department, UUID departmentId, boolean enabled) {
}
