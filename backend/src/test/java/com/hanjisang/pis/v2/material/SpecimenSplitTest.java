package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SpecimenSplitTest extends GrossingClosureTestSupport {

    @Test
    void splitCreatesChildAndPreservesSourceLineage() throws Exception {
        UUID caseId = createCase("SPECIMEN-SPLIT");
        UUID source = createSpecimen(caseId, "SRC-1", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, source, "拆分前来源描述");
        UUID existingBlock = createBlock(grossingId, source, "SRC-A1");
        var child = postOk("/api/v2/registration/specimens/" + source + "/split", """
                {"childSpecimenCode":"SRC-1-1","childSpecimenName":"分割后组织",
                 "specimenKindCode":"TISSUE","sourceKindCode":"LOCAL","quantityValue":1,
                 "quantityUnitCode":"件","description":"分割子标本","labelCode":"LBL-SRC-1-1",
                 "reason":"取材后分装"}
                """);

        UUID childId = UUID.fromString(child.path("specimenId").asText());
        assertThat(child.path("creationSourceCode").asText()).isEqualTo("GROSSING_SPLIT");
        assertThat(jdbc.queryForObject("SELECT source_specimen_id FROM pis_v2.specimen_split WHERE child_specimen_id = ?",
                UUID.class, childId)).isEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE id IN (?, ?)",
                Integer.class, source, childId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT specimen_id FROM pis_v2.block WHERE id = ?", UUID.class,
                existingBlock)).isEqualTo(source);
    }
}
