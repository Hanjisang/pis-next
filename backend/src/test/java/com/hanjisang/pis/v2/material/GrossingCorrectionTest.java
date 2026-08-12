package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GrossingCorrectionTest extends GrossingClosureTestSupport {

    @Test
    void completedGrossingCorrectionKeepsIdentityCompletionAndHistory() throws Exception {
        UUID caseId = createCase("GROSSING-CORRECT");
        UUID specimenId = createSpecimen(caseId, "GC-A", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "修正前标本描述");
        createBlock(grossingId, specimenId, "GC-A1");
        complete(grossingId, 0);
        long version = jdbc.queryForObject("SELECT concurrency_version FROM pis_v2.grossing WHERE id = ?",
                Long.class, grossingId);

        var corrected = postOk("/api/v2/grossings/" + grossingId + "/correct", """
                {"grossDescription":"修正后大体描述","grossingInstruction":"修正后取材说明",
                 "grossingDoctorId":"SYNTH-DOCTOR","recorderId":"SYNTH-RECORDER",
                 "reason":"复核原始记录后纠正","expectedVersion":%d}
                """.formatted(version));

        assertThat(corrected.path("grossingId").asText()).isEqualTo(grossingId.toString());
        assertThat(corrected.path("completedAt").isNull()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.grossing_correction_history WHERE grossing_id = ?",
                Integer.class, grossingId)).isEqualTo(1);
    }
}
