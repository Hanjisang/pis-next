package com.hanjisang.pis.v2.material;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
abstract class GrossingSecurityTestSupport {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationSessionStore sessions;
    @Autowired protected JdbcTemplate jdbc;
    protected MockMvc mockMvc;
    protected UUID caseId;
    protected UUID specimenId;

    @BeforeEach
    void setUpSecurity() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
        caseId = UUID.randomUUID();
        specimenId = UUID.randomUUID();
        UUID businessTypeId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.business_type WHERE business_type_code = 'HISTOLOGY'", UUID.class);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO pis_v2.pathology_case
                    (id, case_no, source_system_code, external_application_id, application_item_code,
                     business_type_id, lifecycle_state_code, number_binding_active, concurrency_version,
                     organization_reference, created_at, created_by_ref)
                VALUES (?, ?, 'TEST', 'GROSSING-SCOPE', 'SYNTH-HISTOLOGY', ?, 'ACTIVE', TRUE, 0,
                        'HOSPITAL_A', ?, 'TEST')
                """, caseId, "G-SCOPE-" + caseId, businessTypeId, now);
        jdbc.update("""
                INSERT INTO pis_v2.case_context_snapshot
                    (id, case_id, patient_reference, visit_reference, snapshot_version_no, captured_at, captured_by_ref)
                VALUES (?, ?, 'SYNTH-PATIENT', 'SYNTH-VISIT', 1, ?, 'TEST')
                """, UUID.randomUUID(), caseId, now);
        jdbc.update("""
                INSERT INTO pis_v2.specimen
                    (id, case_id, specimen_no, specimen_code, specimen_name, specimen_kind_code,
                     creation_source_code, source_kind_code, source_reference, quantity_value, quantity_unit_code,
                     label_code, concurrency_version, organization_reference,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, 'SCOPE-S1', 'SCOPE-S1', '数据范围合成组织', 'TISSUE',
                        'REGISTRATION', 'LOCAL', 'SYNTH-SCOPE', 1, '件', 'LBL-SCOPE', 0, 'HOSPITAL_A',
                        ?, 'TEST', ?, 'TEST')
                """, specimenId, caseId, now, now);
    }

    protected Cookie cookie(String username, String hospital, Set<String> permissions) {
        String token = sessions.create(new AuthenticatedUser(UUID.randomUUID(), username, username, "TEST",
                hospital, "PATHOLOGY", "WORKBENCH", permissions, null, null));
        return new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token);
    }
}
