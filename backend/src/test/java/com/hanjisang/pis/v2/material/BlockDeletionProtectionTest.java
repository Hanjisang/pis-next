package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BlockDeletionProtectionTest extends GrossingClosureTestSupport {

    @Test
    void blockWithGeneratedSlideCannotBeCancelledOrCascadeDeleted() throws Exception {
        UUID caseId = createCase("BLOCK-DELETE");
        UUID specimenId = createSpecimen(caseId, "BD-S", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "删除保护来源");
        UUID blockId = createBlock(grossingId, specimenId, "BD-A1");
        complete(grossingId, 0);

        mockMvc.perform(post("/api/v2/blocks/{id}/soft-delete", blockId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedVersion":0,"reason":"合成取消尝试","idempotencyKey":"%s"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE block_id = ? AND deleted_at IS NULL",
                Integer.class, blockId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM pis_v2.block WHERE id = ?",
                Boolean.class, blockId)).isTrue();
    }
}
