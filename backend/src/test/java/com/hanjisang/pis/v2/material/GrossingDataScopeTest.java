package com.hanjisang.pis.v2.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GrossingDataScopeTest extends GrossingSecurityTestSupport {

    @Test
    void hospitalBUserCannotReadOrWriteHospitalAWorkspace() throws Exception {
        var hospitalB = cookie("grossing-b", "HOSPITAL_B",
                Set.of("P14-PERM-008", "P14-PERM-010", "P14-PERM-013", "P14-PERM-014", "P14-PERM-048"));
        mockMvc.perform(get("/api/v2/cases/{id}/grossing-workspace", caseId).cookie(hospitalB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/cases/{id}/grossings", caseId).cookie(hospitalB)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceType":"INITIAL","grossDescription":"跨院取材",
                         "grossingDoctorId":"grossing-b","recorderId":"grossing-b","idempotencyKey":"cross-scope"}
                        """))
                .andExpect(status().isNotFound());
    }
}
