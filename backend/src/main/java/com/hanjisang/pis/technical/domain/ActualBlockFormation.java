package com.hanjisang.pis.technical.domain;

import java.util.UUID;

public record ActualBlockFormation(UUID id, UUID tissueBlockId, int formationVersion, String inheritedBlockNo,
        boolean currentValid) {

    public static void requireFirstFormation(boolean alreadyFormed) {
        if (alreadyFormed) throw new IllegalStateException("计划蜡块已有有效实际蜡块");
    }
}
