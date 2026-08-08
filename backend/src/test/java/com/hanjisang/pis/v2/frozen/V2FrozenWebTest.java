package com.hanjisang.pis.v2.frozen;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class V2FrozenWebTest {

    private static final UUID FROZEN_TYPE = UUID.fromString("00000000-0000-0000-0000-00000000c101");
    private static final UUID FROZEN_TEMPLATE = UUID.fromString("00000000-0000-0000-0000-00000000c102");
    private static final UUID FROZEN_TEMPLATE_VERSION = UUID.fromString("00000000-0000-0000-0000-00000000c103");
    private static final UUID FROZEN_REPORT_TEMPLATE = UUID.fromString("00000000-0000-0000-0000-00000000c104");
    private static final UUID FROZEN_REPORT_VERSION = UUID.fromString("00000000-0000-0000-0000-00000000c105");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        seedFrozenConfiguration();
    }

    @Test
    void frozenRoundMaterialDiagnosisReportAndEndCreateOneRoutineCase() throws Exception {
        String caseId = createFrozenCase();
        JsonNode opened = json(mockMvc.perform(post("/api/v2/frozen/cases/%s/rounds".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"frozen-round-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String roundId = opened.get("roundId").asText();
        String specimenId = json(mockMvc.perform(post("/api/v2/frozen/cases/%s/specimens".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"specimenCode":"F-A","specimenKindCode":"TISSUE","collectionSite":"synthetic frozen site",
                         "collectionMethodCode":"FROZEN","idempotencyKey":"frozen-specimen-1"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("specimenIds").get(0).asText();

        String grossingId = json(mockMvc.perform(post("/api/v2/cases/%s/grossings".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceType":"FROZEN_CONTEXT","sourceReferenceId":"%s","grossDescription":"synthetic frozen grossing",
                         "grossingInstruction":"synthetic","grossingDoctorId":"p15-local-registration-actor",
                         "recorderId":"p15-local-registration-actor","idempotencyKey":"frozen-grossing-1"}
                        """.formatted(roundId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("grossingId").asText();
        mockMvc.perform(post("/api/v2/grossings/%s/specimens".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"materialDescription\":\"synthetic\",\"idempotencyKey\":\"frozen-associate-1\"}"
                        .formatted(specimenId))).andExpect(status().isOk());
        String blockId = json(mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"F-A1\",\"blockType\":\"FROZEN\",\"idempotencyKey\":\"frozen-block-1\"}"
                        .formatted(specimenId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("blockId").asText();
        JsonNode completion = json(mockMvc.perform(post("/api/v2/grossings/%s/complete".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"frozen-grossing-complete-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(completion.get("createdSlideCount").asInt()).isEqualTo(1);
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(blockId));
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slideId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"frozen-slide-complete-1\"}"))
                .andExpect(status().isOk());

        String diagnosisId = json(mockMvc.perform(post("/api/v2/frozen/rounds/%s/diagnosis".formatted(roundId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"frozen-diagnosis-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("diagnosisId").asText();
        mockMvc.perform(put("/api/v2/diagnoses/%s/content".formatted(diagnosisId)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"structuredData":"{}","diagnosisText":"synthetic frozen diagnosis",
                         "expectedVersion":0,"idempotencyKey":"frozen-diagnosis-save-1"}"""))
                .andExpect(status().isOk());
        JsonNode diagnosisWorkspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/frozen-rounds/%s".formatted(roundId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(diagnosisWorkspace.get("diagnosis").get("diagnosisId").asText()).isEqualTo(diagnosisId);
        JsonNode initial = complete(diagnosisId, "/complete-initial", "AUDIT", "frozen-complete-initial-1");
        complete(diagnosisId, "/complete-audit", null, "frozen-complete-audit-1");
        JsonNode report = json(mockMvc.perform(post("/api/v2/diagnoses/%s/sign-out".formatted(diagnosisId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"frozen-sign-out-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(report.get("reportNo").asText()).startsWith("R");

        JsonNode workspace = json(mockMvc.perform(get("/api/v2/frozen/cases/%s/workspace".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("rounds").get(0).get("status").asText()).isEqualTo("SIGNED");
        assertThat(workspace.get("rounds").get(0).get("productionComplete").asBoolean()).isTrue();

        JsonNode ended = json(mockMvc.perform(post("/api/v2/frozen/cases/%s/finish".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"frozen-end-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID routineCaseId = UUID.fromString(ended.get("routineCaseId").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.frozen_end WHERE frozen_case_id = ?",
                Integer.class, UUID.fromString(caseId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT frozen_source_case_id FROM pis_v2.pathology_case WHERE id = ?",
                UUID.class, routineCaseId)).isEqualTo(UUID.fromString(caseId));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE case_id = ? AND specimen_code = 'FROZEN-REMAINDER'",
                Integer.class, routineCaseId)).isEqualTo(1);
        JsonNode replay = json(mockMvc.perform(post("/api/v2/frozen/cases/%s/finish".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"frozen-end-1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(replay.get("duplicate").asBoolean()).isTrue();
        assertThat(initial.get("nextResponsibilityId").asText()).isNotBlank();
    }

    private JsonNode complete(String diagnosisId, String path, String nextRole, String key) throws Exception {
        String body = """
                {"responsibilityId":null,"responsibilityExpectedVersion":0,"structuredData":"{}",
                "diagnosisText":"synthetic frozen diagnosis","diagnosisExpectedVersion":1,"nextRole":%s,
                "nextDoctorId":"p15-local-registration-actor","nextReason":"synthetic","idempotencyKey":"%s"}"""
                .formatted(nextRole == null ? "null" : "\"" + nextRole + "\"", key);
        if ("/complete-initial".equals(path)) {
            body = body.replace("\"responsibilityId\":null", "\"responsibilityId\":\""
                    + jdbcTemplate.queryForObject("SELECT id FROM pis_v2.responsibility_unit WHERE role_code = 'INITIAL'",
                            UUID.class) + "\"");
        } else {
            body = body.replace("\"responsibilityId\":null", "\"responsibilityId\":\""
                    + jdbcTemplate.queryForObject("SELECT id FROM pis_v2.responsibility_unit WHERE role_code = 'AUDIT'",
                            UUID.class) + "\"");
            body = body.replace("\"diagnosisExpectedVersion\":1", "\"diagnosisExpectedVersion\":2");
        }
        MvcResult result = mockMvc.perform(post("/api/v2/diagnoses/%s%s".formatted(diagnosisId, path))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createFrozenCase() throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"FROZEN-CASE-1",
                         "applicationItemCode":"SYNTH-FROZEN","patientReference":"SYNTH-FROZEN-PATIENT",
                         "visitReference":"SYNTH-FROZEN-VISIT","idempotencyKey":"frozen-case-1"}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("caseId").asText();
    }

    private void seedFrozenConfiguration() {
        jdbcTemplate.update("""
                MERGE INTO pis_v2.business_type (id,business_type_code,display_name,modality_code,active,configuration_version,created_at,created_by_ref)
                KEY (business_type_code) VALUES (?, 'FROZEN', '冰冻病理', 'FROZEN', TRUE, 1, CURRENT_TIMESTAMP, 'TEST')
                """, FROZEN_TYPE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.application_item_mapping (id,application_item_code,business_type_id,default_specimen_kind_code,required,sequence_no,active,configuration_version,created_at,created_by_ref)
                KEY (application_item_code) VALUES (?, 'SYNTH-FROZEN', ?, 'TISSUE', TRUE, 1, TRUE, 1, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID(), FROZEN_TYPE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.pathology_number_rule (id,business_type_id,organization_reference,number_kind_code,prefix,scope_code,padding_width,next_serial,active,configuration_version,created_at,updated_at,created_by_ref)
                KEY (organization_reference,business_type_id,number_kind_code) VALUES (?, ?, 'LOCAL_HOSPITAL', 'CASE', 'F-', 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
                (?, ?, 'LOCAL_HOSPITAL', 'SPECIMEN', 'FS-', 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID(), FROZEN_TYPE, UUID.randomUUID(), FROZEN_TYPE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.slide_rule (id,organization_reference,business_type_id,rule_code,source_context_type,trigger_code,slide_type,stain_code,copies,active,configuration_version,created_at,updated_at,created_by_ref)
                KEY (organization_reference,business_type_id,rule_code) VALUES (?, 'LOCAL_HOSPITAL', ?, 'FROZEN-HE', 'FROZEN_ROUND', 'ON_GROSSING_COMPLETE', 'FROZEN-HE', 'HE', 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID(), FROZEN_TYPE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.diagnosis_template (id,organization_reference,template_code,template_name,business_type_id,scope_code,enabled,concurrency_version,created_at,created_by_ref,updated_at,updated_by_ref)
                KEY (id) VALUES (?, 'LOCAL_HOSPITAL', 'DEFAULT-FROZEN', '冰冻诊断模板', ?, 'LOCAL_HOSPITAL', TRUE, 0, CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST')
                """, FROZEN_TEMPLATE, FROZEN_TYPE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.diagnosis_template_version (id,template_id,version_no,schema_definition,status_code,published_at,published_by_ref,created_at,created_by_ref,concurrency_version)
                KEY (id) VALUES (?, ?, 1, '{"components":[]}', 'PUBLISHED', CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST', 0)
                """, FROZEN_TEMPLATE_VERSION, FROZEN_TEMPLATE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.report_template (id,organization_reference,business_type_id,template_code,template_name,enabled,configuration_version,created_at,created_by_ref,updated_at,updated_by_ref)
                KEY (id) VALUES (?, 'LOCAL_HOSPITAL', ?, 'DEFAULT-REPORT-FROZEN', '冰冻报告模板', TRUE, 1, CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST')
                """, FROZEN_REPORT_TEMPLATE, FROZEN_TYPE);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.report_template_version (id,template_id,version_no,definition,status_code,published_at,published_by_ref,created_at,created_by_ref,concurrency_version)
                KEY (id) VALUES (?, ?, 1, '{"sections":["DIAGNOSIS"]}', 'PUBLISHED', CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST', 0)
                """, FROZEN_REPORT_VERSION, FROZEN_REPORT_TEMPLATE);
    }

    private JsonNode json(String body) throws Exception { return objectMapper.readTree(body); }
}
