package com.hanjisang.pis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hanjisang.pis.presentation.FoundationController;

@SpringBootTest
@ActiveProfiles("test")
class FoundationControllerTest {

    @Autowired
    private FoundationController foundationController;

    @Test
    void foundationEndpointExposesTheP15ModuleCatalog() {
        var response = foundationController.foundation();

        assertThat(response.phase()).isEqualTo("P15");
        assertThat(response.modules()).hasSize(15);
    }
}
