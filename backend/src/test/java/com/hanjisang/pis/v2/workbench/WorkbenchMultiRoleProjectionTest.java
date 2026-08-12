package com.hanjisang.pis.v2.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;

@SpringBootTest(properties = "pis.require-auth=true")
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class WorkbenchMultiRoleProjectionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationSessionStore sessions;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
    }

    @Test
    void oneWorkbenchUnionsRegistrarAndTechnicianPermissions() throws Exception {
        UUID id = UUID.randomUUID();
        String token = sessions.create(new AuthenticatedUser(id, "multi", "Multi", "MULTI_ROLE", "HOSPITAL_A",
                "PATHOLOGY", "WORKBENCH",
                Set.of("P14-PERM-004", "P14-PERM-014", "P14-PERM-017", "P14-PERM-048"), null, null));
        var body = new ObjectMapper().readTree(mockMvc.perform(get("/api/v2/my-workbench")
                .cookie(new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.path("capabilityQueues").findValuesAsText("key"))
                .contains("REGISTRATION_PENDING", "CYTOLOGY_PRODUCTION", "TECHNICAL_ORDER")
                .doesNotContain("PUBLIC_POOL");
    }
}
