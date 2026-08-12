package com.hanjisang.pis.v2.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;

@SpringBootTest(properties = "pis.require-auth=true")
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class WorkbenchPermissionAndDataScopeTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationSessionStore sessions;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
    }

    @Test
    void queuesArePermissionShapedAndMultipleCapabilitiesAreUnioned() throws Exception {
        Cookie registrar = cookie("registrar", "HOSPITAL_A", Set.of("P14-PERM-004", "P14-PERM-048"));
        Cookie multi = cookie("multi", "HOSPITAL_A",
                Set.of("P14-PERM-004", "P14-PERM-014", "P14-PERM-017", "P14-PERM-048"));

        assertThat(queueKeys(registrar)).contains("REGISTRATION_PENDING").doesNotContain("CYTOLOGY_PRODUCTION");
        assertThat(queueKeys(multi)).contains("REGISTRATION_PENDING", "CYTOLOGY_PRODUCTION", "TECHNICAL_ORDER")
                .doesNotContain("PUBLIC_POOL");
    }

    @Test
    void hospitalScopeHidesApplicationsFromAnotherHospital() throws Exception {
        UUID application = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        jdbc.update("INSERT INTO pis_v2.pathology_application (id,application_no,source_type_code,source_system_code,patient_reference,status_code,organization_reference,concurrency_version,applied_at,created_at,created_by_ref,updated_at,updated_by_ref) VALUES (?,?,?,?,?,'RECEIVED','HOSPITAL_A',0,?,?,?,?,?)",
                application, "A-ONLY", "MANUAL", "TEST", "SYNTH-A", Instant.now(), Instant.now(), "TEST",
                Instant.now(), "TEST");
        jdbc.update("INSERT INTO pis_v2.pathology_application_item (id,application_id,external_item_code,item_name,sequence_no,status_code,created_at,created_by_ref) VALUES (?,?,?,?,1,'PENDING',?,'TEST')",
                item, application, "SYNTH-HISTOLOGY", "A院项目", Instant.now());

        JsonNode body = body(cookie("hospital-b", "HOSPITAL_B", Set.of("P14-PERM-004", "P14-PERM-048")));
        assertThat(body.toString()).doesNotContain("A-ONLY", "SYNTH-A");
    }

    private java.util.List<String> queueKeys(Cookie cookie) throws Exception {
        return body(cookie).path("capabilityQueues").findValuesAsText("key");
    }

    private JsonNode body(Cookie cookie) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v2/my-workbench").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Cookie cookie(String username, String hospital, Set<String> permissions) {
        UUID id = UUID.randomUUID();
        String token = sessions.create(new AuthenticatedUser(id, username, username, "TEST", hospital, "PATHOLOGY",
                "WORKBENCH", permissions, null, null));
        return new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token);
    }
}
