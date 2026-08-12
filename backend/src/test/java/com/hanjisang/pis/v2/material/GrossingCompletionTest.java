package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GrossingCompletionTest extends GrossingClosureTestSupport {

    @Test
    void completionRequiresBusinessFactsAndDoesNotChangeCaseLifecycle() throws Exception {
        UUID caseId = createCase("GROSSING-COMPLETE");
        UUID specimenId = createSpecimen(caseId, "CMP-A", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "完整大体描述");
        createBlock(grossingId, specimenId, "CMP-A1");

        var result = complete(grossingId, 0);

        assertThat(result.path("createdSlideCount").asInt()).isEqualTo(1);
        assertThat(result.path("completedAt").isNull()).isFalse();
        assertThat(jdbc.queryForObject("SELECT lifecycle_state_code FROM pis_v2.pathology_case WHERE id = ?",
                String.class, caseId)).isEqualTo("ACTIVE");
    }
}
