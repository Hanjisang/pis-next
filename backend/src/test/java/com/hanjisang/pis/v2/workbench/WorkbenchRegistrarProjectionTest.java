package com.hanjisang.pis.v2.workbench;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class WorkbenchRegistrarProjectionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM pis_v2.pathology_application_case");
        jdbc.update("DELETE FROM pis_v2.pathology_registration_receipt_print");
        jdbc.update("DELETE FROM pis_v2.pathology_application_item");
        jdbc.update("DELETE FROM pis_v2.pathology_application");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void partialMultiItemApplicationRemainsPendingAndCompletedItemDisappears() throws Exception {
        JsonNode first = create("WB-REG-R1", 2);
        create("WB-REG-R2", 1);
        String applicationId = first.path("applicationId").asText();
        String completedItem = first.path("items").get(0).path("itemId").asText();
        String remainingItem = first.path("items").get(1).path("itemId").asText();

        mockMvc.perform(post("/api/v2/applications/{applicationId}/delivery", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":"%s","incomingSpecimenReference":"WB-REG-R1-1",
                         "specimenLabelCode":"WB-REG-R1-1","patientReference":"SYNTH-PATIENT",
                         "actualSpecimenDescription":"合成标本","outcomeCode":"ACCEPTED",
                         "patientMatch":true,"applicationMatch":true,"quantityMatch":true,
                         "specimenMatch":true,"containerMatch":true,"fixationMatch":true}
                        """.formatted(completedItem))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/applications/{applicationId}/items/{itemId}/register",
                applicationId, completedItem).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        JsonNode queue = queue("REGISTRATION_PENDING");
        assertThat(queue.path("count").asInt()).isEqualTo(2);
        assertThat(queue.path("items").findValuesAsText("applicationItemId"))
                .contains(remainingItem).doesNotContain(completedItem);
        assertThat(queue.path("items").findValuesAsText("businessDisplayId"))
                .contains("WB-REG-R1", "WB-REG-R2");
    }

    private JsonNode create(String number, int itemCount) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int index = 1; index <= itemCount; index++) {
            if (index > 1) items.append(',');
            items.append("{\"externalItemCode\":\"SYNTH-HISTOLOGY\",\"itemName\":\"项目")
                    .append(index).append("\",\"specimenKindCode\":\"TISSUE\",\"sequenceNo\":")
                    .append(index).append('}');
        }
        String payload = """
                {"applicationNo":"%s","sourceTypeCode":"MANUAL","sourceSystemCode":"TEST",
                 "patientReference":"SYNTH-PATIENT","patientName":"合成患者","patientSexCode":"F",
                 "patientBirthDate":"1980-01-01","visitReference":"VISIT-%s",
                 "applicationDepartment":"外科","applicantReference":"合成医生","items":[%s]}
                """.formatted(number, number, items);
        return mapper.readTree(mockMvc.perform(post("/api/v2/applications").contentType(MediaType.APPLICATION_JSON)
                .content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode queue(String key) throws Exception {
        JsonNode body = mapper.readTree(mockMvc.perform(get("/api/v2/my-workbench")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode queue : body.path("capabilityQueues")) if (key.equals(queue.path("key").asText())) return queue;
        throw new AssertionError("Queue not found: " + key);
    }
}
