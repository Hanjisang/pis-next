package com.hanjisang.pis.v2.capability;

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
        boolean diagnosisEnabled) {

    public static BusinessTypeCapability from(String businessTypeCode, String modalityCode) {
        String normalizedModality = modalityCode == null ? "" : modalityCode.trim().toUpperCase();
        boolean histology = "TISSUE".equals(normalizedModality);
        boolean cytology = "CYTOLOGY".equals(normalizedModality);
        boolean frozen = "FROZEN".equals(normalizedModality);
        return new BusinessTypeCapability(businessTypeCode, normalizedModality,
                histology || frozen,
                histology || frozen,
                cytology,
                histology,
                histology || cytology || frozen,
                true);
    }
}
