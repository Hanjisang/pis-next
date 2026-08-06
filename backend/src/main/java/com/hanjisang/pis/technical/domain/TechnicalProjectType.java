package com.hanjisang.pis.technical.domain;

import java.util.Arrays;

public enum TechnicalProjectType {
    DEEP_SECTION,
    RECUT,
    WHITE_SLIDE,
    IHC,
    SPECIAL_STAIN;

    public static TechnicalProjectType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported technical project type"));
    }
}
