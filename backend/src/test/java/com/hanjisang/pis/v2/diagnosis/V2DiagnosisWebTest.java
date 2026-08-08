package com.hanjisang.pis.v2.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class V2DiagnosisWebTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void claimEditsAndCompletesContinuousDiagnosisResponsibilityChain() throws Exception {
        String caseId = createReadyCase("I03-CHAIN");
        JsonNode publicPool = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/public-pool"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(publicPool.toString()).contains(caseId);
        JsonNode claimed = json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"claim-i03-chain\"}".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String diagnosisId = claimed.get("diagnosisId").asText();
        String responsibilityId = claimed.get("responsibilityId").asText();
        assertThat(claimed.get("role").asText()).isEqualTo("INITIAL");
        JsonNode claimReplay = json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"claim-i03-chain\"}".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(claimReplay.get("duplicate").asBoolean()).isTrue();

        JsonNode saved = json(mockMvc.perform(put("/api/v2/diagnoses/%s/content".formatted(diagnosisId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"structuredData":"{}","microscopicDescription":"synthetic microscopic",
                         "diagnosisText":"synthetic diagnosis","comment":"synthetic comment",
                         "expectedVersion":0,"idempotencyKey":"save-i03-chain"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saved.get("version").asLong()).isEqualTo(1);
        mockMvc.perform(put("/api/v2/diagnoses/%s/content".formatted(diagnosisId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"structuredData":"{}","diagnosisText":"stale edit","expectedVersion":0,
                         "idempotencyKey":"save-i03-stale"}
                        """))
                .andExpect(status().isConflict());

        String reviewId = complete(diagnosisId, "/complete-initial", responsibilityId, 0, 1, "REVIEW", 0,
                "p15-local-registration-actor", "complete-i03-initial").get("nextResponsibilityId").asText();
        String auditId = complete(diagnosisId, "/complete-review", reviewId, 0, 2, "AUDIT", 0,
                "p15-local-registration-actor", "complete-i03-review").get("nextResponsibilityId").asText();
        JsonNode completed = complete(diagnosisId, "/complete-audit", auditId, 0, 3, null, 0, null,
                "complete-i03-audit");
        assertThat(completed.get("readyForSignOut").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.responsibility_unit", Integer.class))
                .isEqualTo(3);

        JsonNode workspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/%s".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("diagnosis").get("diagnosisText").asText()).isEqualTo("synthetic complete diagnosis");
        assertThat(workspace.get("responsibilityChain")).hasSize(3);
        assertThat(workspace.has("currentResponsibility")).isFalse();
        assertThat(workspace.get("actions").get("readyForSignOut").asBoolean()).isTrue();
        assertThat(workspace.get("technicalOrder").get("status").asText()).isEqualTo("V2-I04已实现");
    }

    @Test
    void singleSignUsesInitialAndAuditOnOneContinuousDiagnosis() throws Exception {
        String caseId = createReadyCase("I03-SINGLE-SIGN");
        JsonNode claimed = json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"claim-i03-single\"}".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String diagnosisId = claimed.get("diagnosisId").asText();
        String auditId = complete(diagnosisId, "/complete-initial", claimed.get("responsibilityId").asText(), 0, 0,
                "AUDIT", 0, "p15-local-registration-actor", "complete-i03-single-initial")
                .get("nextResponsibilityId").asText();
        JsonNode completed = complete(diagnosisId, "/complete-audit", auditId, 0, 1, null, 0, null,
                "complete-i03-single-audit");

        assertThat(completed.get("readyForSignOut").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.responsibility_unit", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.responsibility_unit WHERE role_code = 'AUDIT'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void manualAssignmentAndReassignmentPreserveResponsibilityHistory() throws Exception {
        String caseId = createReadyCase("I03-REASSIGN");
        JsonNode assigned = json(mockMvc.perform(post("/api/v2/diagnoses/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","doctorId":"doctor-a","reason":"synthetic manual assignment",
                         "idempotencyKey":"assign-i03-reassign"}
                        """.formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v2/diagnoses/reassign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","doctorId":"doctor-b","reason":"synthetic reassignment",
                         "idempotencyKey":"reassign-i03-001"}
                        """.formatted(caseId)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.responsibility_unit", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.responsibility_unit WHERE ended_at IS NOT NULL",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT doctor_id FROM pis_v2.responsibility_unit WHERE ended_at IS NULL",
                String.class)).isEqualTo("doctor-b");
        assertThat(assigned.get("doctorId").asText()).isEqualTo("doctor-a");
    }

    @Test
    void newPublishedTemplateVersionDoesNotMoveExistingDiagnosisSnapshot() throws Exception {
        String firstCase = createReadyCase("I03-TEMPLATE-A");
        JsonNode first = json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"claim-i03-template-a\"}".formatted(firstCase)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String templateId = jdbcTemplate.queryForObject("SELECT template_id FROM pis_v2.diagnosis_template_version WHERE id = ?",
                String.class, UUID.fromString(jdbcTemplate.queryForObject("SELECT template_version_id FROM pis_v2.diagnosis WHERE id = ?",
                        String.class, UUID.fromString(first.get("diagnosisId").asText()))));
        JsonNode draft = json(mockMvc.perform(post("/api/v2/diagnosis-templates/%s/versions".formatted(templateId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"schemaDefinition":"{}",
                         "idempotencyKey":"template-v2-i03"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v2/diagnosis-template-versions/%s/publish".formatted(draft.get("versionId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"publish-v2-i03\"}"))
                .andExpect(status().isOk());
        String firstVersion = jdbcTemplate.queryForObject("SELECT template_version_id FROM pis_v2.diagnosis WHERE id = ?",
                String.class, UUID.fromString(first.get("diagnosisId").asText()));
        assertThat(firstVersion).isNotEqualTo(draft.get("versionId").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.diagnosis_template_version WHERE status_code = 'PUBLISHED'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentClaimsCreateOneDiagnosisAndOneInitialResponsibility() throws Exception {
        String caseId = createReadyCase("I03-CONCURRENT-CLAIM");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> first = executor.submit(() -> claimStatus(caseId, "claim-i03-concurrent-a", start));
        Future<Integer> second = executor.submit(() -> claimStatus(caseId, "claim-i03-concurrent-b", start));
        start.countDown();
        int firstStatus = first.get();
        int secondStatus = second.get();
        executor.shutdownNow();

        assertThat(java.util.Set.of(firstStatus, secondStatus)).contains(200, 422);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.diagnosis", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.responsibility_unit", Integer.class))
                .isEqualTo(1);
    }

    private int claimStatus(String caseId, String key, CountDownLatch start) throws Exception {
        start.await();
        return mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"%s\"}".formatted(caseId, key)))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode complete(String diagnosisId, String path, String responsibilityId, long responsibilityVersion,
            long diagnosisVersion, String nextRole, long ignored, String nextDoctorId, String key) throws Exception {
        String nextRoleJson = nextRole == null ? "null" : "\"%s\"".formatted(nextRole);
        String nextDoctorJson = nextDoctorId == null ? "null" : "\"%s\"".formatted(nextDoctorId);
        return json(mockMvc.perform(post("/api/v2/diagnoses/%s%s".formatted(diagnosisId, path))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"responsibilityId":"%s","responsibilityExpectedVersion":%d,
                         "structuredData":"{}","microscopicDescription":"synthetic complete",
                         "diagnosisText":"synthetic complete diagnosis","comment":"synthetic",
                        "diagnosisExpectedVersion":%d,"nextRole":%s,"nextDoctorId":%s,
                         "nextReason":"synthetic next responsibility","idempotencyKey":"%s"}
                        """.formatted(responsibilityId, responsibilityVersion, diagnosisVersion, nextRoleJson,
                        nextDoctorJson, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String createReadyCase(String suffix) throws Exception {
        JsonNode caseBody = json(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                         "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-%s",
                         "visitReference":"SYNTH-VISIT","idempotencyKey":"case-%s"}
                        """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String caseId = caseBody.get("caseId").asText();
        JsonNode specimen = json(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","specimenCode":"A","specimenKindCode":"TISSUE","sourceKindCode":"LOCAL",
                         "sourceReference":"SYNTH-%s","collectionSite":"synthetic site","collectionMethodCode":"SURGICAL",
                         "labelCode":"LABEL-%s","idempotencyKey":"specimen-%s"}
                        """.formatted(caseId, suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode grossing = json(mockMvc.perform(post("/api/v2/cases/%s/grossings".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceType":"INITIAL","grossDescription":"synthetic grossing","grossingDoctorId":"doctor-gross",
                         "recorderId":"recorder","idempotencyKey":"grossing-%s"}
                        """.formatted(suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v2/grossings/%s/specimens".formatted(grossing.get("grossingId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"materialDescription\":\"synthetic\",\"idempotencyKey\":\"associate-%s\"}"
                        .formatted(specimen.get("specimenId").asText(), suffix)))
                .andExpect(status().isOk());
        JsonNode block = json(mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossing.get("grossingId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"A1\",\"blockType\":\"ROUTINE\",\"idempotencyKey\":\"block-%s\"}"
                        .formatted(specimen.get("specimenId").asText(), suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v2/grossings/%s/complete".formatted(grossing.get("grossingId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-grossing-%s\"}".formatted(suffix)))
                .andExpect(status().isOk());
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(block.get("blockId").asText()));
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-slide-%s\"}".formatted(suffix)))
                .andExpect(status().isOk());
        return caseId;
    }

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }
}
