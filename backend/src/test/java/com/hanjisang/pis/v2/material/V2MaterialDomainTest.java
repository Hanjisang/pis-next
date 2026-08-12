package com.hanjisang.pis.v2.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.v2.material.domain.Block;
import com.hanjisang.pis.v2.material.domain.Grossing;
import com.hanjisang.pis.v2.material.domain.Slide;
import com.hanjisang.pis.v2.material.domain.SlideRule;

class V2MaterialDomainTest {

    @Test
    void grossingIsCaseLevelAndCanReopenTheSameRecord() {
        Grossing grossing = Grossing.open(UUID.randomUUID(), UUID.randomUUID(), "G001", Grossing.INITIAL, null,
                "synthetic gross description", "synthetic instruction", "SYNTH-DOCTOR", "SYNTH-RECORDER",
                Instant.now());

        assertThat(grossing.isCompleted()).isFalse();
        grossing.complete(Instant.now(), "SYNTH-RECORDER");
        assertThat(grossing.isCompleted()).isTrue();
        assertThat(grossing.concurrencyVersion()).isEqualTo(1);

        grossing.reopen(Instant.now());
        assertThat(grossing.isCompleted()).isTrue();
        assertThat(grossing.concurrencyVersion()).isEqualTo(2);
        grossing.reopen(Instant.now());
        assertThat(grossing.concurrencyVersion()).isEqualTo(3);
    }

    @Test
    void blockAndSlideUseFactsAndPreserveSoftDeleteHistory() {
        UUID caseId = UUID.randomUUID();
        UUID grossingId = UUID.randomUUID();
        UUID specimenId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        Block block = Block.create(blockId, caseId, grossingId, specimenId, "A1", Block.ROUTINE);
        block.update("A1-R", Block.ROUTINE);
        assertThat(block.blockCode()).isEqualTo("A1-R");
        assertThat(block.concurrencyVersion()).isEqualTo(1);

        Slide slide = Slide.initialFromBlock(UUID.randomUUID(), caseId, blockId, "A1-R-HE", "HE", grossingId,
                "INITIAL-HE", 1, true);
        slide.complete("SYNTH-TECHNICIAN", Instant.now());
        slide.complete("SYNTH-TECHNICIAN", Instant.now());
        assertThat(slide.isCompleted()).isTrue();
        assertThat(slide.concurrencyVersion()).isEqualTo(1);

        slide.softDelete("synthetic correction", Instant.now());
        assertThat(slide.isDeleted()).isTrue();
        assertThat(slide.completedAt()).isNotNull();
        assertThatThrownBy(() -> slide.renameCode("A1-R-HE-REJECTED")).isInstanceOf(IllegalStateException.class);

        block.softDelete("synthetic correction", Instant.now());
        assertThat(block.isDeleted()).isTrue();
        assertThatThrownBy(() -> block.update("A1-R2", Block.ROUTINE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void slideRuleGeneratesStableCodesWithoutIntroducingPlannedActualTypes() {
        SlideRule rule = new SlideRule(UUID.randomUUID(), UUID.randomUUID(), "INITIAL-HE", Slide.INITIAL,
                "ON_GROSSING_COMPLETE", "HE", "HE", 2, true);

        assertThat(rule.slideCode("A1", 1)).isEqualTo("A1-HE-1");
        assertThat(rule.slideCode("A1", 2)).isEqualTo("A1-HE-2");
        assertThat(Arrays.stream(Slide.class.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList())
                .doesNotContain("status", "planned", "actual");
    }
}
