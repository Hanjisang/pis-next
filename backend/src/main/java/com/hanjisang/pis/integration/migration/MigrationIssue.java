package com.hanjisang.pis.integration.migration;

import java.util.UUID;

import com.hanjisang.pis.integration.migration.legacy.LegacyFact.ObjectType;

public record MigrationIssue(UUID id, UUID runId, String exceptionCode, Severity severity,
        ObjectType sourceObjectType, String sourceObjectId, String reason, String manualAction,
        Status status, String evidenceReference) {

    public enum Severity { P0, P1, P2 }

    public enum Status { OPEN, IN_REVIEW, RESOLVED, WAIVED, BLOCKED }
}
