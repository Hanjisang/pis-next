package com.hanjisang.pis.v2.gatec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.Base64;

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
class V2GateCWebTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build(); }

    @Test
    void digitalCustodySearchQcStatisticsAndReportExtensionAreAvailable() throws Exception {
        String caseId = createCase();
        String specimenId = createSpecimen(caseId);
        String grossingId = json(mockMvc.perform(post("/api/v2/cases/%s/grossings".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceType":"INITIAL","grossDescription":"synthetic grossing",
                         "grossingDoctorId":"p15-local-registration-actor","recorderId":"p15-local-registration-actor",
                         "idempotencyKey":"gatec-grossing"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("grossingId").asText();
        mockMvc.perform(post("/api/v2/grossings/%s/specimens".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"materialDescription\":\"synthetic specimen gross finding\",\"idempotencyKey\":\"gatec-associate\"}".formatted(specimenId)))
                .andExpect(status().isOk());
        String blockId = json(mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"A1\",\"blockType\":\"ROUTINE\",\"idempotencyKey\":\"gatec-block\"}".formatted(specimenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("blockId").asText();
        mockMvc.perform(post("/api/v2/grossings/%s/complete".formatted(grossingId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"gatec-grossing-complete\"}"))
                .andExpect(status().isOk());
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(blockId));

        JsonNode digital = json(mockMvc.perform(post("/api/v2/digital-slides")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"caseId":"%s","blockId":"%s","slideId":"%s","bindingModeCode":"AUTOMATIC",
                         "viewerReference":"fixture://digital/1","sourcePlatform":"TEST-FIXTURE"}
                        """.formatted(caseId, blockId, slideId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String digitalId = digital.get("digitalSlideId").asText();
        mockMvc.perform(post("/api/v2/digital-slides/%s/rebind".formatted(digitalId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockId\":\"%s\",\"slideId\":null}".formatted(blockId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/digital-slides/%s/unbind".formatted(digitalId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/digital-slides/%s/annotations".formatted(digitalId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"annotationTypeCode\":\"POINT\",\"geometryJson\":\"{\\\"x\\\":0.5,\\\"y\\\":0.5}\",\"label\":\"可疑区域\",\"note\":\"合成阅片记录\",\"idempotencyKey\":\"gatec-annotation\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/digital-slides/%s/annotations".formatted(digitalId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"annotationTypeCode\":\"POINT\",\"geometryJson\":\"{\\\"x\\\":0.5,\\\"y\\\":0.5}\",\"label\":\"可疑区域\",\"note\":\"合成阅片记录\",\"idempotencyKey\":\"gatec-annotation\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/digital-slides/%s/measurements".formatted(digitalId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geometryJson\":\"{\\\"x1\\\":0.1,\\\"y1\\\":0.2,\\\"x2\\\":0.8,\\\"y2\\\":0.2}\",\"value\":0.7,\"unitCode\":\"IMAGE_RATIO\",\"measurementModeCode\":\"NORMALIZED_IMAGE_COORDINATE\",\"idempotencyKey\":\"gatec-measurement\"}"))
                .andExpect(status().isOk());
        byte[] screenshotBytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        String screenshotData = Base64.getEncoder().encodeToString(screenshotBytes);
        JsonNode screenshot = json(mockMvc.perform(post("/api/v2/digital-slides/%s/screenshots".formatted(digitalId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewportJson\":\"{\\\"mode\\\":\\\"CURRENT_VIEW\\\"}\",\"mediaType\":\"image/png\",\"imageDataBase64\":\"%s\",\"idempotencyKey\":\"gatec-screenshot\"}"
                        .formatted(screenshotData)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(json(mockMvc.perform(get("/api/v2/digital-slides/%s/annotations".formatted(digitalId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())).hasSize(1);
        assertThat(json(mockMvc.perform(get("/api/v2/digital-slides/%s/measurements".formatted(digitalId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())).hasSize(1);
        assertThat(json(mockMvc.perform(get("/api/v2/digital-slides/%s/screenshots".formatted(digitalId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())).hasSize(1);
        byte[] screenshotContent = mockMvc.perform(get("/api/v2/digital-slides/screenshots/%s/content"
                        .formatted(screenshot.get("screenshotId").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(screenshotContent).isEqualTo(screenshotBytes);
        JsonNode screenshotReplay = json(mockMvc.perform(post("/api/v2/digital-slides/%s/screenshots"
                        .formatted(digitalId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewportJson\":\"{\\\"mode\\\":\\\"CURRENT_VIEW\\\"}\",\"mediaType\":\"image/png\",\"imageDataBase64\":\"%s\",\"idempotencyKey\":\"gatec-screenshot\"}"
                        .formatted(screenshotData)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(screenshotReplay.get("duplicate").asBoolean()).isTrue();
        assertThat(screenshotReplay.get("screenshotId").asText()).isEqualTo(screenshot.get("screenshotId").asText());

        mockMvc.perform(post("/api/v2/case-support/cases/%s/favorite".formatted(caseId)))
                .andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get("/api/v2/case-support/cases/%s/favorite".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("favorite").asBoolean())
                .isTrue();
        mockMvc.perform(post("/api/v2/case-support/cases/%s/follow-ups".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"followUpDate\":\"2026-09-01\",\"plan\":\"合成随访计划\",\"idempotencyKey\":\"gatec-follow-up\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/case-support/cases/%s/consultations".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"initiatorRef\":\"SYNTH-DOCTOR-A\",\"participantRefs\":\"SYNTH-DOCTOR-B\",\"reason\":\"合成会诊\",\"idempotencyKey\":\"gatec-consultation\"}"))
                .andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get("/api/v2/case-support/cases/%s/consultations".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())).hasSize(1);

        String locationId = json(mockMvc.perform(post("/api/v2/custody/locations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"locationCode":"ROOM-1","locationName":"合成归档室","locationKindCode":"ROOM"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("locationId").asText();
        mockMvc.perform(post("/api/v2/custody/archive").contentType(MediaType.APPLICATION_JSON).content("""
                {"blockIds":[],"slideIds":["%s"],"locationId":"%s","reason":"初次归档",
                 "idempotencyKey":"gatec-archive"}
                """.formatted(slideId, locationId))).andExpect(status().isOk());
        String loanId = json(mockMvc.perform(post("/api/v2/custody/loans").contentType(MediaType.APPLICATION_JSON).content("""
                {"blockIds":[],"slideIds":["%s"],"borrowerReference":"SYNTH-DOCTOR",
                 "purpose":"会诊复核"}
                """.formatted(slideId))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("loanId").asText();
        mockMvc.perform(post("/api/v2/custody/loans/%s/return".formatted(loanId))).andExpect(status().isOk());

        JsonNode custodyMaterials = json(mockMvc.perform(get("/api/v2/custody/cases/%s/materials".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(custodyMaterials).hasSize(2);
        JsonNode custodySlide = java.util.stream.StreamSupport.stream(custodyMaterials.spliterator(), false)
                .filter(item -> "SLIDE".equals(item.get("materialKind").asText())).findFirst().orElseThrow();
        assertThat(custodySlide.get("materialCode").asText()).isEqualTo("A1-HE");
        assertThat(custodySlide.get("locationName").asText()).isEqualTo("合成归档室");
        assertThat(custodySlide.path("borrowerReference").isMissingNode()).isTrue();

        assertThat(json(mockMvc.perform(get("/api/v2/search?q=H-000001"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).size()).isGreaterThan(0);
        assertThat(json(mockMvc.perform(post("/api/v2/qc/evaluate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\"}".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isArray()).isTrue();
        assertThat(json(mockMvc.perform(get("/api/v2/statistics/summary"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("counts").get("slideCount").asLong())
                .isEqualTo(1);
        assertThat(json(mockMvc.perform(get("/api/v2/report-extensions"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isArray()).isTrue();

        mockMvc.perform(post("/api/v2/custody/destruction").contentType(MediaType.APPLICATION_JSON).content("""
                {"blockIds":[],"slideIds":["%s"],"reason":"合成销毁验证","batchReference":"DEST-1"}
                """.formatted(slideId))).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT destroyed_at FROM pis_v2.slide WHERE id = ?", Object.class,
                UUID.fromString(slideId))).isNotNull();
        jdbcTemplate.update("UPDATE pis_v2.pathology_case SET organization_reference = 'SYNTH-OTHER' WHERE id = ?",
                UUID.fromString(caseId));
        mockMvc.perform(get("/api/v2/digital-slides/%s/annotations".formatted(digitalId)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/v2/digital-slides/screenshots/%s/content"
                        .formatted(screenshot.get("screenshotId").asText())))
                .andExpect(status().isUnprocessableEntity());
    }

    private String createCase() throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON).content("""
                {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"GATEC-CASE-1",
                 "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-PATIENT-GATEC",
                 "visitReference":"SYNTH-VISIT-GATEC","idempotencyKey":"gatec-case"}
                """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("caseId").asText();
    }

    private String createSpecimen(String caseId) throws Exception {
        return json(mockMvc.perform(post("/api/v2/registration/specimens").contentType(MediaType.APPLICATION_JSON).content("""
                {"caseId":"%s","specimenCode":"A","specimenKindCode":"TISSUE","sourceKindCode":"LOCAL",
                 "sourceReference":"SYNTH-GATEC-SOURCE","collectionSite":"synthetic site",
                 "collectionMethodCode":"SURGICAL","idempotencyKey":"gatec-specimen"}
                """.formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("specimenId").asText();
    }

    private JsonNode json(String body) throws Exception { return objectMapper.readTree(body); }
}
