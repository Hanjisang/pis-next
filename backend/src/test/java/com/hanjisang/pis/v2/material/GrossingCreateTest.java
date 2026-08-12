package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GrossingCreateTest extends GrossingClosureTestSupport {

    @Test
    void activeCaseHasOnlyOneInitialGrossing() throws Exception {
        UUID caseId = createCase("GROSSING-CREATE");
        UUID first = createGrossing(caseId, "INITIAL", null);

        mockMvc.perform(post("/api/v2/cases/{id}/grossings", caseId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceType":"INITIAL","grossDescription":"重复初始取材",
                         "grossingDoctorId":"SYNTH-DOCTOR","recorderId":"SYNTH-RECORDER",
                         "idempotencyKey":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.grossing WHERE case_id = ? AND source_type = 'INITIAL'",
                Integer.class, caseId)).isEqualTo(1);
        assertThat(first).isNotNull();
    }
}
