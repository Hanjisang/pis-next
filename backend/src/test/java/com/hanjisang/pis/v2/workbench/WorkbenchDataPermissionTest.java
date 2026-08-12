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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;

@SpringBootTest(properties = "pis.require-auth=true")
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class WorkbenchDataPermissionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationSessionStore sessions;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
    }

    @Test
    void hospitalBCannotSeeHospitalAApplication() throws Exception {
        UUID application = UUID.randomUUID();
        jdbc.update("INSERT INTO pis_v2.pathology_application (id,application_no,source_type_code,source_system_code,patient_reference,status_code,organization_reference,concurrency_version,applied_at,created_at,created_by_ref,updated_at,updated_by_ref) VALUES (?,?,?,?,?,'RECEIVED','HOSPITAL_A',0,?,?,?,?,?)",
                application, "A-PRIVATE", "MANUAL", "TEST", "SYNTH-A", Instant.now(), Instant.now(), "TEST",
                Instant.now(), "TEST");
        jdbc.update("INSERT INTO pis_v2.pathology_application_item (id,application_id,external_item_code,item_name,sequence_no,status_code,created_at,created_by_ref) VALUES (?,?,?,?,1,'PENDING',?,'TEST')",
                UUID.randomUUID(), application, "SYNTH-HISTOLOGY", "A院项目", Instant.now());
        UUID userId = UUID.randomUUID();
        String token = sessions.create(new AuthenticatedUser(userId, "hospital-b", "Hospital B", "REGISTRAR",
                "HOSPITAL_B", "PATHOLOGY", "WORKBENCH", Set.of("P14-PERM-004", "P14-PERM-048"), null, null));
        String body = mockMvc.perform(get("/api/v2/my-workbench")
                .cookie(new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(new ObjectMapper().readTree(body).toString()).doesNotContain("A-PRIVATE", "SYNTH-A");
    }
}
