package com.hanjisang.pis.v2.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkbenchGrossingProjectionTest extends WorkbenchProjectionTestSupport {

    @Test
    void routineFrozenAndTodayQueuesAreSeparateBusinessFactProjections() throws Exception {
        var body = workbench();
        assertThat(queue(body, "GROSSING_PENDING").path("kind").asText()).isEqualTo("PENDING");
        assertThat(queue(body, "FROZEN_GROSSING").path("kind").asText()).isEqualTo("PENDING");
        assertThat(queue(body, "GROSSED_TODAY").path("kind").asText()).isEqualTo("TRACKING");
    }
}
