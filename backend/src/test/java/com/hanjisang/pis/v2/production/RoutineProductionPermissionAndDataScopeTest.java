package com.hanjisang.pis.v2.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;

@SpringBootTest(properties = "pis.require-auth=true")
class RoutineProductionPermissionAndDataScopeTest extends RoutineProductionTestSupport {

    @Autowired private AuthenticationSessionStore sessions;
    @Autowired private AuthenticationSessionFilter authenticationFilter;

    @BeforeEach
    void enableAuthenticationFilter() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilter(authenticationFilter).build();
        requestCookie = cookie("technician-a", "LOCAL_HOSPITAL",
                Set.of("P14-PERM-004", "P14-PERM-008", "P14-PERM-013", "P14-PERM-014", "P14-PERM-048"));
    }

    @Test
    void userWithoutMaterialPermissionCannotReadOrWriteRoutineProduction() throws Exception {
        Fixture fixture = routineFixture("FC03A-PERMISSION", 1);
        String blockId = fixture.blockIds().getFirst();
        Cookie ordinary = cookie("ordinary", "LOCAL_HOSPITAL", Set.of("P14-PERM-048"));

        mockMvc.perform(get("/api/v2/production-workbench").cookie(ordinary)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v2/blocks/%s/routine-slides/extra".formatted(blockId)).cookie(ordinary)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slideType\":\"HE\",\"reason\":\"unauthorized\",\"idempotencyKey\":\"forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hospitalBTechnicianCannotSeeOrOperateHospitalAMaterials() throws Exception {
        Fixture fixture = routineFixture("FC03A-SCOPE", 1);
        Cookie hospitalB = cookie("technician-b", "HOSPITAL_B", Set.of("P14-PERM-014", "P14-PERM-048"));

        JsonNode queue = json.readTree(mockMvc.perform(get("/api/v2/production-workbench").cookie(hospitalB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(queue.path("queues").path("routineProduction").path("count").asInt()).isZero();
        mockMvc.perform(get("/api/v2/cases/%s/materials".formatted(fixture.caseId())).cookie(hospitalB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/blocks/%s/routine-slides/extra".formatted(fixture.blockIds().getFirst()))
                .cookie(hospitalB).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slideType\":\"HE\",\"reason\":\"cross scope\",\"idempotencyKey\":\"cross-scope\"}"))
                .andExpect(status().isNotFound());
    }

    private Cookie cookie(String username, String hospital, Set<String> permissions) {
        String token = sessions.create(new AuthenticatedUser(UUID.randomUUID(), username, username, "TEST",
                hospital, "PATHOLOGY", "WORKBENCH", permissions, null, null));
        return new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token);
    }
}
