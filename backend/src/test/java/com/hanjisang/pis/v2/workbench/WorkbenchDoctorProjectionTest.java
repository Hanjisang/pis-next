package com.hanjisang.pis.v2.workbench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkbenchDoctorProjectionTest extends WorkbenchProjectionTestSupport {

    @Test
    void diagnosisResponsibilitiesRemainQueuesOfOneDiagnosis() throws Exception {
        var body = workbench();
        assertThat(queue(body, "PUBLIC_POOL").path("label").asText()).isEqualTo("待接诊");
        assertThat(queue(body, "INITIAL").path("label").asText()).isEqualTo("待初诊");
        assertThat(queue(body, "REVIEW").path("label").asText()).isEqualTo("待复诊");
        assertThat(queue(body, "AUDIT").path("label").asText()).isEqualTo("待审核");
        assertThat(queue(body, "TECHNICAL_RESULT_RETURNED_REQUIRES_ATTENTION").path("label").asText())
                .isEqualTo("新技术结果");
    }
}
