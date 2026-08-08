package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(status().isUnprocessableEntity());

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
                .andExpect(status().isUnprocessableEntity());

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
                .andExpect(status().isUnprocessableEntity());
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
