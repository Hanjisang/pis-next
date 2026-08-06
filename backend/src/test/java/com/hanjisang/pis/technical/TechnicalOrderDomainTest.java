package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.technical.domain.TechnicalOrder;
import com.hanjisang.pis.technical.domain.TechnicalProjectType;

class TechnicalOrderDomainTest {

    @Test
    void projectTypesAreExplicitAndOrderStateIsDerivedFromProjectFacts() {
        assertThat(TechnicalProjectType.parse("IHC")).isEqualTo(TechnicalProjectType.IHC);
        assertThat(TechnicalOrder.deriveState(true, false, false, false, false, false, false, false))
                .isEqualTo(TechnicalOrder.SUBMITTED);
        assertThat(TechnicalOrder.deriveState(true, false, true, false, false, false, false, false))
                .isEqualTo(TechnicalOrder.ACCEPTED);
        assertThat(TechnicalOrder.deriveState(true, false, true, true, true, false, false, false))
                .isEqualTo(TechnicalOrder.WAITING_RESULT);
        assertThat(TechnicalOrder.deriveState(true, false, true, true, false, true, true, false))
                .isEqualTo(TechnicalOrder.COMPLETED);
    }

    @Test
    void terminalOrdersCannotBeEdited() {
        assertThatThrownBy(() -> TechnicalOrder.requireDraft(TechnicalOrder.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TechnicalOrder.requireNotTerminal(TechnicalOrder.CANCELLED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
