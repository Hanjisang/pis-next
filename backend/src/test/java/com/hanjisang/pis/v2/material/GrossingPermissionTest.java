package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

class GrossingPermissionTest extends GrossingSecurityTestSupport {

    @Test
    void queryPermissionDoesNotGrantGrossingWritesOrActions() throws Exception {
        var user = cookie("ordinary", "HOSPITAL_A", Set.of("P14-PERM-048"));
        var workspace = new ObjectMapper().readTree(mockMvc.perform(
                get("/api/v2/cases/{id}/grossing-workspace", caseId).cookie(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.path("availableActions").toString()).doesNotContain("GROSSING_START", "BLOCK_CREATE");
        mockMvc.perform(get("/api/v2/registration/specimens/{id}", specimenId).cookie(user))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/cases/{id}/grossings", caseId).cookie(user)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceType":"INITIAL","grossDescription":"无权限取材",
                         "grossingDoctorId":"ordinary","recorderId":"ordinary","idempotencyKey":"forbidden"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedGrossingCapabilityReturnsSpecimenAndBlockActions() throws Exception {
        var user = cookie("grosser", "HOSPITAL_A",
                Set.of("P14-PERM-008", "P14-PERM-010", "P14-PERM-013", "P14-PERM-014", "P14-PERM-048"));
        var workspace = new ObjectMapper().readTree(mockMvc.perform(
                get("/api/v2/cases/{id}/grossing-workspace", caseId).cookie(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(workspace.path("availableActions").toString())
                .contains("SPECIMEN_ADD", "SPECIMEN_UPDATE", "SPECIMEN_SPLIT", "SPECIMEN_CANCEL",
                        "GROSSING_START", "BLOCK_CREATE", "BLOCK_UPDATE", "BLOCK_CANCEL", "BLOCK_PRINT",
                        "BLOCK_VERIFY");
    }
}
