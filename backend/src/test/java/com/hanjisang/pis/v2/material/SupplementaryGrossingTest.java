package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SupplementaryGrossingTest extends GrossingClosureTestSupport {

    @Test
    void supplementaryOrderCreatesNewGrossingAndBlocksWithoutReplacingInitialFactsOrCreatingSlides() throws Exception {
        UUID caseId = createCase("SUPPLEMENTARY");
        UUID specimenId = createSpecimen(caseId, "SUP-S", "REGISTRATION");
        UUID initialGrossing = createGrossing(caseId, "INITIAL", null);
        associate(initialGrossing, specimenId, "首次取材描述");
        UUID initialBlock = createBlock(initialGrossing, specimenId, "SUP-A1");
        complete(initialGrossing, 0);
        UUID itemId = seedSupplementaryItem(caseId, specimenId);

        UUID supplementary = createGrossing(caseId, "TECHNICAL_ORDER", itemId);
        associate(supplementary, specimenId, "补充取材描述");
        UUID supplementaryBlock = createBlock(supplementary, specimenId, "SUP-A2");
        var completion = complete(supplementary, 0);

        assertThat(completion.path("createdSlideCount").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.grossing WHERE case_id = ?", Integer.class,
                caseId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.block WHERE id IN (?, ?)", Integer.class,
                initialBlock, supplementaryBlock)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.technical_order_output WHERE item_id = ? AND output_kind IN ('GROSSING', 'BLOCK')",
                Integer.class, itemId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.slide WHERE case_id = ? AND source_context_type = 'TECHNICAL_ORDER'",
                Integer.class, caseId)).isZero();
    }

    private UUID seedSupplementaryItem(UUID caseId, UUID specimenId) {
        UUID projectId = jdbc.queryForObject(
                "SELECT id FROM pis_v2.technical_project WHERE project_code = 'SUPPLEMENTARY-GROSSING'",
                UUID.class);
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO pis_v2.technical_order
                    (id, organization_reference, order_no, diagnosis_id, case_id, required_before_sign_out,
                     status_code, concurrency_version, created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, 'LOCAL_HOSPITAL', ?, ?, ?, TRUE, 'EXECUTING', 0, ?, 'TEST', ?, 'TEST')
                """, orderId, "TO-SUP-" + orderId, UUID.randomUUID(), caseId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.technical_order_item
                    (id, order_id, technical_project_id, project_code_snapshot, project_name_snapshot,
                     project_configuration_version, quantity, parameters, concurrency_version,
                     created_at, created_by_ref, updated_at, updated_by_ref)
                VALUES (?, ?, ?, 'SUPPLEMENTARY-GROSSING', '补充取材', 1, 1, '{}', 0,
                        ?, 'TEST', ?, 'TEST')
                """, itemId, orderId, projectId, now, now);
        jdbc.update("""
                INSERT INTO pis_v2.technical_order_target
                    (id, item_id, case_id, target_type, specimen_target_id, target_display_code,
                     concurrency_version, created_at, created_by_ref)
                VALUES (?, ?, ?, 'SPECIMEN', ?, 'SUP-S', 0, ?, 'TEST')
                """, UUID.randomUUID(), itemId, caseId, specimenId, now);
        return itemId;
    }
}
