package com.hanjisang.pis.technical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.technical.domain.ActualBlockFormation;
import com.hanjisang.pis.technical.domain.EmbeddingTask;
import com.hanjisang.pis.technical.domain.ProcessingBatch;
import com.hanjisang.pis.technical.domain.ProcessingProgramVersion;
import com.hanjisang.pis.technical.domain.ProcessingTask;

class ProcessingDomainTest {

    @Test
    void processingTaskAllowsExplicitRecoveryAndRejectsSkippingExecution() {
        UUID id = UUID.randomUUID();

        ProcessingTask task = ProcessingTask.planned(id)
                .transition(ProcessingTask.ASSIGNED)
                .transition(ProcessingTask.IN_PROGRESS)
                .transition(ProcessingTask.INTERRUPTED)
                .transition(ProcessingTask.REPROCESSING)
                .transition(ProcessingTask.IN_PROGRESS)
                .transition(ProcessingTask.COMPLETED);

        assertThat(task.stateCode()).isEqualTo(ProcessingTask.COMPLETED);
        assertThat(task.version()).isEqualTo(6);
        assertThatThrownBy(() -> ProcessingTask.planned(id).transition(ProcessingTask.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void partialBatchCanOnlyBeClosedAfterMemberDecisions() {
        ProcessingBatch partial = ProcessingBatch.persisted(UUID.randomUUID(), ProcessingBatch.PARTIAL, 3);

        assertThat(partial.transition(ProcessingBatch.COMPLETED).stateCode()).isEqualTo(ProcessingBatch.COMPLETED);
        assertThatThrownBy(() -> ProcessingBatch.persisted(UUID.randomUUID(), ProcessingBatch.COMPLETED, 3)
                .transition(ProcessingBatch.IN_PROGRESS)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void embeddingAndProgramRulesKeepPhysicalFormationExplicit() {
        EmbeddingTask embedding = EmbeddingTask.planned(UUID.randomUUID())
                .transition(EmbeddingTask.ASSIGNED)
                .transition(EmbeddingTask.IN_PROGRESS)
                .transition(EmbeddingTask.COMPLETED);
        assertThat(embedding.stateCode()).isEqualTo(EmbeddingTask.COMPLETED);

        ProcessingProgramVersion synthetic = new ProcessingProgramVersion("id", "SYNTHETIC-1", "SYNTHETIC",
                "ACTIVE", "digest", "parameters");
        assertThat(synthetic.allowedIn("test")).isTrue();
        assertThat(synthetic.allowedIn("formal")).isFalse();

        assertThatThrownBy(() -> ActualBlockFormation.requireFirstFormation(true))
                .isInstanceOf(IllegalStateException.class);
        ActualBlockFormation.requireFirstFormation(false);
    }
}
