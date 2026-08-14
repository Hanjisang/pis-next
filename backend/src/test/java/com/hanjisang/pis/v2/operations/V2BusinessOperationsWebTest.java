package com.hanjisang.pis.v2.operations;

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
class V2BusinessOperationsWebTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build(); }

    @Test
    void supportingBusinessFactsPersistWithAuditAndDerivedStock() throws Exception {
        String schedule = body(post("/api/v2/operations/staff-schedules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffReference\":\"SYNTH-TECH\",\"scheduleDate\":\"2026-08-12\",\"shiftCode\":\"DAY\",\"workArea\":\"制片\"}"));
        assertThat(UUID.fromString(json(schedule).get("id").asText())).isNotNull();
        assertThat(json(body(get("/api/v2/operations/staff-schedules?from=2026-08-01&to=2026-08-31")))).hasSize(1);

        String document = body(post("/api/v2/operations/quality-documents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"合成质量手册\",\"documentNo\":\"QM-001\",\"categoryCode\":\"MANUAL\",\"versionLabel\":\"1.0\",\"ownerReference\":\"SYNTH-QA\",\"contentReference\":\"fixture://quality/qm-001\"}"));
        String documentId = json(document).get("id").asText();
        body(post("/api/v2/operations/quality-documents/%s/REVIEW".formatted(documentId)));
        body(post("/api/v2/operations/quality-documents/%s/PUBLISHED".formatted(documentId)));

        String equipment = body(post("/api/v2/operations/equipment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentCode\":\"EQ-001\",\"name\":\"合成染色机\",\"categoryCode\":\"STAINER\",\"statusCode\":\"ACTIVE\"}"));
        String equipmentId = json(equipment).get("id").asText();
        body(post("/api/v2/operations/equipment/%s/events".formatted(equipmentId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventCode\":\"MAINTENANCE\",\"description\":\"定期保养\"}"));
        assertThat(json(body(get("/api/v2/operations/equipment/%s/events".formatted(equipmentId))))).hasSize(1);

        String catalog = body(post("/api/v2/operations/consumables/catalog").contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialCode\":\"REAGENT-CK7\",\"name\":\"CK7试剂\",\"categoryCode\":\"IHC\",\"unitCode\":\"TEST\",\"hazardous\":false}"));
        String catalogId = json(catalog).get("id").asText();
        String batch = body(post("/api/v2/operations/consumables/catalog/%s/batches".formatted(catalogId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchNo\":\"B-001\",\"storageLocation\":\"冷藏柜1\"}"));
        String batchId = json(batch).get("id").asText();
        body(post("/api/v2/operations/consumables/batches/%s/transactions".formatted(batchId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"directionCode\":\"INBOUND\",\"quantity\":10,\"reason\":\"入库\"}"));
        body(post("/api/v2/operations/consumables/batches/%s/transactions".formatted(batchId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"directionCode\":\"OUTBOUND\",\"quantity\":3,\"reason\":\"IHC消耗\"}"));
        assertThat(json(body(get("/api/v2/operations/consumables/stock"))).get(0).get("balance").decimalValue()).isEqualByComparingTo("7");

        String space = body(post("/api/v2/operations/spaces").contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceCode\":\"ROOM-1\",\"name\":\"制片室\",\"zoneCode\":\"CLEAN\",\"areaValue\":20}"));
        String spaceId = json(space).get("id").asText();
        body(post("/api/v2/operations/spaces/%s/environment".formatted(spaceId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"metricCode\":\"TEMPERATURE\",\"measureValue\":22.5,\"unitCode\":\"C\"}"));
        body(post("/api/v2/operations/spaces/%s/safety".formatted(spaceId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"checkCode\":\"FIRE\",\"resultCode\":\"PASS\"}"));

        String caseId = createCase();
        String critical = body(post("/api/v2/operations/cases/%s/critical-values".formatted(caseId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"valueTypeCode\":\"CRITICAL-CELL\",\"gradeCode\":\"HIGH\"}"));
        String criticalId = json(critical).get("id").asText();
        String notification = body(post("/api/v2/operations/critical-values/%s/notify".formatted(criticalId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentReference\":\"PATHOLOGY\",\"recipientReference\":\"p15-local-registration-actor\",\"methodCode\":\"PHONE\",\"message\":\"请确认危急值\",\"businessPath\":\"/v2/cases/%s\"}".formatted(caseId)));
        body(post("/api/v2/operations/critical-value-notifications/%s/acknowledge".formatted(json(notification).get("id").asText())));
        body(post("/api/v2/operations/critical-values/%s/feedback".formatted(criticalId)).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"已反馈临床\"}"));
        assertThat(json(body(get("/api/v2/operations/critical-values"))).get(0).get("statusCode").asText()).isEqualTo("COMPLETED");

        String molecularProject = body(post("/api/v2/operations/molecular/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectCode\":\"PCR\",\"projectName\":\"合成PCR\",\"projectTypeCode\":\"PCR\"}"));
        String molecularInstrument = body(post("/api/v2/operations/molecular/instruments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"instrumentCode\":\"SIM-PCR\",\"name\":\"合成PCR仪\",\"adapterCode\":\"SIMULATOR\"}"));
        String molecularReagent = body(post("/api/v2/operations/molecular/reagents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kitCode\":\"PCR-KIT\",\"manufacturer\":\"SYNTH\",\"batchNo\":\"B-001\",\"expiryDate\":\"2027-12-31\"}"));
        String specimenId = json(body(post("/api/v2/registration/specimens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"specimenCode\":\"M-1\",\"specimenKindCode\":\"TISSUE\",\"sourceKindCode\":\"LOCAL\",\"sourceReference\":\"SYNTH-MOL-SOURCE\",\"collectionSite\":\"synthetic site\",\"collectionMethodCode\":\"SURGICAL\",\"idempotencyKey\":\"ops-mol-specimen\"}".formatted(caseId)))).get("specimenId").asText();
        String molecularId = body(post("/api/v2/operations/molecular/tests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"specimenId\":\"%s\",\"projectId\":\"%s\",\"detectionNo\":\"M-001\",\"instrumentId\":\"%s\",\"reagentKitId\":\"%s\",\"rawDataReference\":\"fixture://molecular/raw-001\"}".formatted(caseId, specimenId, json(molecularProject).get("id").asText(), json(molecularInstrument).get("id").asText(), json(molecularReagent).get("id").asText()))).trim();
        assertThat(body(get("/api/v2/my-workbench"))).contains("MOLECULAR_PENDING", "M-001", "启动检测");
        body(post("/api/v2/operations/molecular/tests/%s/start".formatted(json(molecularId).get("id").asText())));
        body(post("/api/v2/operations/molecular/tests/%s/start".formatted(json(molecularId).get("id").asText())));
        body(post("/api/v2/operations/molecular/tests/%s/complete".formatted(json(molecularId).get("id").asText())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"structuredResult\":\"阴性\",\"analysisResult\":\"未见异常\"}"));
        body(post("/api/v2/operations/molecular/tests/%s/complete".formatted(json(molecularId).get("id").asText())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"structuredResult\":\"阴性\",\"analysisResult\":\"未见异常\"}"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.molecular_result WHERE case_id=?", Integer.class,
                UUID.fromString(caseId))).isEqualTo(1);
        assertThat(body(get("/api/v2/molecular/workbench"))).contains("M-001", "COMPLETED", "SIM-PCR", "PCR-KIT");
        assertThat(body(get("/api/v2/diagnosis-workspaces/%s".formatted(caseId))))
                .contains("M-001", "structuredResult", "analysisResult");
        String autoPayload = "{\"caseId\":\"%s\",\"specimenId\":\"%s\",\"projectId\":\"%s\",\"instrumentId\":\"%s\",\"reagentKitId\":\"%s\",\"rawDataReference\":\"fixture://molecular/raw-auto\",\"idempotencyKey\":\"molecular-auto-create\"}".formatted(caseId, specimenId, json(molecularProject).get("id").asText(), json(molecularInstrument).get("id").asText(), json(molecularReagent).get("id").asText());
        JsonNode autoCreated = json(body(post("/api/v2/molecular/tests").contentType(MediaType.APPLICATION_JSON).content(autoPayload)));
        JsonNode autoReplay = json(body(post("/api/v2/molecular/tests").contentType(MediaType.APPLICATION_JSON).content(autoPayload)));
        assertThat(autoReplay.get("id").asText()).isEqualTo(autoCreated.get("id").asText());
        assertThat(autoReplay.get("duplicate").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT detection_no FROM pis_v2.molecular_test WHERE id=?", String.class,
                UUID.fromString(autoCreated.get("id").asText()))).startsWith("MOL-");
        body(post("/api/v2/molecular/tests/%s/attachments".formatted(autoCreated.get("id").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentReference\":\"fixture://molecular/support-auto.pdf\",\"description\":\"合成附件\"}"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.molecular_test_attachment WHERE molecular_test_id=?", Integer.class,
                UUID.fromString(autoCreated.get("id").asText()))).isEqualTo(1);
        String externalInstrument = body(post("/api/v2/operations/molecular/instruments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"instrumentCode\":\"REAL-PCR\",\"name\":\"待联调PCR仪\",\"adapterCode\":\"VENDOR-A\"}"));
        String externalTest = body(post("/api/v2/operations/molecular/tests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"specimenId\":\"%s\",\"projectId\":\"%s\",\"detectionNo\":\"M-EXT-001\",\"instrumentId\":\"%s\",\"reagentKitId\":\"%s\",\"rawDataReference\":\"fixture://molecular/raw-ext\"}".formatted(caseId, specimenId, json(molecularProject).get("id").asText(), json(externalInstrument).get("id").asText(), json(molecularReagent).get("id").asText())));
        mockMvc.perform(post("/api/v2/operations/molecular/tests/%s/start".formatted(json(externalTest).get("id").asText())))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.molecular_instrument_attempt WHERE molecular_test_id=? AND status_code='FAILED'", Integer.class,
                UUID.fromString(json(externalTest).get("id").asText()))).isEqualTo(1);

        String job = body(post("/api/v2/operations/migration/jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceCode\":\"LEGACY-SYNTH\",\"modeCode\":\"READ_ONLY\",\"statusCode\":\"READ_ONLY\"}"));
        body(post("/api/v2/operations/migration/records").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":\"%s\",\"legacyType\":\"CASE\",\"legacyKey\":\"OLD-1\",\"recordStatus\":\"MAPPED\"}".formatted(json(job).get("id").asText())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE operation_code LIKE 'PIS-V2-%'", Integer.class)).isGreaterThan(0);
    }

    private String createCase() throws Exception {
        return json(body(post("/api/v2/registration/cases").contentType(MediaType.APPLICATION_JSON).content("""
                {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"OPS-CASE-1",
                 "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-PATIENT-OPS",
                 "visitReference":"SYNTH-VISIT-OPS","idempotencyKey":"ops-case"}
                """))).get("caseId").asText();
    }

    private String body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }
}
