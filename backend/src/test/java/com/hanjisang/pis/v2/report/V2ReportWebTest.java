package com.hanjisang.pis.v2.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
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
class V2ReportWebTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.update("DELETE FROM pis_v2.report_tat_policy");
    }

    @Test
    void previewDoesNotPersistAndSignOutWithdrawResignAndSupplementPreserveHistory() throws Exception {
        String caseId = createReadyCase("I05-LOOP");
        JsonNode claimed = json(mockMvc.perform(post("/api/v2/diagnoses/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"caseId\":\"%s\",\"idempotencyKey\":\"claim-i05\"}".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String diagnosisId = claimed.get("diagnosisId").asText();
        String auditId = complete(diagnosisId, "/complete-initial", claimed.get("responsibilityId").asText(), 0, 0,
                "AUDIT", 1, "complete-i05-initial").get("nextResponsibilityId").asText();
        complete(diagnosisId, "/complete-audit", auditId, 0, 1, null, 2, "complete-i05-audit");

        JsonNode configured = json(mockMvc.perform(put("/api/v2/configuration/tat-policies/{businessTypeId}",
                        "00000000-0000-0000-0000-00000000b001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warningMinutes\":60,\"targetMinutes\":120,\"enabled\":true,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(configured.get("reportTatPolicies").toString()).contains("HISTOLOGY", "CASE_REGISTERED");
        mockMvc.perform(put("/api/v2/configuration/tat-policies/{businessTypeId}",
                        "00000000-0000-0000-0000-00000000b001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warningMinutes\":60,\"targetMinutes\":180,\"enabled\":true,\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        jdbcTemplate.update("UPDATE pis_v2.pathology_case SET created_at = DATEADD('HOUR', -3, CURRENT_TIMESTAMP) WHERE id = ?",
                UUID.fromString(caseId));
        JsonNode reportCenter = json(mockMvc.perform(get("/api/v2/report-center"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reportCenter.get("counts").get("overdue").asInt()).isEqualTo(1);
        assertThat(reportCenter.get("items").get(0).get("tatStatus").asText()).isEqualTo("OVERDUE");
        JsonNode delay = json(mockMvc.perform(post("/api/v2/report-center/delays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"diagnosisId":"%s","reasonCode":"TECHNICAL_WORK",
                         "reasonDetail":"合成技术工作待完成","expectedSignAt":"%s",
                         "idempotencyKey":"delay-i05"}
                        """.formatted(diagnosisId, Instant.now().plusSeconds(86400))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(delay.get("duplicate").asBoolean()).isFalse();
        JsonNode delayReplay = json(mockMvc.perform(post("/api/v2/report-center/delays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"diagnosisId":"%s","reasonCode":"TECHNICAL_WORK",
                         "reasonDetail":"合成技术工作待完成","expectedSignAt":"%s",
                         "idempotencyKey":"delay-i05"}
                        """.formatted(diagnosisId, delay.get("expectedSignAt").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(delayReplay.get("duplicate").asBoolean()).isTrue();
        mockMvc.perform(post("/api/v2/report-center/delays/{delayId}/resolve", delay.get("delayId").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resolutionNote":"合成人工关闭","idempotencyKey":"delay-resolve-i05"}
                        """))
                .andExpect(status().isOk());
        JsonNode activeDelay = json(mockMvc.perform(post("/api/v2/report-center/delays")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"diagnosisId":"%s","reasonCode":"CONSULTATION",
                         "reasonDetail":"合成会诊待完成","expectedSignAt":"%s",
                         "idempotencyKey":"delay-i05-second"}
                        """.formatted(diagnosisId, Instant.now().plusSeconds(172800))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(activeDelay.get("duplicate").asBoolean()).isFalse();
        assertThat(json(mockMvc.perform(get("/api/v2/statistics/summary"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("reportTat").get("activeOverdue").asInt()).isEqualTo(1);

        JsonNode presets = json(mockMvc.perform(get("/api/v2/report-template-presets"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(presets.toString()).contains("TUMOR-LUNG", "肺肿瘤报告结构");
        JsonNode instantiated = json(mockMvc.perform(post("/api/v2/report-template-presets/TUMOR-LUNG/instantiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"SYNTH-LUNG-REPORT","name":"合成肺肿瘤报告模板",
                         "businessTypeId":"00000000-0000-0000-0000-00000000b001"}
                        """))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String templateId = instantiated.get("template").get("templateId").asText();
        mockMvc.perform(post("/api/v2/report-templates/%s/versions".formatted(templateId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definition\":\"{}\"}"))
                .andExpect(status().isUnprocessableEntity());
        String designedDefinition = objectMapper.writeValueAsString(Map.of(
                "schemaVersion", 1,
                "title", "合成肺肿瘤专科报告",
                "category", "TUMOR",
                "tumorSiteCode", "LUNG",
                "page", Map.of("size", "A4", "showPageNumber", true),
                "sections", List.of(
                        Map.of("code", "MICROSCOPY", "label", "镜下所见", "source", "DIAGNOSIS",
                                "fields", List.of("microscopicDescription")),
                        Map.of("code", "DIAGNOSIS", "label", "病理诊断", "source", "DIAGNOSIS",
                                "fields", List.of("diagnosisText", "structuredData")),
                        Map.of("code", "SIGNATURE", "label", "签发信息", "source", "SIGNATURE",
                                "fields", List.of("signedBy", "signedAt")))));
        JsonNode designedVersion = json(mockMvc.perform(post("/api/v2/report-templates/%s/versions".formatted(templateId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("definition", designedDefinition))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String designedVersionId = designedVersion.get("versionId").asText();
        mockMvc.perform(post("/api/v2/report-template-versions/%s/publish".formatted(designedVersionId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"publish-synth-lung-v2\"}"))
                .andExpect(status().isOk());
        JsonNode catalog = json(mockMvc.perform(get("/api/v2/report-templates"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(catalog.toString()).contains("SYNTH-LUNG-REPORT", "TUMOR-LUNG", designedVersionId);

        JsonNode preview = json(mockMvc.perform(get("/api/v2/diagnoses/%s/report-preview".formatted(diagnosisId))
                .param("templateVersionId", designedVersionId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.get("valid").asBoolean()).isTrue();
        assertThat(preview.get("renderedContent").asText()).contains("合成肺肿瘤专科报告", "tumorSiteCode");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.report", Integer.class)).isZero();

        JsonNode first = json(mockMvc.perform(post("/api/v2/diagnoses/%s/sign-out".formatted(diagnosisId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateVersionId\":\"%s\",\"idempotencyKey\":\"sign-i05-r001\"}"
                        .formatted(designedVersionId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.get("reportNo").asText()).isEqualTo("R001");
        assertThat(first.get("status").asText()).isEqualTo("EFFECTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis_v2.report_delay_declaration
                WHERE diagnosis_id = ? AND resolved_at IS NOT NULL
                """, Integer.class, UUID.fromString(diagnosisId))).isEqualTo(2);
        String reportId = first.get("reportId").asText();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.report_pdf_output", Integer.class)).isEqualTo(1);
        byte[] formalPdf = mockMvc.perform(get("/api/v2/reports/%s/pdf".formatted(reportId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(formalPdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(formalPdf)) {
            assertThat(document.isEncrypted()).isTrue();
            assertThat(document.getCurrentAccessPermission().canModify()).isFalse();
        }

        mockMvc.perform(post("/api/v2/reports/%s/pdf-encrypted".formatted(reportId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessPassword\":\"short\",\"reason\":\"synthetic delivery\"}"))
                .andExpect(status().isUnprocessableEntity());
        byte[] protectedPdf = mockMvc.perform(post("/api/v2/reports/%s/pdf-encrypted".formatted(reportId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessPassword\":\"synthetic-safe-2026\",\"reason\":\"synthetic delivery\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThatThrownByInvalidPassword(protectedPdf);
        try (PDDocument document = Loader.loadPDF(protectedPdf, "synthetic-safe-2026")) {
            assertThat(document.isEncrypted()).isTrue();
            assertThat(document.getCurrentAccessPermission().canModify()).isFalse();
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pis.audit_event
                WHERE operation_code = 'PIS-V2-REPORT-PDF-ENCRYPTED-DOWNLOAD'
                """, Integer.class)).isEqualTo(1);

        JsonNode withdrawn = json(mockMvc.perform(post("/api/v2/reports/%s/withdraw".formatted(reportId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"synthetic correction\",\"idempotencyKey\":\"withdraw-i05\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(withdrawn.get("status").asText()).isEqualTo("WITHDRAWN");
        mockMvc.perform(post("/api/v2/reports/%s/pdf-encrypted".formatted(reportId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessPassword\":\"synthetic-safe-2026\",\"reason\":\"synthetic delivery\"}"))
                .andExpect(status().isUnprocessableEntity());
        JsonNode reopened = json(mockMvc.perform(get("/api/v2/diagnosis-workspaces/%s".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reopened.get("currentResponsibility").get("role").asText()).isEqualTo("AUDIT");
        assertThat(reopened.get("responsibilityChain")).hasSize(2);
        JsonNode withdrawalQueue = json(mockMvc.perform(get("/api/v2/my-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(withdrawalQueue.get("counts").get("withdrawnReport").asInt()).isEqualTo(1);
        assertThat(withdrawalQueue.get("myWork").toString()).contains("WITHDRAWN_REPORT_REQUIRES_ATTENTION");
        assertThat(jdbcTemplate.queryForObject("SELECT pdf_content_hash FROM pis_v2.report WHERE id = ?", String.class,
                UUID.fromString(reportId))).isNotBlank();

        JsonNode current = reopened.get("currentResponsibility");
        JsonNode auditCompleted = complete(diagnosisId, "/complete-audit", current.get("responsibilityId").asText(),
                current.get("version").asLong(), reopened.get("diagnosis").get("version").asLong(), null,
                3, "complete-i05-resign-audit");
        assertThat(auditCompleted.get("readyForSignOut").asBoolean()).isTrue();
        JsonNode second = json(mockMvc.perform(post("/api/v2/diagnoses/%s/sign-out".formatted(diagnosisId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"sign-i05-r002\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(second.get("reportNo").asText()).isEqualTo("R002");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.report", Integer.class)).isEqualTo(2);

        JsonNode supplemental = json(mockMvc.perform(post("/api/v2/diagnoses/%s/supplemental".formatted(diagnosisId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"synthetic supplemental result\",\"idempotencyKey\":\"supplement-i05\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(supplemental.get("reportNo").asText()).isEqualTo("S001");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.report WHERE status_code = 'EFFECTIVE'",
                Integer.class)).isEqualTo(2);
        JsonNode history = json(mockMvc.perform(get("/api/v2/cases/%s/reports".formatted(caseId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(history).hasSize(3);
        assertThat(history.get(0).get("reportNo").asText()).isEqualTo("S001");
    }

    private JsonNode complete(String diagnosisId, String path, String responsibilityId, long responsibilityVersion,
            long diagnosisVersion, String nextRole, long ignoredVersion, String key) throws Exception {
        String nextRoleJson = nextRole == null ? "null" : "\"%s\"".formatted(nextRole);
        String nextDoctorJson = nextRole == null ? "null" : "\"p15-local-registration-actor\"";
        return json(mockMvc.perform(post("/api/v2/diagnoses/%s%s".formatted(diagnosisId, path))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"responsibilityId":"%s","responsibilityExpectedVersion":%d,
                         "structuredData":"{}","microscopicDescription":"synthetic microscopic",
                         "diagnosisText":"synthetic diagnosis","comment":"synthetic",
                         "diagnosisExpectedVersion":%d,"nextRole":%s,"nextDoctorId":%s,
                         "nextReason":"synthetic next responsibility","idempotencyKey":"%s"}
                        """.formatted(responsibilityId, responsibilityVersion, diagnosisVersion, nextRoleJson,
                        nextDoctorJson, key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String createReadyCase(String suffix) throws Exception {
        JsonNode caseBody = json(mockMvc.perform(post("/api/v2/registration/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceSystemCode":"SYNTH-HIS","externalApplicationId":"%s",
                         "applicationItemCode":"SYNTH-HISTOLOGY","patientReference":"SYNTH-%s",
                         "visitReference":"SYNTH-VISIT","idempotencyKey":"case-%s"}
                        """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String caseId = caseBody.get("caseId").asText();
        JsonNode specimen = json(mockMvc.perform(post("/api/v2/registration/specimens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"caseId":"%s","specimenCode":"A","specimenKindCode":"TISSUE","sourceKindCode":"LOCAL",
                         "sourceReference":"SYNTH-%s","collectionSite":"synthetic site","collectionMethodCode":"SURGICAL",
                         "labelCode":"LABEL-%s","idempotencyKey":"specimen-%s"}
                        """.formatted(caseId, suffix, suffix, suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode grossing = json(mockMvc.perform(post("/api/v2/cases/%s/grossings".formatted(caseId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceType":"INITIAL","grossDescription":"synthetic grossing","grossingDoctorId":"doctor-gross",
                         "recorderId":"recorder","idempotencyKey":"grossing-%s"}
                        """.formatted(suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v2/grossings/%s/specimens".formatted(grossing.get("grossingId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"materialDescription\":\"synthetic\",\"idempotencyKey\":\"associate-%s\"}"
                        .formatted(specimen.get("specimenId").asText(), suffix)))
                .andExpect(status().isOk());
        JsonNode block = json(mockMvc.perform(post("/api/v2/grossings/%s/blocks".formatted(grossing.get("grossingId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specimenId\":\"%s\",\"blockCode\":\"A1\",\"blockType\":\"ROUTINE\",\"idempotencyKey\":\"block-%s\"}"
                        .formatted(specimen.get("specimenId").asText(), suffix)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v2/grossings/%s/complete".formatted(grossing.get("grossingId").asText()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-grossing-%s\"}".formatted(suffix)))
                .andExpect(status().isOk());
        String slideId = jdbcTemplate.queryForObject("SELECT id FROM pis_v2.slide WHERE block_id = ?", String.class,
                UUID.fromString(block.get("blockId").asText()));
        mockMvc.perform(post("/api/v2/slides/%s/complete".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"complete-slide-%s\"}".formatted(suffix)))
                .andExpect(status().isOk());
        return caseId;
    }

    private JsonNode json(String value) throws Exception { return objectMapper.readTree(value); }

    private static void assertThatThrownByInvalidPassword(byte[] content) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Loader.loadPDF(content))
                .isInstanceOf(InvalidPasswordException.class);
    }
}
