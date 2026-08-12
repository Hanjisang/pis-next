package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GrossImageTest extends GrossingClosureTestSupport {

    @Test
    void simulatorCaptureCreatesViewableImageAndSoftDeletePreservesFact() throws Exception {
        UUID caseId = createCase("GROSS-IMAGE");
        UUID specimenId = createSpecimen(caseId, "IMG-A", "REGISTRATION");
        UUID grossingId = createGrossing(caseId, "INITIAL", null);
        associate(grossingId, specimenId, "图像标本描述");

        var image = postOk("/api/v2/material/grossings/" + grossingId + "/images/capture", """
                {"specimenId":"%s","deviceReference":"SIMULATOR-GROSS-IMAGING"}
                """.formatted(specimenId));
        UUID imageId = UUID.fromString(image.path("imageId").asText());
        assertThat(image.path("storageReference").asText()).startsWith("data:image/svg+xml;base64,");

        postOk("/api/v2/material/grossings/images/" + imageId + "/delete",
                "{\"reason\":\"合成错误图像\"}");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM pis_v2.grossing_image WHERE id = ?",
                Boolean.class, imageId)).isTrue();
        assertThat(getOk("/api/v2/material/grossings/" + grossingId + "/images")).isEmpty();
    }
}
