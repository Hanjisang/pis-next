package com.hanjisang.pis.integration.migration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MigrationValidationReport(UUID runId, Status status,
        CountComparison caseCount, CountComparison specimenCount, CountComparison blockCount,
        CountComparison slideCount, CountComparison diagnosisCount, CountComparison reportCount,
        RelationComparison caseSpecimen, RelationComparison specimenBlock, RelationComparison blockSlide,
        RelationComparison caseReport, List<MigrationIssue> exceptions, Instant generatedAt) {

    public MigrationValidationReport {
        exceptions = List.copyOf(exceptions);
    }

    public enum Status { VALIDATED, BLOCKED, FAILED }

    public record CountComparison(long sourceCount, long stagedCount) {
        public long difference() { return sourceCount - stagedCount; }
    }

    public record RelationComparison(String relationCode, long sourceRelationCount, long validRelationCount) {
        public long difference() { return sourceRelationCount - validRelationCount; }
    }
}
