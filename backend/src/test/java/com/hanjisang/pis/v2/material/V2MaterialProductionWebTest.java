package com.hanjisang.pis.v2.material;

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
class V2MaterialProductionWebTest {

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
    void completeGrossingIsIdempotentAndCorrectionReopenDoesNotCreateNewOutputs() throws Exception {
        String caseId = createCase("APP-I02-001");
        String specimenA = createSpecimen(caseId, "A", "specimen-i02-001");
        String specimenB = createSpecimen(caseId, "B", "specimen-i02-002");
        String grossingId = createGrossing(caseId, "grossing-i02-001");
        associateSpecimen(grossingId, specimenA, "associate-i02-001");
        associateSpecimen(grossingId, specimenB, "associate-i02-002");
        String blockA1 = createBlock(grossingId, specimenA, "A1", "block-i02-a1");
        createBlock(grossingId, specimenA, "A2", "block-i02-a2");
        createBlock(grossingId, specimenB, "B1", "block-i02-b1");

        JsonNode firstCompletion = completeGrossing(grossingId, 0, "complete-i02-001");
        assertThat(firstCompletion.get("createdSlideCount").asInt()).isEqualTo(3);
        assertThat(firstCompletion.get("duplicate").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.print_log", Integer.class)).isEqualTo(3);

        JsonNode replay = completeGrossing(grossingId, 0, "complete-i02-001");
        assertThat(replay.get("duplicate").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide", Integer.class)).isEqualTo(3);

        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/v2/cases/%s/materials".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(tree.get("specimens")).hasSize(2);
        assertThat(tree.get("initialRequiredCount").asInt()).isEqualTo(3);
        assertThat(tree.get("initialCompletedCount").asInt()).isZero();
        assertThat(tree.get("initialProductionComplete").asBoolean()).isFalse();

        JsonNode reopened = objectMapper.readTree(mockMvc.perform(post("/api/v2/grossings/%s/reopen".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"reason\":\"synthetic correction\",\"idempotencyKey\":\"reopen-i02-001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reopened.get("concurrencyVersion").asLong()).isEqualTo(2);
        assertThat(reopened.get("completedAt").isNull()).isFalse();
        mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"A3\",\"blockType\":\"ROUTINE\",\"idempotencyKey\":\"block-i02-a3\"}"
                        .formatted(specimenA)))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide", Integer.class)).isEqualTo(3);
        assertThat(blockA1).isNotBlank();
    }

    @Test
    void blockCorrectionPreservesTraceabilityAndSlidePreventsDeletion() throws Exception {
        String caseId = createCase("APP-I02-002");
        String specimenId = createSpecimen(caseId, "A", "specimen-i02-003");
        String grossingId = createGrossing(caseId, "grossing-i02-002");
        associateSpecimen(grossingId, specimenId, "associate-i02-003");
        String blockId = createBlock(grossingId, specimenId, "A1", "block-i02-a4");
        completeGrossing(grossingId, 0, "complete-i02-003");
        mockMvc.perform(post("/api/v2/grossings/%s/reopen".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"reason\":\"synthetic block maintenance\",\"idempotencyKey\":\"reopen-i02-002\"}"))
                .andExpect(status().isOk());
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(blockId)).toString();

        JsonNode renamed = objectMapper.readTree(mockMvc.perform(put("/api/v2/blocks/%s".formatted(blockId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockCode\":\"A1-R\",\"blockType\":\"ROUTINE\",\"reason\":\"编号录入纠正\",\"expectedVersion\":0,\"idempotencyKey\":\"rename-i02-001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(renamed.get("blockCode").asText()).isEqualTo("A1-R");
        assertThat(jdbcTemplate.queryForObject("SELECT slide_code FROM pis_v2.slide WHERE id = ?", String.class,
                UUID.fromString(slideId))).isEqualTo("A1-R-HE");

        mockMvc.perform(post("/api/v2/blocks/%s/soft-delete".formatted(blockId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"reason\":\"synthetic block correction\",\"idempotencyKey\":\"delete-i02-001\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE deleted_at IS NOT NULL",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE deleted_at IS NOT NULL",
                Integer.class)).isZero();
        JsonNode tree = objectMapper.readTree(mockMvc.perform(get("/api/v2/cases/%s/materials".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(tree.get("specimens").get(0).get("blocks")).hasSize(1);
    }

    @Test
    void slideCompletionIsOptimisticAndPrintFailureDoesNotDeleteMaterial() throws Exception {
        String caseId = createCase("APP-I02-003");
        String specimenId = createSpecimen(caseId, "A", "specimen-i02-004");
        String grossingId = createGrossing(caseId, "grossing-i02-003");
        associateSpecimen(grossingId, specimenId, "associate-i02-004");
        String blockId = createBlock(grossingId, specimenId, "A1", "block-i02-a5");
        completeGrossing(grossingId, 0, "complete-i02-004");
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(blockId)).toString();

        JsonNode completed = objectMapper.readTree(mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"slide-complete-i02-001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(completed.get("completedAt").isNull()).isFalse();
        assertThat(completed.get("concurrencyVersion").asLong()).isEqualTo(1);

        jdbcTemplate.update("""
                INSERT INTO pis_v2.print_rule
                    (id, organization_reference, business_type_id, entity_kind_code, trigger_code,
                     printer_profile_code, active, configuration_version, created_at, updated_at, created_by_ref)
                VALUES (?, 'LOCAL_HOSPITAL', NULL, 'SLIDE', 'MANUAL', 'MOCK://FAIL-SYNTH-PRINTER', TRUE, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID());
        JsonNode print = objectMapper.readTree(mockMvc.perform(post("/api/v2/slides/%s/print".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"synthetic reprint\",\"idempotencyKey\":\"print-i02-001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(print.get("resultCode").asText()).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE id = ?", Integer.class,
                UUID.fromString(slideId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT result_code FROM pis_v2.print_log WHERE entity_id = ? ORDER BY requested_at DESC LIMIT 1",
                String.class, UUID.fromString(slideId))).isEqualTo("FAILED");
    }

    @Test
    void uxWorkspaceQueriesReturnCaseContextAndProductionQueue() throws Exception {
        String caseId = createCase("APP-UX01-QUERY");
        String specimenId = createSpecimen(caseId, "A", "specimen-ux01-query");

        JsonNode beforeGrossing = objectMapper.readTree(mockMvc.perform(
                get("/api/v2/cases/%s/grossing-workspace".formatted(caseId))
                        .queryParam("sourceType", "INITIAL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(beforeGrossing.get("patientReference").asText()).isEqualTo("SYNTH-APP-UX01-QUERY");
        assertThat(beforeGrossing.path("grossing").isMissingNode()).isTrue();
        assertThat(beforeGrossing.get("specimens")).hasSize(1);

        String grossingId = createGrossing(caseId, "grossing-ux01-query");
        associateSpecimen(grossingId, specimenId, "associate-ux01-query");
        createBlock(grossingId, specimenId, "A1", "block-ux01-query");
        completeGrossing(grossingId, 0, "complete-ux01-query");

        JsonNode queue = objectMapper.readTree(mockMvc.perform(get("/api/v2/histology-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(queue.get("slides")).isNotEmpty();
        JsonNode slide = queue.get("slides").get(0);
        assertThat(slide.get("caseNo").asText()).isNotBlank();
        assertThat(slide.get("patientReference").asText()).isEqualTo("SYNTH-APP-UX01-QUERY");
        assertThat(slide.get("slideCode").asText()).isEqualTo("A1-HE");
    }

    @Test
    void histologyFactsExposeFiveDerivedPhasesAndKeepExceptionsOnTheSlide() throws Exception {
        String caseId = createCase("APP-PX01-HISTOLOGY");
        String specimenId = createSpecimen(caseId, "A", "specimen-px01-histology");
        String grossingId = createGrossing(caseId, "grossing-px01-histology");
        associateSpecimen(grossingId, specimenId, "associate-px01-histology");
        String blockId = createBlock(grossingId, specimenId, "A1", "block-px01-histology");
        completeGrossing(grossingId, 0, "complete-px01-histology");
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(blockId)).toString();

        JsonNode initial = objectMapper.readTree(mockMvc.perform(
                get("/api/v2/histology-workbench").queryParam("caseId", caseId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(initial.get("slides")).hasSize(1);
        assertThat(initial.get("slides").get(0).get("phases")).hasSize(5);

        JsonNode started = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/histology/slides/%s/phases/DEHYDRATION/start".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceReference\":\"SYNTH-DEHYDRATOR\",\"batchReference\":\"B-001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(started.get("startedAt").isNull()).isFalse();
        assertThat(started.path("completedAt").isMissingNode() || started.path("completedAt").isNull()).isTrue();

        mockMvc.perform(post("/api/v2/histology/slides/%s/phases/DEHYDRATION/exception".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exceptionCode\":\"染色过浅\",\"note\":\"synthetic exception\"}"))
                .andExpect(status().isOk());
        JsonNode completed = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/histology/slides/%s/phases/DEHYDRATION/complete".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(completed.get("completedAt").isNull()).isFalse();
        assertThat(completed.get("exceptionCode").asText()).isEqualTo("染色过浅");
        assertThat(completed.get("exceptionNote").asText()).isEqualTo("synthetic exception");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.material_process_fact WHERE slide_id = ?",
                Integer.class, UUID.fromString(slideId))).isEqualTo(1);
    }

    @Test
    void caseWorkspaceReturnsMaterialTreeAndBusinessTimeline() throws Exception {
        String caseId = createCase("APP-PX01-CASE-WORKSPACE");
        String specimenId = createSpecimen(caseId, "A", "specimen-px01-case-workspace");
        String grossingId = createGrossing(caseId, "grossing-px01-case-workspace");
        associateSpecimen(grossingId, specimenId, "associate-px01-case-workspace");
        createBlock(grossingId, specimenId, "A1", "block-px01-case-workspace");
        completeGrossing(grossingId, 0, "complete-px01-case-workspace");

        JsonNode workspace = objectMapper.readTree(mockMvc.perform(
                get("/api/v2/case-workspaces/%s".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("caseHeader").get("caseId").asText()).isEqualTo(caseId);
        assertThat(workspace.get("materialTree").get("specimens")).hasSize(1);
        assertThat(workspace.get("materialTree").get("specimens").get(0).get("blocks")).hasSize(1);
        assertThat(workspace.get("timeline").findValuesAsText("title"))
                .contains("完成登记", "登记标本", "开始取材", "新增蜡块", "完成取材");
    }

    @Test
    void grossingImageCaptureAnnotationsAndMeasurementArePersistedAndAudited() throws Exception {
        String caseId = createCase("APP-IMAGE-001");
        String specimenId = createSpecimen(caseId, "A", "specimen-image-001");
        String grossingId = createGrossing(caseId, "grossing-image-001");
        associateSpecimen(grossingId, specimenId, "associate-image-001");

        JsonNode image = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/material/grossings/%s/images/capture".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"deviceReference\":\"SIMULATOR-GROSS-IMAGING\"}"
                        .formatted(specimenId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String imageId = image.get("imageId").asText();
        assertThat(image.get("storageReference").asText()).startsWith("data:image/svg+xml;base64,");

        JsonNode annotation = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/material/grossings/images/%s/annotations".formatted(imageId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"annotationTypeCode\":\"RECTANGLE\",\"geometryJson\":\"{\\\"x\\\":1}\",\"label\":\"lesion\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String annotationId = annotation.get("annotationId").asText();

        JsonNode measurement = objectMapper.readTree(mockMvc.perform(post(
                "/api/v2/material/grossings/images/%s/measurements".formatted(imageId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geometryJson\":\"{\\\"x1\\\":1,\\\"x2\\\":5}\",\"value\":4.0,\"unitCode\":\"MM\",\"measurementModeCode\":\"IMAGE_COORDINATE\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(measurement.get("value").asDouble()).isEqualTo(4.0);
        assertThat(mockMvc.perform(get("/api/v2/material/grossings/%s/images".formatted(grossingId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains(imageId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v2/material/grossings/images/%s/annotations/%s".formatted(imageId, annotationId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/material/grossings/images/%s/delete".formatted(imageId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"synthetic image correction\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.grossing_image WHERE deleted_at IS NOT NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void materialReworkKeepsOriginalSlideAndLinksReplacementWithAudit() throws Exception {
        String caseId = createCase("APP-REWORK-001");
        String specimenId = createSpecimen(caseId, "A", "specimen-rework-001");
        String grossingId = createGrossing(caseId, "grossing-rework-001");
        associateSpecimen(grossingId, specimenId, "associate-rework-001");
        String firstBlock = createBlock(grossingId, specimenId, "A1", "block-rework-001");
        completeGrossing(grossingId, 0, "complete-rework-001");
        String originalSlide = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?",
                String.class, UUID.fromString(firstBlock));

        mockMvc.perform(post("/api/v2/slides/%s/rework".formatted(originalSlide))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reworkTypeCode\":\"RE_STAIN\",\"reason\":\"synthetic quality issue\",\"idempotencyKey\":\"rework-001\"}"))
                .andExpect(status().isOk());
        String supplementaryGrossingId = createGrossing(caseId, "grossing-rework-002", "OTHER",
                UUID.randomUUID().toString());
        associateSpecimen(supplementaryGrossingId, specimenId, "associate-rework-002");
        String secondBlock = createBlock(supplementaryGrossingId, specimenId, "A2", "block-rework-002");
        completeGrossing(supplementaryGrossingId, 0, "complete-rework-002");
        String replacementSlide = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?",
                String.class, UUID.fromString(secondBlock));
        String reworkId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.material_rework WHERE idempotency_key = ?",
                String.class, "rework-001");

        mockMvc.perform(post("/api/v2/material-reworks/%s/complete".formatted(reworkId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"replacementSlideId\":\"%s\"}".formatted(replacementSlide)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status_code FROM pis_v2.material_rework WHERE id = ?",
                String.class, UUID.fromString(reworkId))).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE target_object_kind_code = 'V2-MATERIAL-REWORK'",
                Integer.class)).isGreaterThanOrEqualTo(2);
    }

    private String createCase(String suffix) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                         "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-%s",
                         "visitReference":"SYNTH-VISIT-001","idempotencyKey":"case-%s"}
                        """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("caseId").asText();
    }

    private String createSpecimen(String caseId, String specimenCode, String suffix) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","specimenCode":"%s","specimenKindCode":"TISSUE",
                         "sourceKindCode":"LOCAL","sourceReference":"SYNTH-%s","collectionSite":"synthetic site",
                         "collectionMethodCode":"SURGICAL","labelCode":"LABEL-%s","idempotencyKey":"%s"}
                        """.formatted(caseId, specimenCode, suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("specimenId").asText();
    }

    private String createGrossing(String caseId, String suffix) throws Exception {
        return createGrossing(caseId, suffix, "INITIAL", null);
    }

    private String createGrossing(String caseId, String suffix, String sourceType, String sourceReferenceId)
            throws Exception {
        String sourceReference = sourceReferenceId == null ? ""
                : ",\"sourceReferenceId\":\"" + sourceReferenceId + "\"";
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v2/cases/%s/grossings".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceType":"%s"%s,"grossDescription":"synthetic gross description",
                         "grossingInstruction":"synthetic instruction","grossingDoctorId":"SYNTH-DOCTOR",
                         "recorderId":"SYNTH-RECORDER","idempotencyKey":"%s"}
                        """.formatted(sourceType, sourceReference, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("grossingId").asText();
    }

    private void associateSpecimen(String grossingId, String specimenId, String suffix) throws Exception {
        mockMvc.perform(post("/api/v2/grossings/%s/specimens".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"materialDescription\":\"synthetic material\",\"idempotencyKey\":\"%s\"}"
                        .formatted(specimenId, suffix)))
                .andExpect(status().isOk());
    }

    private String createBlock(String grossingId, String specimenId, String blockCode, String suffix) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"%s\",\"blockType\":\"ROUTINE\",\"idempotencyKey\":\"%s\"}"
                        .formatted(specimenId, blockCode, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("blockId").asText();
    }

    private JsonNode completeGrossing(String grossingId, long expectedVersion, String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v2/grossings/%s/complete".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":%d,\"idempotencyKey\":\"%s\"}".formatted(expectedVersion, suffix)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
