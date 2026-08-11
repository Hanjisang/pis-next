package com.hanjisang.pis.v2.capability;

import java.util.List;

/**
 * Read-only capabilities derived from the configured V2 business type.
 *
 * <p>This is intentionally a projection policy. It does not add lifecycle
 * state to Case or create a second workflow aggregate.</p>
 */
public record BusinessTypeCapability(
        String businessTypeCode,
        String modalityCode,
        boolean requiresGrossing,
        boolean supportsBlocks,
        boolean supportsDirectSlides,
        boolean usesHistologyProcessing,
        boolean requiresSlideCompletion,
        boolean diagnosisEnabled,
        String initialSlideRule,
        List<String> productionCapabilities) {

    public static BusinessTypeCapability from(String businessTypeCode, String modalityCode) {
        String normalizedModality = modalityCode == null ? "" : modalityCode.trim().toUpperCase();
        boolean histology = "TISSUE".equals(normalizedModality);
        boolean cytology = "CYTOLOGY".equals(normalizedModality);
        boolean frozen = "FROZEN".equals(normalizedModality);
        String initialSlideRule = histology ? "INITIAL-HE" : frozen ? "FROZEN-HE" : null;
        List<String> productionCapabilities = cytology
                ? List.of("CYTOLOGY_PRODUCTION", "INCOMPLETE_SLIDES", "TECHNICAL_ORDER")
                : frozen
                        ? List.of("FROZEN_PRODUCTION", "INCOMPLETE_SLIDES", "TECHNICAL_ORDER")
                        : histology
                                ? List.of("ROUTINE_PRODUCTION", "INCOMPLETE_SLIDES", "TECHNICAL_ORDER")
                                : List.of("TECHNICAL_ORDER");
        return new BusinessTypeCapability(businessTypeCode, normalizedModality,
                histology || frozen,
                histology || frozen,
                cytology,
                histology,
                histology || cytology || frozen,
                true, initialSlideRule, productionCapabilities);
    }
}
