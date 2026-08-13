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

import java.util.UUID;

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

        String firstItem = created.get("items").get(0).get("itemId").asText();
        String secondItem = created.get("items").get(1).get("itemId").asText();
        JsonNode delivery = verify(applicationId, firstItem, "SYNTH-LABEL-1");
        verify(applicationId, secondItem, "SYNTH-LABEL-2");
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
    void applicationItemsMapToIndependentHistologyCytologyFrozenAndMolecularCases() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationNo":"SYNTH-APP-BUSINESS-TYPES","sourceTypeCode":"MANUAL",
                         "sourceSystemCode":"PIS-MANUAL","patientReference":"SYNTH-PATIENT-APP",
                         "patientName":"Synthetic Patient","visitReference":"SYNTH-VISIT-TYPES",
                         "applicationDepartment":"SYNTH-DEPARTMENT","applicantReference":"SYNTH-DOCTOR",
                         "items":[
                           {"externalItemCode":"SYNTH-HISTOLOGY","itemName":"histology",
                            "specimenKindCode":"TISSUE","specimenDescription":"A","sequenceNo":1},
                           {"externalItemCode":"SYNTH-CYTOLOGY","itemName":"cytology",
                            "specimenKindCode":"FLUID","specimenDescription":"B","sequenceNo":2},
                           {"externalItemCode":"SYNTH-FROZEN","itemName":"frozen",
                            "specimenKindCode":"TISSUE","specimenDescription":"C","sequenceNo":3},
                           {"externalItemCode":"SYNTH-MOLECULAR","itemName":"molecular",
                            "specimenKindCode":"TISSUE","specimenDescription":"D","sequenceNo":4}]}
                        """))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        for (int index = 0; index < 4; index++) {
            verify(applicationId, created.get("items").get(index).get("itemId").asText(),
                    "SYNTH-APP-BUSINESS-TYPES-" + (index + 1));
        }

        JsonNode registered = json(mockMvc.perform(post("/api/v2/applications/{applicationId}/register", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(registered.get("createdCaseCount").asInt()).isEqualTo(4);
        assertThat(jdbcTemplate.queryForList("""
                SELECT bt.business_type_code
                FROM pis_v2.pathology_application_case ac
                JOIN pis_v2.pathology_case c ON c.id = ac.case_id
                JOIN pis_v2.business_type bt ON bt.id = c.business_type_id
                WHERE ac.application_id = ? ORDER BY bt.business_type_code
                """, String.class, java.util.UUID.fromString(applicationId)))
                .containsExactly("CYTOLOGY_NON_GYN", "FROZEN", "HISTOLOGY", "MOLECULAR");
        assertThat(registered.get("cases").findValuesAsText("caseNo"))
                .anyMatch(value -> value.startsWith("H-"))
                .anyMatch(value -> value.startsWith("C-"))
                .anyMatch(value -> value.startsWith("F-"))
                .anyMatch(value -> value.startsWith("M-"));
        JsonNode frozenCase = null;
        for (JsonNode item : registered.get("cases")) {
            if ("SYNTH-FROZEN".equals(item.path("externalItemCode").asText())) {
                frozenCase = item;
                break;
            }
        }
        assertThat(frozenCase).isNotNull();
        UUID frozenCaseId = UUID.fromString(frozenCase.get("caseId").asText());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pis_v2.frozen_round WHERE case_id = ?", Integer.class, frozenCaseId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pis_v2.frozen_round_specimen frs
                JOIN pis_v2.frozen_round fr ON fr.id = frs.frozen_round_id
                JOIN pis_v2.specimen s ON s.id = frs.specimen_id
                WHERE fr.case_id = ? AND s.case_id = ?
                """, Integer.class, frozenCaseId, frozenCaseId)).isEqualTo(1);
    }

    @Test
    void partialMultiItemRegistrationLeavesOnlyRemainingItemPending() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-PARTIAL")))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        String firstItemId = created.get("items").get(0).get("itemId").asText();
        String secondItemId = created.get("items").get(1).get("itemId").asText();
        verify(applicationId, firstItemId, "SYNTH-PARTIAL-1");

        JsonNode first = json(mockMvc.perform(post("/api/v2/applications/{applicationId}/items/{itemId}/register",
                applicationId, firstItemId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn());

        assertThat(first.get("createdCaseCount").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status_code FROM pis_v2.pathology_application WHERE id = ?",
                String.class, java.util.UUID.fromString(applicationId))).isEqualTo("PARTIALLY_REGISTERED");
        JsonNode queue = json(mockMvc.perform(get("/api/v2/applications/queue"))
                .andExpect(status().isOk()).andReturn());
        java.util.List<String> pendingIds = new java.util.ArrayList<>();
        for (JsonNode row : queue) {
            if ("PENDING".equals(row.path("itemStatusCode").asText())) {
                pendingIds.add(row.path("applicationItemId").asText());
            }
        }
        assertThat(pendingIds).contains(secondItemId).doesNotContain(firstItemId);

        mockMvc.perform(post("/api/v2/applications/{applicationId}/items/{itemId}/register",
                applicationId, firstItemId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void partiallyRegisteredApplicationCanUpdatePendingItemAndCancelWithoutDeletingCase() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-PARTIAL-EDIT")))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        String registeredItemId = created.get("items").get(0).get("itemId").asText();
        verify(applicationId, registeredItemId, "SYNTH-PARTIAL-EDIT-1");
        JsonNode registered = json(mockMvc.perform(post(
                "/api/v2/applications/{applicationId}/items/{itemId}/register", applicationId, registeredItemId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn());
        String caseId = registered.get("cases").get(0).get("caseId").asText();

        JsonNode updated = json(mockMvc.perform(put("/api/v2/applications/{applicationId}", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"clinicalDiagnosis":"corrected application-side diagnosis",
                         "items":[{"externalItemCode":"SYNTH-HISTOLOGY","itemName":"corrected histology",
                         "specimenKindCode":"TISSUE","specimenDescription":"corrected pending specimen",
                         "sequenceNo":2}]}
                        """))
                .andExpect(status().isOk()).andReturn());
        assertThat(updated.get("statusCode").asText()).isEqualTo("PARTIALLY_REGISTERED");
        assertThat(updated.get("items").findValuesAsText("statusCode")).contains("REGISTERED", "PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT case_id FROM pis_v2.pathology_application_case WHERE application_item_id = ?",
                java.util.UUID.class, java.util.UUID.fromString(registeredItemId)).toString()).isEqualTo(caseId);

        JsonNode cancelled = json(mockMvc.perform(post("/api/v2/applications/{applicationId}/cancel", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"upstream cancellation\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(cancelled.get("statusCode").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("items").findValuesAsText("statusCode")).contains("REGISTERED", "CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_case WHERE id = ?",
                Integer.class, java.util.UUID.fromString(caseId))).isEqualTo(1);
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

    @Test
    void validationAndPatientLookupSupportHisFallbackWithoutCreatingPartialApplication() throws Exception {
        JsonNode validation = json(mockMvc.perform(post("/api/v2/applications/validate")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationNo":"APP-INVALID","sourceTypeCode":"MANUAL",
                         "sourceSystemCode":"PIS-MANUAL","patientReference":"PATIENT-INVALID",
                         "patientName":"","visitReference":"MZ-INVALID","visitTypeCode":"OUTPATIENT",
                         "applicationDepartment":"","applicantReference":"SYNTH-DOCTOR","items":[]}
                        """)).andExpect(status().isOk()).andReturn());
        assertThat(validation.get("valid").asBoolean()).isFalse();
        assertThat(validation.path("issues").findValuesAsText("field"))
                .contains("patientName", "applicationDepartment", "items");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_application", Integer.class))
                .isZero();

        JsonNode found = json(mockMvc.perform(post("/api/v2/applications/patient-lookup")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"outpatientNo":"MZ10001"}
                        """))
                .andExpect(status().isOk()).andReturn());
        assertThat(found.get("found").asBoolean()).isTrue();
        assertThat(found.get("visitTypeCode").asText()).isEqualTo("OUTPATIENT");
        assertThat(found.get("adapterCode").asText()).isEqualTo("SIMULATOR");

        JsonNode notFound = json(mockMvc.perform(post("/api/v2/applications/patient-lookup")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"visitId":"UNKNOWN"}
                        """))
                .andExpect(status().isOk()).andReturn());
        assertThat(notFound.get("found").asBoolean()).isFalse();
        assertThat(notFound.get("message").asText()).contains("人工补录");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_application", Integer.class))
                .isZero();

        mockMvc.perform(post("/api/v2/applications/patient-lookup")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"patientId":"TIMEOUT"}
                        """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectedIncomingSpecimenRequiresReasonAndNeverCreatesCase() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-REJECT")))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        String itemId = created.get("items").get(0).get("itemId").asText();

        mockMvc.perform(post("/api/v2/applications/{applicationId}/delivery", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":"%s","incomingSpecimenReference":"RJ-003-1",
                         "specimenLabelCode":"RJ-003-1","patientReference":"SYNTH-PATIENT-APP",
                         "actualSpecimenDescription":"synthetic rejected specimen","outcomeCode":"REJECTED"}
                        """.formatted(itemId))).andExpect(status().isBadRequest());

        JsonNode rejected = json(mockMvc.perform(post("/api/v2/applications/{applicationId}/delivery", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":"%s","incomingSpecimenReference":"RJ-003-1",
                         "specimenLabelCode":"RJ-003-1","patientReference":"SYNTH-PATIENT-APP",
                         "actualSpecimenDescription":"synthetic rejected specimen","outcomeCode":"REJECTED",
                         "reasonCode":"CONTAINER_DAMAGED","reasonText":"synthetic container damaged",
                         "patientMatch":true,"applicationMatch":true,"quantityMatch":true,
                         "specimenMatch":false,"containerMatch":false,"fixationMatch":true}
                        """.formatted(itemId))).andExpect(status().isOk()).andReturn());
        assertThat(rejected.get("statusCode").asText()).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status_code FROM pis_v2.pathology_application_item WHERE id = ?",
                String.class, java.util.UUID.fromString(itemId))).isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.pathology_application_case",
                Integer.class)).isZero();
        assertThat(mockMvc.perform(get("/api/v2/applications/queue")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).doesNotContain(itemId);
    }

    @Test
    void barcodeBatchPrintIsOrderedLoggedAndFailureDoesNotAlterApplication() throws Exception {
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-PRINT")))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        String firstItem = created.get("items").get(0).get("itemId").asText();
        String secondItem = created.get("items").get(1).get("itemId").asText();

        json(mockMvc.perform(post("/api/v2/applications/{applicationId}/barcode-print", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":["%s","%s"],"copies":2,
                         "printerProfileCode":"MOCK://SYNTH-PRINTER"}
                        """.formatted(secondItem, firstItem))).andExpect(status().isOk()).andReturn());
        json(mockMvc.perform(post("/api/v2/applications/{applicationId}/barcode-print", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":["%s"],"copies":1,
                         "printerProfileCode":"MOCK://SYNTH-PRINTER"}
                        """.formatted(firstItem))).andExpect(status().isOk()).andReturn());
        JsonNode failed = json(mockMvc.perform(post("/api/v2/applications/{applicationId}/barcode-print", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":["%s"],"copies":1,"printerProfileCode":"FAIL-PRINTER"}
                        """.formatted(secondItem))).andExpect(status().isOk()).andReturn());
        assertThat(failed.get("allSucceeded").asBoolean()).isFalse();

        JsonNode history = json(mockMvc.perform(get(
                "/api/v2/applications/{applicationId}/barcode-print-history", applicationId))
                .andExpect(status().isOk()).andReturn());
        assertThat(history).hasSize(4);
        assertThat(history.findValuesAsText("barcode"))
                .startsWith("SYNTH-APP-PRINT-1", "SYNTH-APP-PRINT-2");
        assertThat(history.findValuesAsText("operationCode")).contains("PRINT", "REPRINT");
        assertThat(history.findValuesAsText("resultCode")).contains("SUCCESS", "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status_code FROM pis_v2.pathology_application WHERE id = ?",
                String.class, java.util.UUID.fromString(applicationId))).isEqualTo("RECEIVED");
    }

    @Test
    void registrationLabelReprintAndOutpatientReceiptPreserveFormalSpecimenIdentity() throws Exception {
        String request = applicationRequest("SYNTH-APP-REG-PRINT").replace(
                "\"visitReference\":", "\"visitTypeCode\":\"OUTPATIENT\",\"visitReference\":");
        JsonNode created = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn());
        String applicationId = created.get("applicationId").asText();
        String itemId = created.get("items").get(0).get("itemId").asText();
        verify(applicationId, itemId, "SYNTH-APP-REG-PRINT-1");
        JsonNode registered = json(mockMvc.perform(post(
                "/api/v2/applications/{applicationId}/items/{itemId}/register", applicationId, itemId)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn());
        String caseId = registered.get("cases").get(0).get("caseId").asText();
        String specimenId = registered.get("cases").get(0).get("specimenId").asText();

        String printRequest = """
                {"specimenIds":["%s"],"copies":1,"printerProfileCode":"MOCK://SYNTH-PRINTER"}
                """.formatted(specimenId);
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/specimen-labels/print", caseId)
                .contentType(MediaType.APPLICATION_JSON).content(printRequest)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/specimen-labels/print", caseId)
                .contentType(MediaType.APPLICATION_JSON).content(printRequest)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v2/registration/cases/{caseId}/receipt/print", caseId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"copies":1,"printerProfileCode":"MOCK://SYNTH-PRINTER"}
                        """)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE id = ?",
                Integer.class, java.util.UUID.fromString(specimenId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("""
                SELECT operation_code FROM pis_v2.pathology_registration_label_print
                WHERE case_id = ? ORDER BY requested_at
                """, String.class, java.util.UUID.fromString(caseId))).containsExactly("PRINT", "REPRINT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT rendered_receipt FROM pis_v2.pathology_registration_receipt_print WHERE case_id = ?
                """, String.class, java.util.UUID.fromString(caseId)))
                .contains("Synthetic Patient", "SYNTH-VISIT-APP", registered.get("cases").get(0).get("caseNo").asText());
    }

    @Test
    void barcodeScanDeliveryQueryAndExcelExportUseTheSameScopedFacts() throws Exception {
        JsonNode first = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-SCAN-A")))
                .andExpect(status().isOk()).andReturn());
        JsonNode second = json(mockMvc.perform(post("/api/v2/applications")
                .contentType(MediaType.APPLICATION_JSON).content(applicationRequest("SYNTH-APP-SCAN-B")))
                .andExpect(status().isOk()).andReturn());
        String firstApplication = first.get("applicationId").asText();
        String firstItem = first.get("items").get(0).get("itemId").asText();
        String secondApplication = second.get("applicationId").asText();

        mockMvc.perform(post("/api/v2/applications/{applicationId}/barcode-print", firstApplication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"applicationItemId":["%s"],"printerProfileCode":"MOCK://SYNTH-PRINTER","copies":1}
                        """.formatted(firstItem)))
                .andExpect(status().isOk());

        JsonNode scanned = json(mockMvc.perform(get("/api/v2/applications/barcode-scan")
                .param("barcode", "SYNTH-APP-SCAN-A-1")).andExpect(status().isOk()).andReturn());
        assertThat(scanned.get("applicationId").asText()).isEqualTo(firstApplication);
        assertThat(scanned.get("delivered").asBoolean()).isFalse();
        mockMvc.perform(get("/api/v2/applications/barcode-scan").param("barcode", "NO-SUCH-BARCODE"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v2/applications/{applicationId}/delivery", secondApplication)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":"%s","incomingSpecimenReference":"SYNTH-APP-SCAN-A-1",
                         "patientReference":"SYNTH-PATIENT-APP","outcomeCode":"ACCEPTED",
                         "patientMatch":true,"applicationMatch":true,"quantityMatch":true,
                         "specimenMatch":true,"containerMatch":true,"fixationMatch":true}
                        """.formatted(firstItem))).andExpect(status().isNotFound());

        verify(firstApplication, firstItem, "SYNTH-APP-SCAN-A-1");
        JsonNode delivered = json(mockMvc.perform(get("/api/v2/applications/barcode-scan")
                .param("barcode", "SYNTH-APP-SCAN-A-1")).andExpect(status().isOk()).andReturn());
        assertThat(delivered.get("delivered").asBoolean()).isTrue();
        assertThat(delivered.hasNonNull("deliveredAt")).isTrue();
        assertThat(delivered.hasNonNull("deliveredBy")).isTrue();

        JsonNode rows = json(mockMvc.perform(get("/api/v2/applications/deliveries")
                .param("visitReference", "SYNTH-VISIT-APP")
                .param("externalItemCode", "SYNTH-HISTOLOGY"))
                .andExpect(status().isOk()).andReturn());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("applicationNo").asText()).isEqualTo("SYNTH-APP-SCAN-A");
        String excel = mockMvc.perform(get("/api/v2/applications/deliveries/export")
                .param("visitReference", "SYNTH-VISIT-APP")
                .param("externalItemCode", "SYNTH-HISTOLOGY"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(excel).contains("Excel.Sheet", "SYNTH-APP-SCAN-A", "SYNTH-APP-SCAN-A-1");
        assertThat(excel).doesNotContain("SYNTH-APP-SCAN-B");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode verify(String applicationId, String itemId, String barcode) throws Exception {
        return json(mockMvc.perform(post("/api/v2/applications/{applicationId}/delivery", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"applicationItemId":"%s","incomingSpecimenReference":"%s",
                         "specimenLabelCode":"%s","patientReference":"SYNTH-PATIENT-APP",
                         "actualSpecimenDescription":"合成送检标本","outcomeCode":"ACCEPTED",
                         "patientMatch":true,"applicationMatch":true,"quantityMatch":true,
                         "specimenMatch":true,"containerMatch":true,"fixationMatch":true}
                        """.formatted(itemId, barcode, barcode))).andExpect(status().isOk()).andReturn());
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
