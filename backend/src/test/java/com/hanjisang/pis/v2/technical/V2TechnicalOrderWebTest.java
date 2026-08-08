package com.hanjisang.pis.v2.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class V2TechnicalOrderWebTest {

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
    void projectConfigurationSupportsMultipleItemsTargetsAndRejectsCrossCaseTargets() throws Exception {
        CaseSetup first = createReadyCase("I04-MULTI-A");
        CaseSetup other = createReadyCase("I04-MULTI-B");
        String diagnosisId = claim(first.caseId(), "claim-i04-multi").get("diagnosisId").asText();
        JsonNode projects = json(mockMvc.perform(get("/api/v2/technical-projects").queryParam("caseId", first.caseId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(projects).hasSize(3);

        String ihc = projectId("IHC-KI67");
        String molecular = projectId("MOLECULAR-STRUCTURED");
        JsonNode order = createOrder("order-i04-multi", diagnosisId, true,
                item(ihc, "BLOCK", first.blockId(), "SLIDE", first.initialSlideId()),
                item(molecular, "CASE", first.caseId()));
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        assertThat(order.get("items")).hasSize(2);
        assertThat(itemByProject(order, "IHC-KI67").get("targets")).hasSize(2);
        assertThat(order.get("blocking").asBoolean()).isTrue();

        mockMvc.perform(post("/api/v2/technical-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"diagnosisId":"%s","requiredBeforeSignOut":true,"items":[%s],"idempotencyKey":"order-i04-cross-case"}
                        """.formatted(diagnosisId, item(ihc, "BLOCK", other.blockId()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void technicalSlideUsesOfficialSlideAndDoesNotReplaceInitialProduction() throws Exception {
        CaseSetup setup = createReadyCase("I04-SLIDE");
        String diagnosisId = claim(setup.caseId(), "claim-i04-slide").get("diagnosisId").asText();
        JsonNode order = createOrder("order-i04-slide", diagnosisId, true,
                item(projectId("IHC-KI67"), "BLOCK", setup.blockId()));
        String orderId = order.get("orderId").asText();
        JsonNode executing = execute(orderId, "execute-i04-slide");
        JsonNode item = itemByProject(executing, "IHC-KI67");
        assertThat(executing.get("status").asText()).isEqualTo("EXECUTING");
        assertThat(item.get("outputs")).hasSize(1);
        String technicalSlideId = item.get("outputs").get(0).get("outputId").asText();
        assertThat(item.get("outputs").get(0).get("outputKind").asText()).isEqualTo("SLIDE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_context_type FROM pis_v2.slide WHERE id = ?", String.class,
                UUID.fromString(technicalSlideId))).isEqualTo("TECHNICAL_ORDER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.slide WHERE case_id = ? AND source_context_type = 'INITIAL'",
                Integer.class, setup.caseId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.slide WHERE case_id = ? AND source_context_type = 'INITIAL' AND completed_at IS NOT NULL",
                Integer.class, setup.caseId())).isEqualTo(1);

        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(technicalSlideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-i04-technical-slide\"}"))
                .andExpect(status().isOk());
        JsonNode completed = json(mockMvc.perform(get("/api/v2/technical-orders/%s".formatted(orderId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("blocking").asBoolean()).isFalse();
        assertThat(completed.get("items").get(0).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void supplementaryGrossingCreatesFormalGrossingBlockAndSlideFacts() throws Exception {
        CaseSetup setup = createReadyCase("I04-SUPPLEMENTARY");
        String diagnosisId = claim(setup.caseId(), "claim-i04-supplementary").get("diagnosisId").asText();
        JsonNode created = createOrder("order-i04-supplementary", diagnosisId, true,
                item(projectId("SUPPLEMENTARY-GROSSING"), "SPECIMEN", setup.specimenId()));
        JsonNode executed = execute(created.get("orderId").asText(), "execute-i04-supplementary");
        JsonNode outputs = executed.get("items").get(0).get("outputs");
        assertThat(outputs).hasSize(3);
        assertThat(outputs.toString()).contains("GROSSING", "BLOCK", "SLIDE");
        String itemId = executed.get("items").get(0).get("itemId").asText();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.grossing WHERE case_id = ? AND source_type = 'TECHNICAL_ORDER' AND source_reference_id = ?",
                Integer.class, setup.caseId(), UUID.fromString(itemId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.block b JOIN pis_v2.grossing g ON g.id = b.grossing_id WHERE b.case_id = ? AND g.source_type = 'TECHNICAL_ORDER'",
                Integer.class, setup.caseId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.slide WHERE case_id = ? AND source_context_type = 'TECHNICAL_ORDER'",
                Integer.class, setup.caseId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.slide WHERE case_id = ? AND source_context_type = 'INITIAL' AND completed_at IS NOT NULL",
                Integer.class, setup.caseId())).isEqualTo(1);
    }

    @Test
    void structuredResultCompletesItemAndIsVisibleInDiagnosisWorkspaceAndWorkbench() throws Exception {
        CaseSetup setup = createReadyCase("I04-RESULT");
        String diagnosisId = claim(setup.caseId(), "claim-i04-result").get("diagnosisId").asText();
        JsonNode created = createOrder("order-i04-result", diagnosisId, true,
                item(projectId("MOLECULAR-STRUCTURED"), "CASE", setup.caseId()));
        String itemId = created.get("items").get(0).get("itemId").asText();
        JsonNode result = json(mockMvc.perform(post("/api/v2/technical-order-items/%s/result".formatted(itemId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultData\":\"{}\",\"expectedVersion\":0,\"idempotencyKey\":\"result-i04-001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.get("items").get(0).get("result").get("resultData").asText()).isEqualTo("{}");

        JsonNode workspace = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/%s".formatted(setup.caseId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workspace.get("technicalOrders")).hasSize(1);
        assertThat(workspace.get("blockingTechnicalOrderCount").asInt()).isZero();
        JsonNode workbench = json(mockMvc.perform(get("/api/v2/technical-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(workbench.get("orders")).isEmpty();
    }

    @Test
    void cancellationReleasesBlockingButPreservesFactsAndCompletedOrderCannotBeCancelled() throws Exception {
        CaseSetup setup = createReadyCase("I04-CANCEL");
        String diagnosisId = claim(setup.caseId(), "claim-i04-cancel").get("diagnosisId").asText();
        JsonNode created = createOrder("order-i04-cancel", diagnosisId, true,
                item(projectId("IHC-KI67"), "BLOCK", setup.blockId()));
        JsonNode executed = execute(created.get("orderId").asText(), "execute-i04-cancel");
        JsonNode cancelled = json(mockMvc.perform(post("/api/v2/technical-orders/%s/cancel".formatted(created.get("orderId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"expectedVersion":0,"reason":"synthetic operator cancellation","idempotencyKey":"cancel-i04-001"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("blocking").asBoolean()).isFalse();
        assertThat(cancelled.get("items").get(0).get("outputs")).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE id = ?", Integer.class,
                UUID.fromString(executed.get("items").get(0).get("outputs").get(0).get("outputId").asText()))).isEqualTo(1);

        JsonNode completedOrder = createOrder("order-i04-completed", diagnosisId, true,
                item(projectId("MOLECULAR-STRUCTURED"), "CASE", setup.caseId()));
        String completedItemId = completedOrder.get("items").get(0).get("itemId").asText();
        mockMvc.perform(post("/api/v2/technical-order-items/%s/result".formatted(completedItemId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultData\":\"{}\",\"expectedVersion\":0,\"idempotencyKey\":\"result-i04-cancel-completed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/technical-orders/%s/cancel".formatted(completedOrder.get("orderId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"reason\":\"late cancellation\",\"idempotencyKey\":\"cancel-i04-completed\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private JsonNode claim(UUID caseId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"%s\"}".formatted(caseId, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode createOrder(String key, String diagnosisId, boolean requiredBeforeSignOut, String... items)
            throws Exception {
        return json(mockMvc.perform(post("/api/v2/technical-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosisId\":\"%s\",\"requiredBeforeSignOut\":%s,\"items\":[%s],\"idempotencyKey\":\"%s\"}"
                        .formatted(diagnosisId, requiredBeforeSignOut, String.join(",", items), key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode execute(String orderId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v2/technical-orders/%s/execute".formatted(orderId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"%s\"}".formatted(key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode itemByProject(JsonNode order, String projectCode) {
        for (JsonNode item : order.get("items")) {
            if (projectCode.equals(item.get("projectCode").asText())) return item;
        }
        throw new AssertionError("technical item not found: " + projectCode);
    }

    private static String item(String projectId, Object... targetPairs) {
        StringBuilder targets = new StringBuilder();
        for (int index = 0; index < targetPairs.length; index += 2) {
            if (targets.length() > 0) targets.append(',');
            targets.append("{\"targetType\":\"").append(targetPairs[index]).append("\",\"targetId\":\"")
                    .append(targetPairs[index + 1]).append("\"}");
        }
        return "{\"projectId\":\"%s\",\"quantity\":1,\"parameters\":\"{}\",\"note\":\"synthetic technical item\",\"targets\":[%s]}"
                .formatted(projectId, targets);
    }

    private String projectId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM pis_v2.technical_project WHERE project_code = ?",
                String.class, code);
    }

    private CaseSetup createReadyCase(String suffix) throws Exception {
        JsonNode caseBody = json(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s","applicationItemCode":"SYNTH-HISTOLOGY",
                         "patientReference":"SYNTH-%s","visitReference":"SYNTH-VISIT","idempotencyKey":"case-%s"}
                        """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID caseId = UUID.fromString(caseBody.get("caseId").asText());
        JsonNode specimen = json(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","specimenCode":"A","specimenKindCode":"TISSUE","sourceKindCode":"LOCAL",
                         "sourceReference":"SYNTH-%s","collectionSite":"synthetic site","collectionMethodCode":"SURGICAL",
                         "labelCode":"LABEL-%s","idempotencyKey":"specimen-%s"}
                        """.formatted(caseId, suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID specimenId = UUID.fromString(specimen.get("specimenId").asText());
        JsonNode grossing = json(mockMvc.perform(post("/api/v2/cases/%s/grossings".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceType\":\"INITIAL\",\"grossDescription\":\"synthetic grossing\",\"grossingDoctorId\":\"doctor-gross\",\"recorderId\":\"recorder\",\"idempotencyKey\":\"grossing-%s\"}"
                        .formatted(suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID grossingId = UUID.fromString(grossing.get("grossingId").asText());
        mockMvc.perform(post("/api/v2/grossings/%s/specimens".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"materialDescription\":\"synthetic\",\"idempotencyKey\":\"associate-%s\"}"
                        .formatted(specimenId, suffix)))
                .andExpect(status().isOk());
        JsonNode block = json(mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"A1\",\"blockType\":\"ROUTINE\",\"idempotencyKey\":\"block-%s\"}"
                        .formatted(specimenId, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID blockId = UUID.fromString(block.get("blockId").asText());
        mockMvc.perform(post("/api/v2/grossings/%s/complete".formatted(grossingId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-grossing-%s\"}".formatted(suffix)))
                .andExpect(status().isOk());
        UUID initialSlideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", UUID.class,
                blockId);
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(initialSlideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-slide-%s\"}".formatted(suffix)))
                .andExpect(status().isOk());
        return new CaseSetup(caseId, specimenId, blockId, initialSlideId);
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }

    private record CaseSetup(UUID caseId, UUID specimenId, UUID blockId, UUID initialSlideId) { }
}
