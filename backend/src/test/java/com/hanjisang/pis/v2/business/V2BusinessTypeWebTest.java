package com.hanjisang.pis.v2.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class V2BusinessTypeWebTest {

    private static final UUID CYTOLOGY_TYPE = UUID.fromString("00000000-0000-0000-0000-00000000c201");
    private static final UUID MOLECULAR_TYPE = UUID.fromString("00000000-0000-0000-0000-00000000c202");
    private static final UUID REFERRAL_TYPE = UUID.fromString("00000000-0000-0000-0000-00000000c203");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        seedBusinessTypes();
    }

    @Test
    void cytologyUsesSpecimenToSlideAndCanClaimDiagnosisWithoutBlock() throws Exception {
        String caseId = createCase("SYNTH-CYTOLOGY", "cytology-case-1", "cytology-case-key");
        String specimenId = createSpecimen(caseId, "C-1", "cytology-specimen-key");
        JsonNode slide = json(mockMvc.perform(post("/api/v2/cases/%s/specimens/%s/slides".formatted(caseId, specimenId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"slideCode":"C-1-1","slideType":"CYTOLOGY","idempotencyKey":"cytology-slide-key"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(jdbcTemplate.queryForObject("SELECT block_id FROM pis_v2.slide WHERE id = ?", UUID.class,
                UUID.fromString(slide.get("slideId").asText()))).isNull();
        assertThat(slide.get("sourceContextType").asText()).isEqualTo("CYTOLOGY");
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slide.get("slideId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"cytology-slide-complete\"}"))
                .andExpect(status().isOk());
        JsonNode workspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/%s".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("materialTree").get("initialProductionComplete").asBoolean()).isTrue();
        assertThat(workspace.get("actions").get("canClaim").asBoolean()).isTrue();
        JsonNode diagnosis = claim(caseId, "cytology-claim-key");
        assertThat(diagnosis.get("diagnosisId").asText()).isNotBlank();
    }

    @Test
    void molecularResultIsRequiredBeforeDiagnosisAndStaysOnTheSameCase() throws Exception {
        String caseId = createCase("SYNTH-MOLECULAR", "molecular-case-1", "molecular-case-key");
        mockMvc.perform(post("/api/v2/diagnoses/claim").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"molecular-claim-before-result\"}".formatted(caseId)))
                .andExpect(status().isUnprocessableEntity());
        JsonNode result = json(mockMvc.perform(post("/api/v2/molecular/cases/%s/results".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resultCode":"PANEL-1","resultData":"{\\"mutationDetected\\":false}",
                         "idempotencyKey":"molecular-result-key"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("caseId").asText()).isEqualTo(caseId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.molecular_result WHERE case_id = ?",
                Integer.class, UUID.fromString(caseId))).isEqualTo(1);
        JsonNode workspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/%s".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("materialTree").get("initialProductionComplete").asBoolean()).isTrue();
        assertThat(workspace.get("actions").get("canClaim").asBoolean()).isTrue();
        assertThat(claim(caseId, "molecular-claim-after-result").get("diagnosisId").asText()).isNotBlank();
        JsonNode sendOut = json(mockMvc.perform(post("/api/v2/send-outs/cases/%s".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"externalReference":"SEND-OUT-1","destinationName":"SYNTH-LAB",
                         "idempotencyKey":"send-out-key"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode received = json(mockMvc.perform(post("/api/v2/send-outs/%s/result".formatted(sendOut.get("sendOutId").asText()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"resultData\":\"{\\\"value\\\":\\\"negative\\\"}\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(received.get("statusCode").asText()).isEqualTo("RESULT_RECEIVED");
    }

    @Test
    void consultationKeepsExternalBlockFactAndCreatesLocalSlide() throws Exception {
        String caseId = createCase("SYNTH-CONSULTATION", "consultation-case-1", "consultation-case-key");
        JsonNode material = json(mockMvc.perform(post("/api/v2/consultation/cases/%s/external-material".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"externalReference":"EXT-HOSPITAL-BLOCK-1","specimenKindCode":"TISSUE",
                         "blockCode":"EXT-B1","blockType":"EXTERNAL","operatorId":"p15-local-registration-actor",
                         "createLocalSlide":true,"localSlideCode":"LOCAL-S1","localSlideType":"HE",
                         "idempotencyKey":"consultation-material-key"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID blockId = UUID.fromString(material.get("blockId").asText());
        UUID slideId = UUID.fromString(material.get("slideId").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT external_source_flag FROM pis_v2.block WHERE id = ?",
                Boolean.class, blockId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT source_context_type FROM pis_v2.slide WHERE id = ?",
                String.class, slideId)).isEqualTo("EXTERNAL");
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slideId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"consultation-slide-complete\"}"))
                .andExpect(status().isOk());
        JsonNode workspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/%s".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("materialTree").get("initialProductionComplete").asBoolean()).isTrue();
        assertThat(workspace.get("actions").get("canClaim").asBoolean()).isTrue();
        assertThat(claim(caseId, "consultation-claim-key").get("diagnosisId").asText()).isNotBlank();
    }

    private JsonNode claim(String caseId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/diagnoses/claim").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"%s\"}".formatted(caseId, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String createCase(String item, String applicationId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                         "applicationItemCode":"%s","patientReference":"SYNTH-PATIENT-%s",
                         "visitReference":"SYNTH-VISIT-%s","idempotencyKey":"%s"}
                        """.formatted(applicationId, item, applicationId, applicationId, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("caseId").asText();
    }

    private String createSpecimen(String caseId, String code, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/specimens").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","specimenCode":"%s","specimenKindCode":"TISSUE",
                         "sourceKindCode":"LOCAL","sourceReference":"SYNTH-SOURCE-%s",
                         "collectionSite":"synthetic site","collectionMethodCode":"SURGICAL",
                         "idempotencyKey":"%s"}
                        """.formatted(caseId, code, key, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("specimenId").asText();
    }

    private void seedBusinessTypes() {
        seedType(CYTOLOGY_TYPE, "CYTOLOGY_NON_GYN", "CYTOLOGY", "SYNTH-CYTOLOGY", "C-");
        seedType(MOLECULAR_TYPE, "MOLECULAR", "MOLECULAR", "SYNTH-MOLECULAR", "M-");
        seedType(REFERRAL_TYPE, "REFERRAL", "REFERRAL", "SYNTH-CONSULTATION", "R-");
    }

    private void seedType(UUID typeId, String typeCode, String modality, String itemCode, String prefix) {
        jdbcTemplate.update("""
                MERGE INTO pis_v2.business_type (id,business_type_code,display_name,modality_code,active,
                    configuration_version,created_at,created_by_ref)
                KEY (business_type_code) VALUES (?, ?, ?, ?, TRUE, 1, CURRENT_TIMESTAMP, 'TEST')
                """, typeId, typeCode, typeCode, modality);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.application_item_mapping (id,application_item_code,business_type_id,
                    default_specimen_kind_code,required,sequence_no,active,configuration_version,created_at,created_by_ref)
                KEY (application_item_code) VALUES (?, ?, ?, 'TISSUE', TRUE, 1, TRUE, 1, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID(), itemCode, typeId);
        jdbcTemplate.update("""
                MERGE INTO pis_v2.pathology_number_rule (id,business_type_id,organization_reference,number_kind_code,
                    prefix,scope_code,padding_width,next_serial,active,configuration_version,created_at,updated_at,created_by_ref)
                KEY (organization_reference,business_type_id,number_kind_code)
                VALUES (?, ?, 'LOCAL_HOSPITAL', 'CASE', ?, 'ORGANIZATION', 6, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST'),
                       (?, ?, 'LOCAL_HOSPITAL', 'SPECIMEN', ?, 'ORGANIZATION', 7, 1, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID(), typeId, prefix, UUID.randomUUID(), typeId, prefix + "S");
        UUID templateId = UUID.randomUUID();
        jdbcTemplate.update("""
                MERGE INTO pis_v2.diagnosis_template (id,organization_reference,template_code,template_name,
                    business_type_id,scope_code,enabled,concurrency_version,created_at,created_by_ref,updated_at,updated_by_ref)
                KEY (organization_reference,template_code)
                VALUES (?, 'LOCAL_HOSPITAL', ?, ?, ?, 'LOCAL_HOSPITAL', TRUE, 0, CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST')
                """, templateId, "DEFAULT-" + typeCode, typeCode, typeId);
        jdbcTemplate.update("""
                INSERT INTO pis_v2.diagnosis_template_version
                    (id,template_id,version_no,schema_definition,status_code,published_at,published_by_ref,
                     created_at,created_by_ref,concurrency_version)
                VALUES (?, ?, 1, '{"components":[]}', 'PUBLISHED', CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST', 0)
                """, UUID.randomUUID(), templateId);
    }

    private JsonNode json(String body) throws Exception { return objectMapper.readTree(body); }
}
