package com.hanjisang.pis.v2.production;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
abstract class RoutineProductionTestSupport {

    @Autowired protected WebApplicationContext context;
    @Autowired protected JdbcTemplate jdbc;
    protected MockMvc mockMvc;
    protected Cookie requestCookie;
    protected final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void configureMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    protected Fixture routineFixture(String key, int blockCount) throws Exception {
        String caseId = response(post("/api/v2/registration/cases"), """
                {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"APP-%s",
                 "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-%s",
                 "visitReference":"VISIT-%s","idempotencyKey":"case-%s"}
                """.formatted(key, key, key, key)).get("caseId").asText();
        String specimenId = response(post("/api/v2/registration/specimens"), """
                {"caseId":"%s","specimenCode":"SP-%s","specimenKindCode":"TISSUE",
                 "sourceKindCode":"LOCAL","sourceReference":"APP-%s","collectionSite":"合成部位",
                 "collectionMethodCode":"SURGICAL","labelCode":"LBL-%s","idempotencyKey":"specimen-%s"}
                """.formatted(caseId, key, key, key, key)).get("specimenId").asText();
        String grossingId = response(post("/api/v2/cases/%s/grossings".formatted(caseId)), """
                {"sourceType":"INITIAL","grossDescription":"合成取材描述",
                 "grossingInstruction":"常规取材","grossingDoctorId":"SYNTH-DOCTOR",
                 "recorderId":"SYNTH-RECORDER","idempotencyKey":"grossing-%s"}
                """.formatted(key)).get("grossingId").asText();
        response(post("/api/v2/grossings/%s/specimens".formatted(grossingId)), """
                {"specimenId":"%s","materialDescription":"合成材料","idempotencyKey":"associate-%s"}
                """.formatted(specimenId, key));
        java.util.ArrayList<String> blockIds = new java.util.ArrayList<>();
        for (int index = 1; index <= blockCount; index++) {
            blockIds.add(response(post("/api/v2/grossings/%s/blocks".formatted(grossingId)), """
                    {"specimenId":"%s","blockCode":"A%d","blockType":"ROUTINE",
                     "idempotencyKey":"block-%s-%d"}
                    """.formatted(specimenId, index, key, index)).get("blockId").asText());
        }
        response(post("/api/v2/grossings/%s/complete".formatted(grossingId)),
                "{\"expectedVersion\":0,\"idempotencyKey\":\"complete-%s\"}".formatted(key));
        return new Fixture(caseId, specimenId, grossingId, List.copyOf(blockIds));
    }

    protected String slideForBlock(String blockId) {
        return jdbc.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ? AND deleted_at IS NULL",
                UUID.class, UUID.fromString(blockId)).toString();
    }

    protected JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        if (requestCookie != null) request.cookie(requestCookie);
        return json.readTree(mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    protected record Fixture(String caseId, String specimenId, String grossingId, List<String> blockIds) { }
}
