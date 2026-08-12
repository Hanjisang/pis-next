package com.hanjisang.pis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "pis.require-auth=true")
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class V2AuthPasswordWebTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthIdentityRepository identities;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
        seedAccount("registrar", Set.of("P14-PERM-004", "P14-PERM-048"));
        seedAccount("doctor", Set.of("P14-PERM-034", "P14-PERM-048"));
        seedAccount("technician", Set.of("P14-PERM-014", "P14-PERM-048"));
        seedAccount("admin", Set.of("P14-PERM-001", "P14-PERM-048"));
    }

    @Test
    void userChangesOwnPasswordAndOtherSessionsAreRevoked() throws Exception {
        Cookie first = login("registrar", "123456");
        Cookie second = login("registrar", "123456");
        mockMvc.perform(post("/api/v2/auth/password").cookie(first).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"123456\",\"newPassword\":\"NewPass-2026\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/auth/me").cookie(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        login("registrar", "NewPass-2026");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE operation_code = 'PIS-V2-AUTH-PASSWORD-CHANGE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void wrongOldWeakAndUnchangedPasswordsAreRejected() throws Exception {
        Cookie cookie = login("doctor", "123456");
        mockMvc.perform(post("/api/v2/auth/password").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"ValidPass-2026\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("V2-AUTH-PASSWORD-INVALID"));
        mockMvc.perform(post("/api/v2/auth/password").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"123456\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("V2-AUTH-PASSWORD-WEAK"));
        mockMvc.perform(post("/api/v2/auth/password").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"same-password\",\"newPassword\":\"same-password\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("V2-AUTH-PASSWORD-UNCHANGED"));
    }

    @Test
    void onlyAuthorizedAdministratorResetsAnotherUserPassword() throws Exception {
        String targetId = identities.authenticate("technician", "123456").orElseThrow().userId().toString();
        mockMvc.perform(post("/api/v2/auth/users/{id}/password-reset", targetId)
                .cookie(login("registrar", "123456")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"ResetPass-2026\"}"))
                .andExpect(status().isForbidden());
        Cookie targetSession = login("technician", "123456");
        mockMvc.perform(post("/api/v2/auth/users/{id}/password-reset", targetId)
                .cookie(login("admin", "123456")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"ResetPass-2026\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/auth/me").cookie(targetSession)).andExpect(status().isUnauthorized());
        login("technician", "ResetPass-2026");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE operation_code = 'PIS-V2-AUTH-PASSWORD-RESET'", Integer.class)).isEqualTo(1);
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v2/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).isNotBlank();
        return new Cookie(AuthenticationSessionFilter.COOKIE_NAME, header.split(";", 2)[0].split("=", 2)[1]);
    }

    private void seedAccount(String username, java.util.Set<String> permissions) {
        UUID id = jdbc.query("SELECT id FROM pis_v2.auth_user WHERE username = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : UUID.randomUUID(), username);
        jdbc.update("""
                MERGE INTO pis_v2.auth_user
                    (id, username, display_name, password_digest, role_code, hospital_scope, department_scope,
                     task_scope, enabled, created_at, updated_at, hospital_profile_id, campus_id, department_id)
                KEY(username) VALUES (?, ?, ?, ?, ?, 'LOCAL_HOSPITAL', 'PATHOLOGY', 'ADMINISTRATION', TRUE,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL, NULL)
                """, id, username, username, PasswordHash.create("123456"),
                username.equals("admin") ? "ADMIN" : "USER");
        jdbc.update("DELETE FROM pis_v2.auth_user_permission WHERE user_id = ?", id);
        permissions.forEach(permission -> jdbc.update(
                "INSERT INTO pis_v2.auth_user_permission (user_id, permission_code) VALUES (?, ?)", id, permission));
    }
}
