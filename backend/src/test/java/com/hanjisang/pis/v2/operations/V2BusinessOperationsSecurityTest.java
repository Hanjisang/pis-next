package com.hanjisang.pis.v2.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;

@SpringBootTest(properties = "pis.require-auth=true")
@ActiveProfiles("test")
@Sql("classpath:v2-i01-test-schema.sql")
class V2BusinessOperationsSecurityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationSessionStore sessions;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(context.getBean(AuthenticationSessionFilter.class)).build();
    }

    @Test
    void ordinaryUserCannotReadOrWriteAdministrationCapabilities() throws Exception {
        Cookie registrar = cookie("registrar", "HOSPITAL_A", Set.of("P14-PERM-004", "P14-PERM-048"));
        mockMvc.perform(get("/api/v2/operations/overview").cookie(registrar)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v2/operations/equipment").cookie(registrar).contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentCode\":\"DENIED-EQ\",\"name\":\"拒绝设备\",\"categoryCode\":\"OTHER\",\"statusCode\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hospitalScopeIsAppliedToReadsAndReferencedWrites() throws Exception {
        Cookie hospitalA = cookie("admin-a", "HOSPITAL_A", Set.of("P14-PERM-001", "P14-PERM-048"));
        Cookie hospitalB = cookie("admin-b", "HOSPITAL_B", Set.of("P14-PERM-001", "P14-PERM-048"));
        String equipmentId = id(mockMvc.perform(post("/api/v2/operations/equipment").cookie(hospitalA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentCode\":\"A-EQ\",\"name\":\"A院设备\",\"categoryCode\":\"OTHER\",\"statusCode\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andReturn());
        String overviewB = mockMvc.perform(get("/api/v2/operations/overview").cookie(hospitalB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(overviewB).doesNotContain("A-EQ");
        mockMvc.perform(post("/api/v2/operations/equipment/{id}/events", equipmentId).cookie(hospitalB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"eventCode\":\"FAULT\",\"description\":\"cross scope\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void inventoryBoundaryAndProcurementValidationAreEnforced() throws Exception {
        Cookie admin = cookie("admin-stock", "HOSPITAL_STOCK", Set.of("P14-PERM-001", "P14-PERM-014", "P14-PERM-048"));
        String catalogId = id(mockMvc.perform(post("/api/v2/operations/consumables/catalog").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialCode\":\"STOCK-CAT\",\"name\":\"库存试剂\",\"categoryCode\":\"IHC\",\"unitCode\":\"TEST\",\"hazardous\":false}"))
                .andExpect(status().isOk()).andReturn());
        String batchId = id(mockMvc.perform(post("/api/v2/operations/consumables/catalog/{id}/batches", catalogId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"batchNo\":\"B1\"}"))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/v2/operations/consumables/batches/{id}/transactions", batchId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"directionCode\":\"OUTBOUND\",\"quantity\":1,\"reason\":\"无库存出库\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v2/operations/procurement/requests").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestNo\":\"P-EMPTY\",\"departmentReference\":\"PATHOLOGY\",\"reason\":\"缺少项目\",\"items\":[]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void failedDistributionIsRecordedWithoutChangingSignedReport() throws Exception {
        String organization = "HOSPITAL_REPORT";
        Cookie reporter = cookie("reporter", organization, Set.of("P14-PERM-055", "P14-PERM-048"));
        UUID reportId = insertSignedReport(organization);
        String distributionId = id(mockMvc.perform(post("/api/v2/operations/reports/{id}/distribution", reportId).cookie(reporter)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetCode\":\"EXTERNAL_CHANNEL\",\"idempotencyKey\":\"report-delivery-failure\"}"))
                .andExpect(status().isOk()).andReturn());
        mockMvc.perform(post("/api/v2/operations/report-distributions/{id}/status", distributionId).cookie(reporter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FAILED\",\"error\":\"模拟通道超时\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status_code FROM pis_v2.report WHERE id = ?", String.class, reportId)).isEqualTo("EFFECTIVE");
        assertThat(jdbc.queryForObject("SELECT last_error FROM pis_v2.report_distribution WHERE id = ?", String.class, UUID.fromString(distributionId))).isEqualTo("模拟通道超时");

        String printBody = """
                {"identityReference":"SYNTHETIC-IDENTITY","terminalReference":"SELF-SERVICE-01",
                 "printerReference":"MOCK://REPORT-PRINTER","copyCount":1,
                 "idempotencyKey":"report-print-success"}
                """;
        mockMvc.perform(post("/api/v2/operations/reports/{id}/print", reportId).cookie(reporter)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identityReference\":\"WRONG-IDENTITY\",\"terminalReference\":\"SELF-SERVICE-01\",\"printerReference\":\"MOCK://REPORT-PRINTER\",\"copyCount\":1,\"idempotencyKey\":\"report-print-denied\"}"))
                .andExpect(status().isUnprocessableEntity());
        String firstPrint = mockMvc.perform(post("/api/v2/operations/reports/{id}/print", reportId).cookie(reporter)
                .contentType(MediaType.APPLICATION_JSON).content(printBody)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(firstPrint).contains("SUCCESS").contains("\"duplicate\":false");
        String replayPrint = mockMvc.perform(post("/api/v2/operations/reports/{id}/print", reportId).cookie(reporter)
                .contentType(MediaType.APPLICATION_JSON).content(printBody)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(replayPrint).contains("REPLAYED").contains("\"duplicate\":true");
        assertThat(mockMvc.perform(get("/api/v2/operations/reports/{id}/prints", reportId).cookie(reporter))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("SIM-PRINT-").contains("SELF-SERVICE-01");
        String simulatedDelivery = mockMvc.perform(post("/api/v2/operations/reports/{id}/distribution", reportId)
                .cookie(reporter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetCode\":\"SIMULATOR_PATIENT_PORTAL\",\"idempotencyKey\":\"report-delivery-success\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(simulatedDelivery).contains("SENT").contains("\"duplicate\":false");
        assertThat(mockMvc.perform(get("/api/v2/operations/reports/{id}/distributions", reportId).cookie(reporter))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("EXTERNAL_CHANNEL").contains("模拟通道超时")
                .contains("SIMULATOR_PATIENT_PORTAL").contains("SIM-DISTRIBUTION-");
        assertThat(mockMvc.perform(get("/api/v2/operations/report-printer-status")
                .param("printerReference", "MOCK://REPORT-PRINTER").cookie(reporter))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains("READY");
        Cookie otherHospital = cookie("reporter-b", "HOSPITAL_REPORT_B", Set.of("P14-PERM-055", "P14-PERM-048"));
        mockMvc.perform(get("/api/v2/operations/reports/{id}/prints", reportId).cookie(otherHospital))
                .andExpect(status().isUnprocessableEntity());
    }

    private Cookie cookie(String username, String hospital, Set<String> permissions) {
        UUID id = UUID.randomUUID();
        String token = sessions.create(new AuthenticatedUser(id, username, username, "TEST", hospital, "PATHOLOGY",
                "ADMINISTRATION", permissions, null, null));
        return new Cookie(AuthenticationSessionFilter.COOKIE_NAME, token);
    }

    private String id(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID insertSignedReport(String organization) {
        UUID caseId = UUID.randomUUID(); UUID reportId = UUID.randomUUID(); UUID businessType = UUID.randomUUID();
        jdbc.update("INSERT INTO pis_v2.business_type (id,business_type_code,display_name,modality_code,active,configuration_version,created_at,created_by_ref) VALUES (?,?,?,?,TRUE,1,CURRENT_TIMESTAMP,'TEST')",
                businessType, "BT-" + caseId, "测试", "HISTOLOGY");
        jdbc.update("INSERT INTO pis_v2.pathology_case (id,case_no,source_system_code,external_application_id,application_item_code,business_type_id,lifecycle_state_code,number_binding_active,concurrency_version,organization_reference,created_at,created_by_ref) VALUES (?,?,?,?,?,?,'ACTIVE',TRUE,0,?,CURRENT_TIMESTAMP,'TEST')",
                caseId, "CASE-" + caseId, "TEST", "APP-" + caseId, "ITEM", businessType, organization);
        jdbc.update("INSERT INTO pis_v2.case_context_snapshot (id, case_id, patient_reference, snapshot_version_no, captured_at, captured_by_ref) VALUES (?, ?, 'SYNTHETIC-IDENTITY', 1, CURRENT_TIMESTAMP, 'TEST')",
                UUID.randomUUID(), caseId);
        jdbc.update("INSERT INTO pis_v2.report (id,report_no,organization_reference,case_id,diagnosis_id,template_version_id,report_nature_code,status_code,diagnosis_snapshot,responsibility_snapshot,case_snapshot,material_snapshot,technical_result_snapshot,rendered_content,rendered_content_hash,pdf_file_reference,pdf_content_hash,signed_by_ref,signed_at,concurrency_version,created_at,created_by_ref) VALUES (?,?,?,?,?,?,'ORIGINAL','EFFECTIVE','{}','{}','{}','{}','{}','signed','h','fixture://report','p','TEST',CURRENT_TIMESTAMP,0,CURRENT_TIMESTAMP,'TEST')",
                reportId, "R-" + reportId, organization, caseId, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("INSERT INTO pis_v2.report_pdf_output (id, report_id, file_reference, content, content_hash, created_at, created_by_ref) VALUES (?, ?, ?, ?, 'p', CURRENT_TIMESTAMP, 'TEST')",
                UUID.randomUUID(), reportId, "fixture://report/" + reportId, "%PDF-1.4 synthetic".getBytes());
        return reportId;
    }
}
