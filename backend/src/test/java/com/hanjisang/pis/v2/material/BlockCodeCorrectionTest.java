package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BlockCodeCorrectionTest extends GrossingClosureTestSupport {

    @Test
    void codeCorrectionKeepsBlockIdentityAndWritesHistory() throws Exception {
        UUID caseId = createCase("BLOCK-CORRECT");
        UUID specimenId = createSpecimen(caseId, "BC-S", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "材块纠正来源");
        UUID blockId = createBlock(grossingId, specimenId, "BC-A1");

        var result = ok(mockMvc.perform(put("/api/v2/blocks/{id}", blockId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"blockCode":"BC-A1-C","blockType":"ROUTINE","samplingDescription":"纠正后组织",
                         "note":"复核完成","reason":"编号录入错误","expectedVersion":0,
                         "idempotencyKey":"%s"}
                        """.formatted(UUID.randomUUID()))));

        assertThat(result.path("blockId").asText()).isEqualTo(blockId.toString());
        assertThat(result.path("blockCode").asText()).isEqualTo("BC-A1-C");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block_code_history WHERE block_id = ?",
                Integer.class, blockId)).isEqualTo(1);
    }
}
