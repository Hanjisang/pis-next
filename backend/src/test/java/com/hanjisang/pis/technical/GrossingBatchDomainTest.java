package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.technical.domain.GrossingBatch;
import com.hanjisang.pis.technical.domain.TissueBlock;

class GrossingBatchDomainTest {

    @Test
    void batchRequiresTakeoverBeforeStartingAndCannotReopenAfterHandoff() {
        GrossingBatch batch = GrossingBatch.planned(UUID.randomUUID());
        assertThatThrownBy(() -> batch.transition(GrossingBatch.COMPLETED)).isInstanceOf(IllegalStateException.class);
        GrossingBatch completed = batch.transition(GrossingBatch.ASSIGNED)
                .transition(GrossingBatch.IN_PROGRESS).transition(GrossingBatch.COMPLETED)
                .transition(GrossingBatch.HANDED_OFF);
        assertThatThrownBy(() -> completed.transition(GrossingBatch.IN_PROGRESS)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void plannedBlockOnlyMovesToGrossingRecordedAndNeverPhysicalFormedInP16() {
        TissueBlock block = TissueBlock.planned(UUID.randomUUID()).markGrossingRecorded();
        org.assertj.core.api.Assertions.assertThat(block.stateCode()).isEqualTo(TissueBlock.GROSSING_RECORDED);
        assertThatThrownBy(() -> block.markGrossingRecorded()).isInstanceOf(IllegalStateException.class);
    }
}
