package com.hanjisang.pis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundariesTest {

    @Test
    void allActiveModulesAreDiscoveredAndVerified() {
        ApplicationModules modules = ApplicationModules.of(PisApplication.class);

        modules.verify();

        assertThat(modules.stream()).hasSize(12);
    }
}
