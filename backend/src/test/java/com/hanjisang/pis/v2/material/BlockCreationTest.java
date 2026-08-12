package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BlockCreationTest extends GrossingClosureTestSupport {

    @Test
    void batchCreationKeepsOneBlockPerRowAndSpecimenLineage() throws Exception {
        UUID caseId = createCase("BLOCK-CREATE");
        UUID specimenId = createSpecimen(caseId, "BLK-S", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "批量材块来源描述");

        var result = postOk("/api/v2/grossings/" + grossingId + "/blocks/batch", """
                {"blocks":[
                    {"specimenId":"%s","blockCode":"BLK-A1","blockType":"ROUTINE","samplingDescription":"组织一"},
                    {"specimenId":"%s","blockCode":"BLK-A2","blockType":"ROUTINE","samplingDescription":"组织二"}
                 ],"idempotencyKey":"%s"}
                """.formatted(specimenId, specimenId, UUID.randomUUID()));

        assertThat(result.path("blocks")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE grossing_id = ? AND specimen_id = ?",
                Integer.class, grossingId, specimenId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT quantity FROM pis_v2.block WHERE grossing_id = ?", Integer.class,
                grossingId)).containsOnly(1);
    }
}
