package com.hanjisang.pis.v2.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

class SlideLifecycleTest extends RoutineProductionTestSupport {

    @Test
    void requiredGenerationIsIdempotentAndManualExtraCreatesIndependentUnifiedSlides() throws Exception {
        Fixture fixture = routineFixture("FC03A-GENERATE", 1);
        String original = slideForBlock(fixture.blockIds().getFirst());
        response(post("/api/v2/slides/%s/cancel".formatted(original)), """
                {"expectedVersion":0,"reason":"构造生成场景","idempotencyKey":"cancel-generated"}
                """);

        JsonNode first = response(post("/api/v2/cases/%s/routine-slides/generate".formatted(fixture.caseId())),
                "{\"blockIds\":[\"%s\"],\"idempotencyKey\":\"required-first\"}"
                        .formatted(fixture.blockIds().getFirst()));
        JsonNode replay = response(post("/api/v2/cases/%s/routine-slides/generate".formatted(fixture.caseId())),
                "{\"blockIds\":[\"%s\"],\"idempotencyKey\":\"required-second\"}"
                        .formatted(fixture.blockIds().getFirst()));
        JsonNode extra = response(post("/api/v2/blocks/%s/routine-slides/extra".formatted(fixture.blockIds().getFirst())),
                "{\"slideType\":\"HE\",\"reason\":\"物理加片\",\"idempotencyKey\":\"extra-one\"}");

        assertThat(first.path("createdCount").asInt()).isEqualTo(1);
        assertThat(replay.path("createdCount").asInt()).isZero();
        assertThat(extra.path("blockId").asText()).isEqualTo(fixture.blockIds().getFirst());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE block_id = ? AND deleted_at IS NULL",
                Integer.class, UUID.fromString(fixture.blockIds().getFirst()))).isEqualTo(2);
    }

    @Test
    void codeCorrectionAndCompletionCorrectionPreserveSlideIdentityAndHistory() throws Exception {
        Fixture fixture = routineFixture("FC03A-CORRECT", 1);
        String slideId = slideForBlock(fixture.blockIds().getFirst());
        JsonNode renamed = response(post("/api/v2/slides/%s/correct-code".formatted(slideId)), """
                {"newSlideCode":"A1-01","reason":"标签录入纠正","expectedVersion":0}
                """);
        assertThat(renamed.path("slideId").asText()).isEqualTo(slideId);
        assertThat(renamed.path("slideCode").asText()).isEqualTo("A1-01");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide_code_history WHERE slide_id = ?",
                Integer.class, UUID.fromString(slideId))).isEqualTo(1);

        response(post("/api/v2/slides/%s/complete".formatted(slideId)),
                "{\"expectedVersion\":1,\"idempotencyKey\":\"complete-corrected\"}");
        response(post("/api/v2/slides/%s/correct-completion".formatted(slideId)), """
                {"reason":"误点完成修正","expectedVersion":2}
                """);
        assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM pis_v2.slide WHERE id = ?",
                Boolean.class, UUID.fromString(slideId))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide_completion_correction WHERE slide_id = ?",
                Integer.class, UUID.fromString(slideId))).isEqualTo(1);
    }

    @Test
    void batchPrintAndBatchCompletionDoNotRequireTechnicalTraces() throws Exception {
        Fixture fixture = routineFixture("FC03A-BATCH", 2);
        String first = slideForBlock(fixture.blockIds().get(0));
        String second = slideForBlock(fixture.blockIds().get(1));

        JsonNode printed = response(post("/api/v2/slides/print-batch"), """
                {"slideIds":["%s","%s"],"reason":"批量标签","idempotencyKey":"batch-print"}
                """.formatted(second, first));
        assertThat(printed.path("results")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.print_log WHERE entity_kind_code = 'SLIDE'",
                Integer.class)).isGreaterThanOrEqualTo(4);

        JsonNode completed = response(post("/api/v2/slides/complete-batch"), """
                {"slides":[{"slideId":"%s","expectedVersion":0},{"slideId":"%s","expectedVersion":0}],
                 "idempotencyKey":"batch-complete"}
                """.formatted(first, second));
        assertThat(completed.path("changedCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.material_process_fact", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE completed_at IS NOT NULL",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void softCancelPreservesHistoryAndRefusesSlidesWithDigitalDownstreamEvidence() throws Exception {
        Fixture fixture = routineFixture("FC03A-PROTECT", 2);
        String cancellable = slideForBlock(fixture.blockIds().get(0));
        String protectedSlide = slideForBlock(fixture.blockIds().get(1));
        response(post("/api/v2/slides/%s/cancel".formatted(cancellable)), """
                {"expectedVersion":0,"reason":"误生成","idempotencyKey":"cancel-safe"}
                """);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM pis_v2.slide WHERE id = ?",
                Boolean.class, UUID.fromString(cancellable))).isTrue();

        jdbc.update("""
                INSERT INTO pis_v2.digital_slide
                    (id, case_id, block_id, slide_id, binding_mode_code, status_code, viewer_reference,
                     source_platform, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, ?, 'MANUAL', 'ACTIVE', 'synthetic://viewer', 'SYNTHETIC',
                        CURRENT_TIMESTAMP, 'TEST', CURRENT_TIMESTAMP, 'TEST')
                """, UUID.randomUUID(), UUID.fromString(fixture.caseId()), UUID.fromString(fixture.blockIds().get(1)),
                UUID.fromString(protectedSlide));
        mockMvc.perform(post("/api/v2/slides/%s/cancel".formatted(protectedSlide))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"reason\":\"should fail\",\"idempotencyKey\":\"cancel-protected\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM pis_v2.slide WHERE id = ?",
                Boolean.class, UUID.fromString(protectedSlide))).isTrue();
    }

    @Test
    void barcodeLocatePrefersTheMaterialInsideTheCurrentCaseWhenBusinessCodesRepeat() throws Exception {
        Fixture firstCase = routineFixture("FC03A-SCAN-FIRST", 1);
        Fixture secondCase = routineFixture("FC03A-SCAN-SECOND", 1);

        JsonNode located = response(get("/api/v2/cases/%s/materials/locate".formatted(secondCase.caseId()))
                .queryParam("barcode", "A1-HE"), "");

        assertThat(located.path("materialKind").asText()).isEqualTo("SLIDE");
        assertThat(located.path("materialId").asText()).isEqualTo(slideForBlock(secondCase.blockIds().getFirst()));
        assertThat(located.path("materialId").asText()).isNotEqualTo(slideForBlock(firstCase.blockIds().getFirst()));
    }
}
