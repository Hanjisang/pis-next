package com.hanjisang.pis.v2.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

class TechnicalTraceAndReworkTest extends RoutineProductionTestSupport {

    @Test
    void recordsFiveOptionalPhysicalTracesAgainstTheirRealMaterialTargets() throws Exception {
        Fixture fixture = routineFixture("FC03A-TRACE", 1);
        String blockId = fixture.blockIds().getFirst();
        String slideId = slideForBlock(blockId);

        completeTrace("BLOCK", blockId, "DEHYDRATION", null);
        completeTrace("BLOCK", blockId, "EMBEDDING", null);
        completeTrace("SLIDE", slideId, "SECTIONING", null);
        completeTrace("SLIDE", slideId, "STAINING", "HE");
        completeTrace("SLIDE", slideId, "COVERSLIPPING", null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.material_process_fact WHERE block_id = ?",
                Integer.class, UUID.fromString(blockId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.material_process_fact WHERE slide_id = ?",
                Integer.class, UUID.fromString(slideId))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT stain_code FROM pis_v2.material_process_fact WHERE slide_id = ? AND phase_code = 'STAINING'",
                String.class, UUID.fromString(slideId))).isEqualTo("HE");

        mockMvc.perform(post("/api/v2/histology/traces/SLIDE/%s/complete".formatted(slideId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stageCode\":\"DEHYDRATION\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchTraceIsAllOrNothingAndPersistsOneFactPerBlock() throws Exception {
        Fixture fixture = routineFixture("FC03A-TRACE-BATCH", 2);
        String first = fixture.blockIds().get(0);
        String second = fixture.blockIds().get(1);
        JsonNode result = response(post("/api/v2/histology/traces/BLOCK/complete-batch"), """
                {"targetIds":["%s","%s"],"stageCode":"DEHYDRATION","note":"同批脱水完成"}
                """.formatted(first, second));
        assertThat(result).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.material_process_fact WHERE phase_code = 'DEHYDRATION'",
                Integer.class)).isEqualTo(2);

        mockMvc.perform(post("/api/v2/histology/traces/BLOCK/complete-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetIds":["%s","%s"],"stageCode":"EMBEDDING"}
                        """.formatted(first, UUID.randomUUID())))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.material_process_fact WHERE phase_code = 'EMBEDDING'",
                Integer.class)).isZero();
    }

    @Test
    void exceptionAttentionDisappearsAfterResolution() throws Exception {
        Fixture fixture = routineFixture("FC03A-EXCEPTION", 1);
        String slideId = slideForBlock(fixture.blockIds().getFirst());
        JsonNode fact = response(post("/api/v2/histology/slides/%s/phases/STAINING/exception".formatted(slideId)),
                "{\"exceptionCode\":\"STAIN_FAILURE\",\"note\":\"合成染色异常\"}");
        assertThat(exceptionCount()).isEqualTo(1);

        response(post("/api/v2/histology/traces/%s/resolve-exception".formatted(fact.path("factId").asText())),
                "{\"note\":\"已复核并关闭\"}");
        assertThat(exceptionCount()).isZero();
    }

    @Test
    void recutCreatesNewSlideWhileRestainAndRescanKeepThePhysicalSlide() throws Exception {
        Fixture fixture = routineFixture("FC03A-REWORK", 1);
        String original = slideForBlock(fixture.blockIds().getFirst());
        response(post("/api/v2/histology/slides/%s/phases/STAINING/exception".formatted(original)),
                "{\"exceptionCode\":\"BROKEN\",\"note\":\"合成破损异常\"}");
        JsonNode recut = performRework(original, "RECUT", "玻片破损", "recut");
        String replacement = recut.path("replacementSlideId").asText();
        assertThat(replacement).isNotEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT block_id FROM pis_v2.slide WHERE id = ?", UUID.class,
                UUID.fromString(replacement))).isEqualTo(UUID.fromString(fixture.blockIds().getFirst()));
        assertThat(exceptionCount()).isEqualTo(1);
        response(post("/api/v2/slides/%s/complete".formatted(replacement)),
                "{\"expectedVersion\":0,\"idempotencyKey\":\"complete-recut\"}");
        assertThat(exceptionCount()).isZero();

        JsonNode restain = performRework(original, "RESTAIN", "染色修复", "restain");
        JsonNode rescan = performRework(original, "RESCAN", "数字切片重扫", "rescan");
        assertThat(restain.path("replacementSlideId").isMissingNode()
                || restain.path("replacementSlideId").isNull()).isTrue();
        assertThat(rescan.path("replacementSlideId").isMissingNode()
                || rescan.path("replacementSlideId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE block_id = ?",
                Integer.class, UUID.fromString(fixture.blockIds().getFirst()))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.material_rework WHERE original_slide_id = ?",
                Integer.class, UUID.fromString(original))).isEqualTo(3);
    }

    private JsonNode completeTrace(String kind, String id, String stage, String stain) throws Exception {
        return response(post("/api/v2/histology/traces/%s/%s/complete".formatted(kind, id)), """
                {"stageCode":"%s","stainCode":%s,"note":"合成技术记录"}
                """.formatted(stage, stain == null ? "null" : "\"" + stain + "\""));
    }

    private JsonNode performRework(String slideId, String type, String reason, String key) throws Exception {
        return response(post("/api/v2/slides/%s/rework/perform".formatted(slideId)), """
                {"reworkTypeCode":"%s","reason":"%s","idempotencyKey":"%s"}
                """.formatted(type, reason, key));
    }

    private int exceptionCount() throws Exception {
        JsonNode result = json.readTree(mockMvc.perform(get("/api/v2/production-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return result.path("queues").path("exceptions").path("count").asInt();
    }
}
