package com.hanjisang.pis.v2.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkbenchTechnicianProjectionTest extends WorkbenchProjectionTestSupport {

    @Test
    void technicalQueuesStaySeparatedByBusinessSource() throws Exception {
        var body = workbench();
        assertThat(queue(body, "ROUTINE_PRODUCTION").path("label").asText()).isEqualTo("常规制片");
        assertThat(queue(body, "CYTOLOGY_PRODUCTION").path("label").asText()).isEqualTo("细胞制片");
        assertThat(queue(body, "INCOMPLETE_SLIDES").path("label").asText()).isEqualTo("待完成玻片");
        assertThat(queue(body, "TECHNICAL_ORDER").path("label").asText()).isEqualTo("技术医嘱");
        assertThat(queue(body, "EXCEPTIONS").path("label").asText()).isEqualTo("异常 / 返工");
    }
}
