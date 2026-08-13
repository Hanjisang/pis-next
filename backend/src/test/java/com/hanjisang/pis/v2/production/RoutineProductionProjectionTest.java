package com.hanjisang.pis.v2.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class RoutineProductionProjectionTest extends RoutineProductionTestSupport {

    @Test
    void projectionUsesOutstandingBlockRequirementsAndDisappearsAfterLastCompletion() throws Exception {
        Fixture fixture = routineFixture("FC03A-PROJECTION", 2);
        String firstSlide = slideForBlock(fixture.blockIds().get(0));
        String secondSlide = slideForBlock(fixture.blockIds().get(1));
        response(post("/api/v2/slides/%s/cancel".formatted(firstSlide)), """
                {"expectedVersion":0,"reason":"构造待生成材块","idempotencyKey":"cancel-projection"}
                """);
        response(post("/api/v2/slides/%s/complete".formatted(secondSlide)),
                "{\"expectedVersion\":0,\"idempotencyKey\":\"complete-second\"}");

        JsonNode before = workbench();
        JsonNode item = findCase(before.path("queues").path("routineProduction").path("items"), fixture.caseId());
        assertThat(item).isNotNull();
        assertThat(item.path("requiredCount").asInt()).isEqualTo(2);
        assertThat(item.path("completedCount").asInt()).isEqualTo(1);

        JsonNode generated = response(post("/api/v2/cases/%s/routine-slides/generate".formatted(fixture.caseId())),
                "{\"blockIds\":[\"%s\"],\"idempotencyKey\":\"generate-missing\"}"
                        .formatted(fixture.blockIds().get(0)));
        assertThat(generated.path("createdCount").asInt()).isEqualTo(1);
        String generatedSlide = generated.path("slides").get(0).path("slideId").asText();
        response(post("/api/v2/slides/%s/complete".formatted(generatedSlide)),
                "{\"expectedVersion\":0,\"idempotencyKey\":\"complete-generated\"}");

        JsonNode after = workbench();
        assertThat(findCase(after.path("queues").path("routineProduction").path("items"), fixture.caseId()))
                .isNull();
        assertThat(findCase(after.path("queues").path("incompleteSlides").path("items"), fixture.caseId()))
                .isNull();
    }

    @Test
    void cancelledCaseIsExcludedFromRoutineAndPendingSlideQueues() throws Exception {
        Fixture fixture = routineFixture("FC03A-CANCELLED", 1);
        jdbc.update("UPDATE pis_v2.pathology_case SET lifecycle_state_code = 'CANCELLED' WHERE id = ?",
                UUID.fromString(fixture.caseId()));

        JsonNode result = workbench();
        assertThat(findCase(result.path("queues").path("routineProduction").path("items"), fixture.caseId()))
                .isNull();
        assertThat(findCase(result.path("queues").path("incompleteSlides").path("items"), fixture.caseId()))
                .isNull();
    }

    @Test
    void completedSlideOnInactiveBlockDoesNotSatisfyAnActiveBlockRequirement() throws Exception {
        Fixture fixture = routineFixture("FC03A-INACTIVE-BLOCK", 2);
        String inactiveBlockId = fixture.blockIds().get(0);
        String historicalSlideId = slideForBlock(inactiveBlockId);
        response(post("/api/v2/slides/%s/complete".formatted(historicalSlideId)),
                "{\"expectedVersion\":0,\"idempotencyKey\":\"complete-inactive-block-slide\"}");
        jdbc.update("""
                UPDATE pis_v2.block
                   SET deleted_at = CURRENT_TIMESTAMP, deleted_by_ref = 'fixture', deletion_reason = '历史失效材块'
                 WHERE id = ?
                """, UUID.fromString(inactiveBlockId));

        JsonNode queueItem = findCase(workbench().path("queues").path("routineProduction").path("items"),
                fixture.caseId());
        assertThat(queueItem).isNotNull();
        assertThat(queueItem.path("requiredCount").asInt()).isEqualTo(1);
        assertThat(queueItem.path("completedCount").asInt()).isZero();

        JsonNode materialTree = json.readTree(mockMvc.perform(get("/api/v2/cases/{caseId}/materials", fixture.caseId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(materialTree.path("initialRequiredCount").asInt()).isEqualTo(1);
        assertThat(materialTree.path("initialCompletedCount").asInt()).isZero();
        assertThat(materialTree.path("initialProductionComplete").asBoolean()).isFalse();
    }

    private JsonNode workbench() throws Exception {
        return json.readTree(mockMvc.perform(get("/api/v2/production-workbench"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode findCase(JsonNode items, String caseId) {
        for (JsonNode item : items) {
            if (caseId.equals(item.path("caseId").asText())) return item;
        }
        return null;
    }
}
