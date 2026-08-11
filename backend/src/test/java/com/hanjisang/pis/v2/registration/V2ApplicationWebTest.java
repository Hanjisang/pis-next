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
class V2ApplicationWebTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM pis_v2.pathology_application_case");
        jdbcTemplate.update("DELETE FROM pis_v2.pathology_registration_receipt_print");
        jdbcTemplate.update("DELETE FROM pis_v2.pathology_application_barcode_print");
        jdbcTemplate.update("DELETE FROM pis_v2.pathology_application_delivery");
        jdbcTemplate.update("DELETE FROM pis_v2.pathology_application_item");
        jdbcTemplate.update("DELETE FROM pis_v2.pathology_application");
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void applicationIsIndependentAndOneApplicationCanCreateMultipleCases() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-MULTI")))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        assertThat(created.get("statusCode").asText()).isEqualTo("RECEIVED");
        assertThat(created.get("items")).hasSize(2);

        JsonNode delivery = json(mockMvc.perform(post("/api/v2/applications/%s/delivery".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"patientReference":"SYNTH-PATIENT-APP","specimenLabelCode":"SYNTH-LABEL-1"}
                """ )).andExpect(status().isOk()).andReturn());
        assertThat(delivery.get("statusCode").asText()).isEqualTo("ACCEPTED");

        JsonNode printed = json(mockMvc.perform(post("/api/v2/applications/%s/barcode-print".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"printerProfileCode\":\"MOCK://SYNTH-PRINTER\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(printed.get("successCount").asInt()).isEqualTo(2);
        assertThat(mockMvc.perform(get("/api/v2/applications/%s/delivery-export".formatted(applicationId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("ACCEPTED");
        assertThat(mockMvc.perform(get("/api/v2/applications/%s/barcode-print-export".formatted(applicationId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("MOCK://SYNTH-PRINTER");

        JsonNode registered = json(mockMvc.perform(post("/api/v2/applications/%s/register".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(registered.get("createdCaseCount").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_application_case", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT status_code FROM pis_v2.pathology_application WHERE id = ?",
                String.class, java.util.UUID.fromString(applicationId))).isEqualTo("REGISTERED");
    }

    @Test
    void applicationCanBeCorrectedOrCancelledBeforeRegistration() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-CANCEL")))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        String update = """
                {"patientName":"Synthetic Updated Patient","clinicalDiagnosis":"updated synthetic diagnosis",
                 "note":"correction","items":[{"externalItemCode":"SYNTH-HISTOLOGY","itemName":"routine",
                 "specimenKindCode":"TISSUE","sequenceNo":1}]}
                """;
        JsonNode updated = json(mockMvc.perform(put("/api/v2/applications/%s".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andReturn());
        assertThat(updated.get("patientName").asText()).isEqualTo("Synthetic Updated Patient");
        assertThat(updated.get("items").findValuesAsText("statusCode")).contains("PENDING");

        JsonNode cancelled = json(mockMvc.perform(post("/api/v2/applications/%s/cancel".formatted(applicationId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"synthetic duplicate request\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(cancelled.get("statusCode").asText()).isEqualTo("CANCELLED");
        assertThat(mockMvc.perform(get("/api/v2/applications/queue")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).doesNotContain(applicationId);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String applicationRequest(String applicationNo) {
        return """
                {"applicationNo":"%s","sourceTypeCode":"MANUAL","sourceSystemCode":"PIS-MANUAL",
                 "patientReference":"SYNTH-PATIENT-APP","patientName":"Synthetic Patient","patientSexCode":"F",
                 "visitReference":"SYNTH-VISIT-APP","applicationDepartment":"SYNTH-DEPARTMENT",
                 "applicantReference":"SYNTH-DOCTOR","clinicalDiagnosis":"synthetic diagnosis",
                 "examinationPurpose":"synthetic purpose","specimenDescription":"synthetic specimen",
                 "items":[{"externalItemCode":"SYNTH-HISTOLOGY","itemName":"routine histology",
                 "specimenKindCode":"TISSUE","specimenDescription":"A","sequenceNo":1},
                 {"externalItemCode":"SYNTH-HISTOLOGY","itemName":"supplementary histology",
                 "specimenKindCode":"TISSUE","specimenDescription":"B","sequenceNo":2}]}
                """.formatted(applicationNo);
    }
}
