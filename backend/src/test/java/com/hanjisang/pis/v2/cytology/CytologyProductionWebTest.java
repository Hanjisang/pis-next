package com.hanjisang.pis.v2.cytology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class CytologyProductionWebTest {

    private static final String CYTOLOGY_MAPPING = "SYNTH-CYTOLOGY";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void zeroSlideMultiSpecimenCaseGeneratesIdempotentlyCompletesAndLeavesQueue() throws Exception {
        String caseId = createCase("CY-003", "cytology-multi-case");
        String firstSpecimenId = createSpecimen(caseId, "1", "cytology-specimen-1");
        String secondSpecimenId = createSpecimen(caseId, "2", "cytology-specimen-2");

        JsonNode pending = cytologyQueueItem(caseId);
        assertThat(pending.path("requiredCount").asInt()).isEqualTo(2);
        assertThat(pending.path("completedCount").asInt()).isZero();
        assertThat(pending.path("deepLink").asText()).contains("CYTOLOGY_PRODUCTION");

        JsonNode generated = json(mockMvc.perform(post("/api/v2/cases/%s/cytology-slides/generate".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"specimenIds":["%s","%s"],"slideType":"CYTOLOGY","stainCode":"PAP",
                         "idempotencyKey":"cytology-generate-1"}
                        """.formatted(firstSpecimenId, secondSpecimenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(generated.path("createdCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE case_id = ?", Integer.class,
                UUID.fromString(caseId))).isZero();

        JsonNode repeated = json(mockMvc.perform(post("/api/v2/cases/%s/cytology-slides/generate".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"specimenIds":["%s","%s"],"slideType":"CYTOLOGY","stainCode":"PAP",
                         "idempotencyKey":"cytology-generate-2"}
                        """.formatted(firstSpecimenId, secondSpecimenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(repeated.path("createdCount").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE case_id = ?", Integer.class,
                UUID.fromString(caseId))).isEqualTo(2);

        JsonNode tree = materials(caseId);
        assertThat(tree.path("specimens")).hasSize(2);
        assertThat(tree.path("specimens").get(0).path("blocks")).isEmpty();
        assertThat(tree.path("specimens").get(0).path("directSlides").get(0).path("stainCode").asText())
                .isEqualTo("PAP");

        JsonNode firstSlide = generated.path("slides").get(0);
        JsonNode secondSlide = generated.path("slides").get(1);
        assertThat(jdbc.queryForObject("SELECT block_id IS NULL FROM pis_v2.slide WHERE id = ?", Boolean.class,
                UUID.fromString(firstSlide.path("slideId").asText()))).isTrue();
        assertThat(firstSlide.path("specimenId").asText()).isNotBlank();

        mockMvc.perform(put("/api/v2/cases/%s/specimens/%s/cytology-preparation".formatted(caseId, firstSpecimenId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preparationMethodCode\":\"LIQUID_BASED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        assertThat(materials(caseId).path("specimens").findValue("preparationMethodCode").asText())
                .isEqualTo("LIQUID_BASED");

        mockMvc.perform(post("/api/v2/slides/print-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slideIds":["%s","%s"],"reason":"initial labels",
                         "idempotencyKey":"cytology-print-batch"}
                        """.formatted(firstSlide.path("slideId").asText(), secondSlide.path("slideId").asText())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/slides/%s/print".formatted(firstSlide.path("slideId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"reprint\",\"idempotencyKey\":\"cytology-reprint\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.print_log WHERE entity_kind_code = 'SLIDE'", Integer.class))
                .isEqualTo(3);

        completeSlide(firstSlide);
        JsonNode partial = cytologyQueueItem(caseId);
        assertThat(partial.path("completedCount").asInt()).isEqualTo(1);
        assertThat(partial.path("requiredCount").asInt()).isEqualTo(2);

        completeSlide(secondSlide);
        JsonNode after = productionWorkbench();
        assertThat(after.path("queues").path("cytologyProduction").path("count").asInt()).isZero();
        assertThat(materials(caseId).path("initialProductionComplete").asBoolean()).isTrue();

        JsonNode claimed = json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"cytology-diagnosis-claim\"}".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String diagnosisId = claimed.path("diagnosisId").asText();
        JsonNode diagnosisWorkspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/{caseId}", caseId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(diagnosisWorkspace.path("templateVersion").path("schemaDefinition").asText())
                .contains("CYTOLOGY-NON-GYN-STRUCTURED", "specimenAdequacy", "diagnosticCategory");
        mockMvc.perform(post("/api/v2/diagnoses/{diagnosisId}/complete-initial", diagnosisId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"responsibilityId":"%s","responsibilityExpectedVersion":0,"structuredData":"{}",
                         "microscopicDescription":"合成镜下所见","diagnosisText":"合成细胞学诊断",
                         "diagnosisExpectedVersion":0,"nextRole":"AUDIT",
                         "nextDoctorId":"p15-local-registration-actor","nextReason":"合成提交审核",
                         "idempotencyKey":"cytology-incomplete-initial"}
                        """.formatted(claimed.path("responsibilityId").asText())))
                .andExpect(status().isUnprocessableEntity());
        String structured = """
                {"specimenType":"BODY_FLUID","specimenAdequacy":"SATISFACTORY",
                 "diagnosticCategory":"NEGATIVE","interpretationResult":"合成解释结果",
                 "microscopicDescription":"合成镜下所见","diagnosisText":"合成细胞学诊断"}
                """;
        JsonNode initial = completeDiagnosis(diagnosisId, "complete-initial",
                claimed.path("responsibilityId").asText(), 0, structured, 0, "AUDIT",
                "cytology-complete-initial");
        completeDiagnosis(diagnosisId, "complete-audit", initial.path("nextResponsibilityId").asText(), 0,
                structured, 1, null, "cytology-complete-audit");
        JsonNode preview = json(mockMvc.perform(get("/api/v2/diagnoses/{diagnosisId}/report-preview", diagnosisId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.path("valid").asBoolean()).isTrue();
        assertThat(preview.path("renderedContent").asText()).contains("非妇科细胞学报告", "diagnosticCategory");
        JsonNode report = json(mockMvc.perform(post("/api/v2/diagnoses/{diagnosisId}/sign-out", diagnosisId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"cytology-report-sign\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(report.path("status").asText()).isEqualTo("EFFECTIVE");
        assertThat(report.path("templateVersionId").asText())
                .isEqualTo("00000000-0000-0000-0000-00000000b504");
        assertThat(jdbc.queryForObject("SELECT diagnosis_snapshot FROM pis_v2.report WHERE id = ?", String.class,
                UUID.fromString(report.path("reportId").asText()))).contains("diagnosticCategory", "NEGATIVE");
    }

    @Test
    void configuredMultipleCytologyRulesCreateStableSpecimenSlideCodes() throws Exception {
        insertCytologyRule("00000000-0000-0000-0000-00000000b201", "CYTOLOGY-HE", "HE");
        insertCytologyRule("00000000-0000-0000-0000-00000000b202", "CYTOLOGY-PAP", "PAP");
        String caseId = createCase("CY-004", "cytology-rule-case");
        String specimenId = createSpecimen(caseId, "1", "cytology-rule-specimen");

        assertThat(cytologyQueueItem(caseId).path("requiredCount").asInt()).isEqualTo(2);
        JsonNode generated = json(mockMvc.perform(post("/api/v2/cases/%s/cytology-slides/generate".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"specimenIds":["%s"],"idempotencyKey":"cytology-rule-generate"}
                        """.formatted(specimenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(generated.path("createdCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT slide_code FROM pis_v2.slide WHERE specimen_id = ? AND rule_code = ?",
                String.class, UUID.fromString(specimenId), "CYTOLOGY-HE")).isEqualTo("1-1");
        assertThat(jdbc.queryForObject("SELECT stain_code FROM pis_v2.slide WHERE specimen_id = ? AND rule_code = ?",
                String.class, UUID.fromString(specimenId), "CYTOLOGY-PAP")).isEqualTo("PAP");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE specimen_id = ?", Integer.class,
                UUID.fromString(specimenId))).isEqualTo(2);

        JsonNode repeated = json(mockMvc.perform(post("/api/v2/cases/%s/cytology-slides/generate".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"specimenIds":["%s"],"idempotencyKey":"cytology-rule-generate-again"}
                        """.formatted(specimenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(repeated.path("createdCount").asInt()).isZero();
    }

    private void completeSlide(JsonNode slide) throws Exception {
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slide.path("slideId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-" + slide.path("slideId").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode completeDiagnosis(String diagnosisId, String action, String responsibilityId,
            long responsibilityVersion, String structuredData, long diagnosisVersion, String nextRole,
            String idempotencyKey) throws Exception {
        String nextRoleValue = nextRole == null ? "null" : "\"" + nextRole + "\"";
        String nextDoctorValue = nextRole == null ? "null" : "\"p15-local-registration-actor\"";
        return json(mockMvc.perform(post("/api/v2/diagnoses/{diagnosisId}/{action}", diagnosisId, action)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"responsibilityId":"%s","responsibilityExpectedVersion":%d,
                         "structuredData":%s,"microscopicDescription":"合成镜下所见",
                         "diagnosisText":"合成细胞学诊断","comment":"合成备注",
                         "diagnosisExpectedVersion":%d,"nextRole":%s,"nextDoctorId":%s,
                         "nextReason":"合成责任流转","idempotencyKey":"%s"}
                        """.formatted(responsibilityId, responsibilityVersion,
                        objectMapper.writeValueAsString(structuredData), diagnosisVersion, nextRoleValue,
                        nextDoctorValue, idempotencyKey)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode cytologyQueueItem(String caseId) throws Exception {
        JsonNode items = productionWorkbench().path("queues").path("cytologyProduction").path("items");
        for (JsonNode item : items) {
            if (caseId.equals(item.path("caseId").asText())) return item;
        }
        throw new AssertionError("Cytology case is not in the production queue: " + caseId);
    }

    private JsonNode productionWorkbench() throws Exception {
        return json(mockMvc.perform(get("/api/v2/production-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode materials(String caseId) throws Exception {
        return json(mockMvc.perform(get("/api/v2/cases/%s/materials".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String createCase(String applicationId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                         "applicationItemCode":"%s","patientReference":"SYNTH-PATIENT-%s",
                         "visitReference":"SYNTH-VISIT-%s","idempotencyKey":"%s"}
                        """.formatted(applicationId, CYTOLOGY_MAPPING, applicationId, applicationId, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("caseId").asText();
    }

    private String createSpecimen(String caseId, String code, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/specimens").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","specimenCode":"%s","specimenKindCode":"FLUID",
                         "sourceKindCode":"LOCAL","sourceReference":"SYNTH-%s",
                         "collectionSite":"synthetic site","collectionMethodCode":"COLLECTED",
                         "idempotencyKey":"%s"}
                        """.formatted(caseId, code, key, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("specimenId").asText();
    }

    private void insertCytologyRule(String id, String ruleCode, String stainCode) {
        jdbc.update("""
                INSERT INTO pis_v2.slide_rule
                    (id, organization_reference, business_type_id, rule_code, source_context_type, trigger_code,
                     slide_type, stain_code, copies, active, configuration_version, created_at, updated_at, created_by_ref)
                VALUES (?, 'LOCAL_HOSPITAL', '00000000-0000-0000-0000-00000000b102', ?, 'CYTOLOGY', 'MANUAL',
                        'CYTOLOGY', ?, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.fromString(id), ruleCode, stainCode);
    }

    private JsonNode json(String body) throws Exception { return objectMapper.readTree(body); }
}
