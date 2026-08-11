package com.hanjisang.pis.v2.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessTypeCapabilityTest {

    @Test
    void keepsCytologyOnDirectSpecimenToSlidePath() {
        BusinessTypeCapability capability = BusinessTypeCapability.from("CYTOLOGY_NON_GYN", "CYTOLOGY");

        assertThat(capability.supportsDirectSlides()).isTrue();
        assertThat(capability.usesHistologyProcessing()).isFalse();
        assertThat(capability.supportsBlocks()).isFalse();
        assertThat(capability.requiresSlideCompletion()).isTrue();
    }

    @Test
    void keepsTissueOnHistologyPath() {
        BusinessTypeCapability capability = BusinessTypeCapability.from("HISTOLOGY", "TISSUE");

        assertThat(capability.supportsDirectSlides()).isFalse();
        assertThat(capability.usesHistologyProcessing()).isTrue();
        assertThat(capability.requiresGrossing()).isTrue();
        assertThat(capability.supportsBlocks()).isTrue();
    }
}
