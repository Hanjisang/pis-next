package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BlockPrintTest extends GrossingClosureTestSupport {

    @Test
    void printAndReprintKeepBlockIdentityAndCreateSeparateLogs() throws Exception {
        UUID caseId = createCase("BLOCK-PRINT");
        UUID specimenId = createSpecimen(caseId, "BP-S", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "打印来源");
        UUID blockId = createBlock(grossingId, specimenId, "BP-A1");

        postOk("/api/v2/blocks/" + blockId + "/print",
                "{\"reason\":\"首次打印\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}");
        postOk("/api/v2/blocks/" + blockId + "/print",
                "{\"reason\":\"标签污损重打\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE id = ?", Integer.class, blockId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.print_log WHERE entity_id = ?",
                Integer.class, blockId)).isEqualTo(2);
    }
}
