package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecimenAddTest extends GrossingClosureTestSupport {

    @Test
    void grossingWorkspaceCanAddNamedSpecimenWithoutInventingCollectionFacts() throws Exception {
        var caseId = createCase("SPECIMEN-ADD");
        var originalId = createSpecimen(caseId, "ADD-1", "REGISTRATION");
        var specimenId = createSpecimen(caseId, "ADD-2", "GROSSING_ADD");
        var specimen = getOk("/api/v2/registration/specimens/" + specimenId);

        assertThat(specimen.path("specimenName").asText()).isEqualTo("ADD-2号合成组织");
        assertThat(specimen.path("creationSourceCode").asText()).isEqualTo("GROSSING_ADD");
        assertThat(specimen.path("collectionSite").isMissingNode() || specimen.path("collectionSite").isNull()).isTrue();
        assertThat(specimen.path("collectionMethodCode").isMissingNode()
                || specimen.path("collectionMethodCode").isNull()).isTrue();
        assertThat(specimenId).isNotEqualTo(originalId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis_v2.specimen WHERE case_id = ? AND deleted_at IS NULL",
                Integer.class, caseId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pis.audit_event WHERE target_object_id = ?",
                Integer.class, specimenId)).isGreaterThan(0);
    }
}
