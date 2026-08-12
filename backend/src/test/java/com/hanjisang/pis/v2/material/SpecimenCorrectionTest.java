package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SpecimenCorrectionTest extends GrossingClosureTestSupport {

    @Test
    void downstreamSpecimenCorrectionKeepsIdentityAndRequiresAuditedReason() throws Exception {
        UUID caseId = createCase("SPECIMEN-CORRECT");
        UUID specimenId = createSpecimen(caseId, "COR-1", "GROSSING_ADD");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "修正前描述");

        String body = """
                {"specimenCode":"COR-1A","specimenName":"修正后合成组织","specimenKindCode":"TISSUE",
                 "sourceKindCode":"LOCAL","sourceReference":"SYNTH-COR-1","quantityValue":1,
                 "quantityUnitCode":"件","description":"修正后描述","labelCode":"LBL-COR-1",
                 "expectedVersion":0,"reason":"核对原始申请后纠正"}
                """;
        var corrected = ok(mockMvc.perform(put("/api/v2/registration/specimens/{id}", specimenId)
                .contentType(MediaType.APPLICATION_JSON).content(body)));

        assertThat(corrected.path("specimenId").asText()).isEqualTo(specimenId.toString());
        assertThat(corrected.path("specimenCode").asText()).isEqualTo("COR-1A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE target_object_id = ?",
                Integer.class, specimenId)).isGreaterThan(0);
    }

    @Test
    void downstreamSpecimenCorrectionWithoutReasonIsRejected() throws Exception {
        UUID caseId = createCase("SPECIMEN-CORRECT-NO-REASON");
        UUID specimenId = createSpecimen(caseId, "COR-2", "GROSSING_ADD");
        associate(createGrossing(caseId, "INITIAL", null), specimenId, "原描述");

        mockMvc.perform(put("/api/v2/registration/specimens/{id}", specimenId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"specimenCode":"COR-2A","specimenName":"修正后组织","specimenKindCode":"TISSUE",
                         "sourceKindCode":"LOCAL","sourceReference":"SYNTH-COR-2","quantityValue":1,
                         "quantityUnitCode":"件","labelCode":"LBL-COR-2","expectedVersion":0}
                        """))
                .andExpect(status().isUnprocessableEntity());
    }
}
