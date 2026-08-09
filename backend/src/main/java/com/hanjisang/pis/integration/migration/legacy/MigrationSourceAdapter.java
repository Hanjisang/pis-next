package com.hanjisang.pis.integration.migration.legacy;

import java.time.Instant;
import java.util.List;

public interface MigrationSourceAdapter {

    String adapterCode();

    SourceManifest manifest();

    List<LegacyFact> readFacts();

    record SourceManifest(String sourceReference, String datasetVersion, String schemaHash, Instant capturedAt,
            long recordCount) { }
}
