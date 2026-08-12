package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BlockVerificationTest extends GrossingClosureTestSupport {

    @Test
    void verificationRecordsPassAndExplainedMismatchWithoutChangingBlock() throws Exception {
        UUID caseId = createCase("BLOCK-VERIFY");
        UUID specimenId = createSpecimen(caseId, "BV-S", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "核对来源");
        UUID blockId = createBlock(grossingId, specimenId, "BV-A1");

        var passed = postOk("/api/v2/blocks/" + blockId + "/verify", """
                {"verifiedCode":"BV-A1","verifiedSpecimenId":"%s","verifiedQuantity":1}
                """.formatted(specimenId));
        var failed = postOk("/api/v2/blocks/" + blockId + "/verify", """
                {"verifiedCode":"BV-WRONG","verifiedSpecimenId":"%s","verifiedQuantity":1,
                 "reason":"扫描到错误标签，已隔离"}
                """.formatted(specimenId));

        assertThat(passed.path("resultCode").asText()).isEqualTo("PASSED");
        assertThat(failed.path("resultCode").asText()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block_verification WHERE block_id = ?",
                Integer.class, blockId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM pis_v2.block WHERE id = ?",
                Boolean.class, blockId)).isTrue();
    }

    @Test
    void configuredVerificationMustPassBeforeGrossingCanComplete() throws Exception {
        UUID caseId = createCase("BLOCK-VERIFY-REQUIRED");
        UUID specimenId = createSpecimen(caseId, "BVR-S", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "强制核对来源");
        UUID blockId = createBlock(grossingId, specimenId, "BVR-A1");
        UUID businessTypeId = jdbc.queryForObject("SELECT business_type_id FROM pis_v2.pathology_case WHERE id = ?",
                UUID.class, caseId);
        jdbc.update("""
                UPDATE pis_v2.block_verification_policy
                   SET verification_required = TRUE, dual_check_required = TRUE, same_user_allowed = FALSE
                 WHERE organization_reference = 'LOCAL_HOSPITAL' AND business_type_id = ?
                """, businessTypeId);

        mockMvc.perform(post("/api/v2/grossings/{id}/complete", grossingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"idempotencyKey\":\"verify-required-before\"}"))
                .andExpect(status().isConflict());
        postOk("/api/v2/blocks/" + blockId + "/verify", """
                {"verifiedCode":"BVR-A1","verifiedSpecimenId":"%s","verifiedQuantity":1}
                """.formatted(specimenId));
        assertThat(complete(grossingId, 0).path("completedAt").isNull()).isFalse();
    }
}
