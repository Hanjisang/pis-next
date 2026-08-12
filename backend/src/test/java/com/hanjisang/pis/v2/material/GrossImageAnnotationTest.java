package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GrossImageAnnotationTest extends GrossingClosureTestSupport {

    @Test
    void imageSupportsStructuredAnnotationAndMillimetreMeasurement() throws Exception {
        UUID caseId = createCase("GROSS-ANNOTATION");
        UUID specimenId = createSpecimen(caseId, "ANN-A", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "标注标本描述");
        UUID imageId = UUID.fromString(postOk("/api/v2/material/grossings/" + grossingId + "/images/capture",
                "{\"specimenId\":\"" + specimenId + "\"}").path("imageId").asText());

        postOk("/api/v2/material/grossings/images/" + imageId + "/annotations", """
                {"annotationTypeCode":"RECTANGLE","geometryJson":"{\\\"x\\\":10,\\\"y\\\":20,\\\"width\\\":30,\\\"height\\\":40}",
                 "label":"可疑区域","note":"合成标注"}
                """);
        postOk("/api/v2/material/grossings/images/" + imageId + "/measurements", """
                {"geometryJson":"{\\\"x1\\\":0,\\\"y1\\\":0,\\\"x2\\\":30,\\\"y2\\\":0}",
                 "value":12.5,"unitCode":"MM","measurementModeCode":"LINEAR"}
                """);

        assertThat(getOk("/api/v2/material/grossings/images/" + imageId + "/annotations")).hasSize(1);
        assertThat(getOk("/api/v2/material/grossings/images/" + imageId + "/measurements").get(0)
                .path("unitCode").asText()).isEqualTo("MM");
    }
}
