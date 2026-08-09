package com.hanjisang.pis.integration.migration.legacy;

public record LegacyFact(ObjectType objectType, String legacyId, ObjectType parentType, String parentLegacyId,
        String businessReference, String payloadReference, String payloadDigest) {

    public enum ObjectType {
        PATIENT,
        CASE,
        SPECIMEN,
        BLOCK,
        SLIDE,
        DIAGNOSIS,
        REPORT_METADATA
    }
}
