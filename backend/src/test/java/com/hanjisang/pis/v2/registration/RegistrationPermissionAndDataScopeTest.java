package com.hanjisang.pis.v2.registration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;

@SpringBootTest(properties = "pis.require-auth=true")
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class RegistrationPermissionAndDataScopeTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationSessionStore sessions;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private UUID applicationId;
    private UUID itemId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
        applicationId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'", UUID.class);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_application
                    (id, application_no, source_type_code, source_system_code, patient_reference, patient_name,
                     visit_reference, visit_type_code, application_department, applicant_reference,
                     status_code, organization_reference, concurrency_version, applied_at,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'MANUAL', 'TEST', 'SYNTH-PATIENT-A', 'Synthetic Patient A',
                        'MZ-A', 'OUTPATIENT', 'DEPT-A', 'DOCTOR-A',
                        'PARTIALLY_REGISTERED', 'HOSPITAL_A', 0, ?, ?, 'TEST', ?, 'TEST')
                """, applicationId, "APP-SCOPE-" + applicationId, now, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_application_item
                    (id, application_id, external_item_code, item_name, business_type_id,
                     specimen_kind_code, sequence_no, status_code, created_at, created_by_ref)
                VALUES (?, ?, 'SYNTH-HISTOLOGY', 'Synthetic histology', ?, 'TISSUE',
                        1, 'REGISTERED', ?, 'TEST')
                """, itemId, applicationId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'TEST', ?, 'SYNTH-HISTOLOGY', ?, 'ACTIVE', TRUE, 0,
                        'HOSPITAL_A', ?, 'TEST')
                """, caseId, "P-SCOPE-" + caseId, "APP-SCOPE-" + applicationId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.case_context_snapshot
                    (id, case_id, patient_reference, visit_reference, snapshot_version_no, captured_at, captured_by_ref)
                VALUES (?, ?, 'SYNTH-PATIENT-A', 'MZ-A', 1, ?, 'TEST')
                """, UUID.randomUUID(), caseId, now);
        jdbc.update("""
                INSERT INTO pis_v2.pathology_application_case
                    (id, application_id, application_item_id, case_id, linked_at, linked_by_ref)
                VALUES (?, ?, ?, ?, ?, 'TEST')
                """, UUID.randomUUID(), applicationId, itemId, caseId, now);
    }

    @Test
    void ordinaryUserCannotCallRegistrationCorrectionOrCancellationWriteApis() throws Exception {
        Cookie ordinary = cookie("ordinary", "HOSPITAL_A", Set.of("P14-PERM-048"));
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/pathology-number", caseId)
                .cookie(ordinary).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newPathologyNo":"P-FORBIDDEN","reason":"not authorized","expectedVersion":0}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/cancel", caseId)
                .cookie(ordinary).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"not authorized","expectedVersion":0}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v2/applications/{applicationId}/items/{itemId}/register",
                applicationId, itemId).cookie(ordinary).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hospitalBRegistrarCannotReadModifyOrRegisterHospitalAApplication() throws Exception {
        Cookie hospitalB = cookie("registrar-b", "HOSPITAL_B",
                Set.of("P14-PERM-003", "P14-PERM-004", "P14-PERM-007", "P14-PERM-048"));
        mockMvc.perform(get("/api/v2/applications/{applicationId}", applicationId).cookie(hospitalB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/applications/{applicationId}/items/{itemId}/register",
                applicationId, itemId).cookie(hospitalB).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/pathology-number", caseId)
                .cookie(hospitalB).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newPathologyNo":"P-CROSS-SCOPE","reason":"cross scope","expectedVersion":0}
                        """))
                .andExpect(status().isNotFound());
    }

    private Cookie cookie(String username, String hospital, Set<String> permissions) {
        String token = sessions.create(new AuthenticatedUser(UUID.randomUUID(), username, username, "TEST",
                hospital, "PATHOLOGY", "WORKBENCH", permissions, null, null));
        return new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token);
    }
}
