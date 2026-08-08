package com.hanjisang.pis.v2.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.v2.registration.domain.Case;
import com.hanjisang.pis.v2.registration.domain.PathologyNumberRule;
import com.hanjisang.pis.v2.registration.domain.Specimen;

class V2RegistrationDomainTest {

    @Test
    void caseLifecycleOnlyAllowsActiveAndCancelled() {
        Case pathologyCase = Case.active(UUID.randomUUID(), "H-000001", "SYNTH-HIS", "APP-001",
                "SYNTH-HISTOLOGY", UUID.randomUUID(), "HISTOLOGY", "SYNTH-PATIENT-001", "SYNTH-VISIT-001");

        assertThat(pathologyCase.lifecycleStateCode()).isEqualTo(Case.ACTIVE);
        assertThat(pathologyCase.numberBindingActive()).isTrue();
        pathologyCase.cancel("synthetic cancellation", Instant.now());

        assertThat(pathologyCase.lifecycleStateCode()).isEqualTo(Case.CANCELLED);
        assertThat(pathologyCase.numberBindingActive()).isFalse();
        assertThatThrownBy(() -> pathologyCase.cancel("duplicate", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void specimenIsMutableAndSoftDeletableWithoutWorkflowState() {
        Specimen specimen = Specimen.register(UUID.randomUUID(), UUID.randomUUID(), "HS-0000001", "A",
                "TISSUE", "LOCAL", "SYNTH-SOURCE-001", "synthetic site", "SURGICAL", "SYNTH-LABEL-001");

        specimen.updateDetails("A-UPDATED", "TISSUE", "LOCAL", "SYNTH-SOURCE-002", "updated site", "SURGICAL",
                "SYNTH-LABEL-002", Instant.now());
        assertThat(specimen.specimenCode()).isEqualTo("A-UPDATED");
        assertThat(specimen.concurrencyVersion()).isEqualTo(1);

        specimen.softDelete("synthetic correction", Instant.now());
        assertThat(specimen.deleted()).isTrue();
        assertThat(specimen.concurrencyVersion()).isEqualTo(2);
        assertThatThrownBy(() -> specimen.updateDetails("A-REJECTED", "TISSUE", "LOCAL", "SOURCE", "site",
                "SURGICAL", null, Instant.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pathologyNumberFormattingRequiresActiveRuleAndPositiveSerial() {
        PathologyNumberRule rule = PathologyNumberRule.configure("LOCAL_HOSPITAL", "HISTOLOGY", "CASE", "H-",
                "ORGANIZATION", 6, true);

        assertThat(rule.format(1)).isEqualTo("H-000001");
        assertThatThrownBy(() -> rule.format(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
