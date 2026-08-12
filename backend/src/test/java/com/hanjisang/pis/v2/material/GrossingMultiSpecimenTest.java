package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GrossingMultiSpecimenTest extends GrossingClosureTestSupport {

    @Test
    void oneGrossingPreservesPerSpecimenDescriptionsAndLineage() throws Exception {
        UUID caseId = createCase("GROSSING-MULTI");
        UUID specimenA = createSpecimen(caseId, "M-A", "REGISTRATION");
        UUID specimenB = createSpecimen(caseId, "M-B", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenA, "A标本大体描述");
        associate(grossingId, specimenB, "B标本大体描述");
        createBlock(grossingId, specimenA, "M-A1");
        createBlock(grossingId, specimenB, "M-B1");

        var workspace = getOk("/api/v2/cases/" + caseId + "/grossing-workspace");
        assertThat(workspace.path("specimens")).hasSize(2);
        assertThat(workspace.path("specimens").toString()).contains("A标本大体描述", "B标本大体描述");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT specimen_id) FROM pis_v2.block WHERE grossing_id = ?",
                Integer.class, grossingId)).isEqualTo(2);
    }
}
