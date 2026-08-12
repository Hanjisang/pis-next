package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class V2RegistrationWebTest {

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
    void createsActiveCaseIdempotentlyAndKeepsBusinessNumberSeparate() throws Exception {
        String request = caseRequest("APP-I01-001", "SYNTH-PATIENT-001", "case-i01-001");

        MvcResult first = mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn();
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        String caseId = firstBody.get("caseId").asText();
        assertThat(firstBody.get("caseNo").asText()).isEqualTo("H-000001");
        assertThat(firstBody.get("lifecycleStateCode").asText()).isEqualTo("ACTIVE");
        assertThat(firstBody.get("numberBindingActive").asBoolean()).isTrue();
        assertThat(firstBody.get("caseId").asText()).isNotEqualTo(firstBody.get("caseNo").asText());

        JsonNode replayBody = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(replayBody.get("caseId").asText()).isEqualTo(caseId);
        assertThat(replayBody.get("duplicate").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_case", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void specimenCodeIsUniqueWithinCaseMutableAndSoftDeletable() throws Exception {
        String firstCaseId = createCase("APP-I01-002", "SYNTH-PATIENT-002", "case-i01-002");
        String firstSpecimenId = createSpecimen(firstCaseId, "A", "specimen-i01-001", "SYNTH-LABEL-001");

        String duplicateCode = specimenRequest(firstCaseId, "A", "specimen-i01-002", "SYNTH-LABEL-002");
        mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON).content(duplicateCode))
                .andExpect(status().isConflict());

        String secondCaseId = createCase("APP-I01-003", "SYNTH-PATIENT-003", "case-i01-003");
        String secondSpecimenId = createSpecimen(secondCaseId, "A", "specimen-i01-003", "SYNTH-LABEL-003");
        assertThat(secondSpecimenId).isNotEqualTo(firstSpecimenId);

        String update = """
                {"specimenCode":"A-UPDATED","specimenKindCode":"TISSUE","sourceKindCode":"LOCAL",
                 "sourceReference":"SYNTH-SOURCE-UPDATED","collectionSite":"updated site",
                 "collectionMethodCode":"SURGICAL","labelCode":"SYNTH-LABEL-UPDATED","expectedVersion":0}
                """;
        JsonNode updated = objectMapper.readTree(mockMvc.perform(put("/api/v2/registration/specimens/%s".formatted(firstSpecimenId))
                .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.get("specimenCode").asText()).isEqualTo("A-UPDATED");
        assertThat(updated.get("concurrencyVersion").asLong()).isEqualTo(1);

        mockMvc.perform(put("/api/v2/registration/specimens/%s".formatted(firstSpecimenId))
                .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict());

        String softDelete = """
                {"expectedVersion":1,"reason":"synthetic correction"}
                """;
        JsonNode deleted = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/registration/specimens/%s/soft-delete".formatted(firstSpecimenId))
                .contentType(MediaType.APPLICATION_JSON).content(softDelete))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(deleted.get("deletedAt").isNull()).isFalse();
        assertThat(deleted.get("deletionReason").asText()).isEqualTo("synthetic correction");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE deleted_at IS NOT NULL",
                Integer.class)).isEqualTo(1);

        String reusedCodeSpecimenId = createSpecimen(firstCaseId, "A-UPDATED", "specimen-i01-004",
                "SYNTH-LABEL-UPDATED");
        assertThat(reusedCodeSpecimenId).isNotEqualTo(firstSpecimenId);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
        String first = caseRequest("APP-I01-004", "SYNTH-PATIENT-004", "case-i01-conflict");
        mockMvc.perform(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk());

        String conflict = first.replace("SYNTH-PATIENT-004", "SYNTH-PATIENT-005");
        mockMvc.perform(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON).content(conflict))
                .andExpect(status().isConflict());
    }

    @Test
    void caseCancellationKeepsIdentityReleasesNumberBindingAndIsAudited() throws Exception {
        String caseId = createCase("APP-I01-CANCEL", "SYNTH-PATIENT-CANCEL", "case-i01-cancel");
        JsonNode specimen = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"caseId":"%s","specimenCode":"CANCEL-A","specimenKindCode":"TISSUE",
                         "sourceKindCode":"LOCAL","sourceReference":"SYNTH-CANCEL-SOURCE",
                         "collectionSite":"synthetic site","collectionMethodCode":"SURGERY",
                         "labelCode":"SYNTH-CANCEL-LABEL","idempotencyKey":"specimen-cancel-history"}
                        """.formatted(caseId))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        String specimenId = specimen.get("specimenId").asText();
        JsonNode cancelled = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/registration/cases/%s/cancel".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"reason\":\"synthetic receiving rejection\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cancelled.get("caseId").asText()).isEqualTo(caseId);
        assertThat(cancelled.get("lifecycleStateCode").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("numberBindingActive").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT cancellation_reason FROM pis_v2.pathology_case WHERE id = ?",
                String.class, java.util.UUID.fromString(caseId))).isEqualTo("synthetic receiving rejection");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE id = ?",
                Integer.class, java.util.UUID.fromString(specimenId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.pathology_number_history WHERE case_id = ? AND operation_code = 'CANCELLATION_RELEASE'",
                Integer.class, java.util.UUID.fromString(caseId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE operation_code = 'PIS-V2-CASE-CANCEL'",
                Integer.class)).isEqualTo(1);
        assertThat(mockMvc.perform(get("/api/v2/search?q=APP-I01-CANCEL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains(caseId);
    }

    @Test
    void pathologyNumberCorrectionKeepsCaseIdentityAndPreservesSearchableHistory() throws Exception {
        String firstCaseId = createCase("APP-I01-CORRECT-A", "SYNTH-PATIENT-CORRECT", "case-i01-correct-a");
        String secondCaseId = createCase("APP-I01-CORRECT-B", "SYNTH-PATIENT-OTHER", "case-i01-correct-b");
        JsonNode firstBefore = objectMapper.readTree(mockMvc.perform(
                get("/api/v2/registration/cases/{caseId}", firstCaseId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(mockMvc.perform(
                get("/api/v2/registration/cases/{caseId}", secondCaseId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/pathology-number", firstCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newPathologyNo":"P-CORRECTED-001","reason":""}
                        """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/pathology-number", firstCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newPathologyNo":"%s","reason":"synthetic duplicate attempt","expectedVersion":0}
                        """.formatted(second.get("caseNo").asText())))
                .andExpect(status().isConflict());

        JsonNode corrected = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/registration/cases/{caseId}/pathology-number", firstCaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"newPathologyNo":"P-CORRECTED-001","reason":"synthetic transcription correction",
                         "expectedVersion":0}
                        """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(corrected.get("caseId").asText()).isEqualTo(firstCaseId);
        assertThat(corrected.get("caseNo").asText()).isEqualTo("P-CORRECTED-001");
        assertThat(corrected.get("businessTypeCode").asText()).isEqualTo(firstBefore.get("businessTypeCode").asText());

        JsonNode history = objectMapper.readTree(mockMvc.perform(get(
                "/api/v2/registration/cases/{caseId}/pathology-number-history", firstCaseId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("oldPathologyNo").asText()).isEqualTo(firstBefore.get("caseNo").asText());
        assertThat(history.get(0).get("newPathologyNo").asText()).isEqualTo("P-CORRECTED-001");
        assertThat(mockMvc.perform(get("/api/v2/search").param("q", firstBefore.get("caseNo").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains(firstCaseId);
    }

    @Test
    void specimenFactsReceivingAndSplitLineageArePersisted() throws Exception {
        String caseId = createCase("APP-I01-SPECIMEN-FACTS", "SYNTH-PATIENT-SPECIMEN", "case-i01-specimen-facts");
        String specimenRequest = """
                {"caseId":"%s","specimenCode":"A","specimenKindCode":"FLUID","sourceKindCode":"LOCAL",
                 "sourceReference":"SYNTH-SOURCE-FACTS","collectionSite":"synthetic pleura",
                 "collectionMethodCode":"ASPIRATION","lateralityCode":"LEFT","quantityValue":2.5,
                 "quantityUnitCode":"ML","description":"synthetic fluid","removedAt":"2026-08-12T01:00:00Z",
                 "fixedAt":"2026-08-12T02:00:00Z","labelCode":"SYNTH-LABEL-FACTS",
                 "idempotencyKey":"specimen-i01-facts"}
                """.formatted(caseId);
        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON).content(specimenRequest))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String specimenId = created.get("specimenId").asText();
        assertThat(created.get("lateralityCode").asText()).isEqualTo("LEFT");
        assertThat(created.get("quantityValue").asDouble()).isEqualTo(2.5D);

        JsonNode received = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/registration/specimens/%s/receive".formatted(specimenId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"verificationCode\":\"MATCHED\",\"actualDescription\":\"synthetic fluid\","
                        + "\"reason\":\"barcode and material matched\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(received.get("receivedAt").isNull()).isFalse();
        assertThat(received.get("concurrencyVersion").asLong()).isEqualTo(1);

        JsonNode child = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/registration/specimens/%s/split".formatted(specimenId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"childSpecimenCode\":\"A1\",\"quantityValue\":1.0,"
                        + "\"quantityUnitCode\":\"ML\",\"reason\":\"synthetic aliquot\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(child.get("specimenCode").asText()).isEqualTo("A1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.specimen_split WHERE source_specimen_id = ?", Integer.class,
                java.util.UUID.fromString(specimenId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.specimen_receiving_fact WHERE specimen_id = ?", Integer.class,
                java.util.UUID.fromString(specimenId))).isEqualTo(1);
    }

    private String createCase(String applicationId, String patientReference, String idempotencyKey) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(caseRequest(applicationId, patientReference, idempotencyKey)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("caseId").asText();
    }

    private String createSpecimen(String caseId, String specimenCode, String idempotencyKey, String labelCode)
            throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(specimenRequest(caseId, specimenCode, idempotencyKey, labelCode)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("specimenId").asText();
    }

    private static String caseRequest(String applicationId, String patientReference, String idempotencyKey) {
        return """
                {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                 "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"%s",
                 "visitReference":"SYNTH-VISIT-001","idempotencyKey":"%s"}
                """.formatted(applicationId, patientReference, idempotencyKey);
    }

    private static String specimenRequest(String caseId, String specimenCode, String idempotencyKey,
            String labelCode) {
        return """
                {"caseId":"%s","specimenCode":"%s","specimenKindCode":"TISSUE","sourceKindCode":"LOCAL",
                 "sourceReference":"SYNTH-SOURCE-%s","collectionSite":"synthetic site",
                 "collectionMethodCode":"SURGICAL","labelCode":"%s","idempotencyKey":"%s"}
                """.formatted(caseId, specimenCode, idempotencyKey, labelCode, idempotencyKey);
    }
}
