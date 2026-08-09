package com.hanjisang.pis.integration.migration;

import java.time.Instant;
import java.util.UUID;

import com.hanjisang.pis.integration.migration.legacy.LegacyFact;
import com.hanjisang.pis.integration.migration.legacy.MigrationSourceAdapter.SourceManifest;

public interface MigrationStagingStore {

    UUID beginOrResume(UUID requestedRunId, String sourceAdapterCode, SourceManifest manifest,
            String mappingRuleVersion, String startedByRef, Instant now);

    boolean stage(UUID runId, LegacyFact fact, String targetObjectType, UUID targetObjectId,
            String mappingDecisionCode, String evidenceSnapshot, Instant now);

    void addIssue(MigrationIssue issue, Instant now);

    void saveCheckpoint(UUID runId, String checkpointCode, String lastObjectType, String lastObjectId,
            long stagedCount, long exceptionCount, Instant now);

    void complete(MigrationValidationReport report, Instant now);
}
