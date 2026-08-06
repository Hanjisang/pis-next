package com.hanjisang.pis.specimen;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.specimen.domain.Specimen;

class SpecimenLifecycleTest {

    @Test
    void receivingCreatesASeparateLifecycleFactAndRejectsSecondTransition() {
        Specimen specimen = Specimen.expected(UUID.randomUUID(), UUID.randomUUID(), "DEV-SP-1", "TISSUE",
                "synthetic site");

        specimen.receive();

        org.assertj.core.api.Assertions.assertThat(specimen.lifecycleStateCode()).isEqualTo(Specimen.RECEIVED);
        org.assertj.core.api.Assertions.assertThat(specimen.concurrencyVersion()).isEqualTo(1);
        assertThatThrownBy(specimen::receive).isInstanceOf(IllegalStateException.class);
    }
}
