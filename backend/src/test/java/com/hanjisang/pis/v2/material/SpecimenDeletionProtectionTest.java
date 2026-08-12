package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SpecimenDeletionProtectionTest extends GrossingClosureTestSupport {

    @Test
    void unusedSpecimenCanBeSoftCancelledAndRemainsStored() throws Exception {
        UUID specimenId = createSpecimen(createCase("SPECIMEN-UNUSED-DELETE"), "DEL-UNUSED", "GROSSING_ADD");

        postOk("/api/v2/registration/specimens/" + specimenId + "/soft-delete",
                "{\"expectedVersion\":0,\"reason\":\"合成误录标本\"}");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE id = ?", Integer.class,
                specimenId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM pis_v2.specimen WHERE id = ?",
                Boolean.class, specimenId)).isTrue();
    }

    @Test
    void specimenWithGrossingOrBlockLineageCannotBeCancelled() throws Exception {
        UUID caseId = createCase("SPECIMEN-DELETE");
        UUID specimenId = createSpecimen(caseId, "DEL-1", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "不可删除来源组织");
        createBlock(grossingId, specimenId, "DEL-A1");

        mockMvc.perform(post("/api/v2/registration/specimens/{id}/soft-delete", specimenId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"reason\":\"合成取消尝试\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM pis_v2.specimen WHERE id = ?",
                Boolean.class, specimenId)).isTrue();
    }
}
