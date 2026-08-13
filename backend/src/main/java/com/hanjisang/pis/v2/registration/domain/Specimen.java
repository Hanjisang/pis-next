package com.hanjisang.pis.v2.registration.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Specimen {

    public static final String REGISTRATION = "REGISTRATION";
    public static final String GROSSING_ADD = "GROSSING_ADD";
    public static final String GROSSING_SPLIT = "GROSSING_SPLIT";
    public static final String EXTERNAL_INPUT = "EXTERNAL_INPUT";
    public static final String FROZEN_REMAINDER = "FROZEN_REMAINDER";
    private static final Set<String> CREATION_SOURCES = Set.of(REGISTRATION, GROSSING_ADD, GROSSING_SPLIT,
            EXTERNAL_INPUT, FROZEN_REMAINDER);

    private final UUID id;
    private final UUID caseId;
    private final String specimenNo;
    private String specimenCode;
    private String specimenName;
    private String specimenKindCode;
    private final String creationSourceCode;
    private String sourceKindCode;
    private String sourceReference;
    private String collectionSite;
    private String collectionMethodCode;
    private String preparationMethodCode;
    private String lateralityCode;
    private BigDecimal quantityValue;
    private String quantityUnitCode;
    private String description;
    private Instant removedAt;
    private Instant fixedAt;
    private Instant receivedAt;
    private String labelCode;
    private Instant deletedAt;
    private String deletionReason;
    private long concurrencyVersion;

    private Specimen(UUID id, UUID caseId, String specimenNo, String specimenCode, String specimenName,
            String specimenKindCode, String creationSourceCode, String sourceKindCode, String sourceReference,
            String collectionSite, String collectionMethodCode, String preparationMethodCode, String lateralityCode, BigDecimal quantityValue,
            String quantityUnitCode, String description, Instant removedAt, Instant fixedAt, Instant receivedAt,
            String labelCode, Instant deletedAt, String deletionReason, long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "Specimen id is required");
        this.caseId = Objects.requireNonNull(caseId, "Specimen case is required");
        this.specimenNo = required(specimenNo, "Specimen number is required");
        this.specimenCode = required(specimenCode, "Specimen code is required");
        this.specimenName = required(specimenName, "Specimen name is required");
        this.specimenKindCode = required(specimenKindCode, "Specimen kind is required");
        this.creationSourceCode = required(creationSourceCode, "Specimen creation source is required");
        if (!CREATION_SOURCES.contains(this.creationSourceCode)) {
            throw new IllegalArgumentException("Unsupported specimen creation source");
        }
        this.sourceKindCode = required(sourceKindCode, "Specimen source kind is required");
        this.sourceReference = required(sourceReference, "Specimen source reference is required");
        this.collectionSite = optional(collectionSite);
        this.collectionMethodCode = optional(collectionMethodCode);
        this.preparationMethodCode = optional(preparationMethodCode);
        this.lateralityCode = optional(lateralityCode);
        this.quantityValue = validQuantity(quantityValue);
        this.quantityUnitCode = quantityValue == null ? optional(quantityUnitCode)
                : required(quantityUnitCode, "Specimen quantity unit is required");
        this.description = optional(description);
        this.removedAt = removedAt;
        this.fixedAt = fixedAt;
        this.receivedAt = receivedAt;
        this.labelCode = optional(labelCode);
        this.deletedAt = deletedAt;
        this.deletionReason = optional(deletionReason);
        if (deletedAt == null && this.deletionReason != null) {
            throw new IllegalArgumentException("An active specimen cannot have a deletion reason");
        }
        if (concurrencyVersion < 0) throw new IllegalArgumentException("Specimen version cannot be negative");
        this.concurrencyVersion = concurrencyVersion;
    }

    public static Specimen register(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String labelCode) {
        return register(id, caseId, specimenNo, specimenCode, specimenKindCode, sourceKindCode, sourceReference,
                collectionSite, collectionMethodCode, null, null, null, null, null, null, null, labelCode);
    }

    public static Specimen register(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String lateralityCode, BigDecimal quantityValue, String quantityUnitCode,
            String description, Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode) {
        String defaultName = collectionSite == null || collectionSite.isBlank() ? specimenKindCode : collectionSite;
        return registerWithSource(id, caseId, specimenNo, specimenCode, defaultName, specimenKindCode, REGISTRATION,
                sourceKindCode, sourceReference, collectionSite, collectionMethodCode, null, lateralityCode, quantityValue,
                quantityUnitCode, description, removedAt, fixedAt, receivedAt, labelCode);
    }

    public static Specimen registerWithSource(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenName, String specimenKindCode, String creationSourceCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String lateralityCode,
            BigDecimal quantityValue, String quantityUnitCode, String description, Instant removedAt,
            Instant fixedAt, Instant receivedAt, String labelCode) {
        return registerWithSource(id, caseId, specimenNo, specimenCode, specimenName, specimenKindCode,
                creationSourceCode, sourceKindCode, sourceReference, collectionSite, collectionMethodCode, null,
                lateralityCode, quantityValue, quantityUnitCode, description, removedAt, fixedAt, receivedAt,
                labelCode);
    }

    public static Specimen registerWithSource(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenName, String specimenKindCode, String creationSourceCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String preparationMethodCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode) {
        return new Specimen(id, caseId, specimenNo, specimenCode, specimenName, specimenKindCode,
                creationSourceCode, sourceKindCode, sourceReference, collectionSite, collectionMethodCode,
                preparationMethodCode, lateralityCode, quantityValue, quantityUnitCode, description, removedAt, fixedAt, receivedAt,
                labelCode, null, null, 0);
    }

    public static Specimen persisted(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenName, String specimenKindCode, String creationSourceCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String lateralityCode,
            BigDecimal quantityValue, String quantityUnitCode, String description, Instant removedAt,
            Instant fixedAt, Instant receivedAt, String labelCode, Instant deletedAt, String deletionReason,
            long concurrencyVersion) {
        return persisted(id, caseId, specimenNo, specimenCode, specimenName, specimenKindCode, creationSourceCode,
                sourceKindCode, sourceReference, collectionSite, collectionMethodCode, null, lateralityCode,
                quantityValue, quantityUnitCode, description, removedAt, fixedAt, receivedAt, labelCode, deletedAt,
                deletionReason, concurrencyVersion);
    }

    public static Specimen persisted(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenName, String specimenKindCode, String creationSourceCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String preparationMethodCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode, Instant deletedAt,
            String deletionReason, long concurrencyVersion) {
        return new Specimen(id, caseId, specimenNo, specimenCode, specimenName, specimenKindCode,
                creationSourceCode, sourceKindCode, sourceReference, collectionSite, collectionMethodCode,
                preparationMethodCode, lateralityCode, quantityValue, quantityUnitCode, description, removedAt, fixedAt, receivedAt,
                labelCode, deletedAt, deletionReason, concurrencyVersion);
    }

    public void updateDetails(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String labelCode,
            Instant updatedAt) {
        updateDetails(specimenCode, specimenName, specimenKindCode, sourceKindCode, sourceReference, collectionSite,
                collectionMethodCode, lateralityCode, quantityValue, quantityUnitCode, description, removedAt,
                fixedAt, receivedAt, labelCode, updatedAt);
    }

    public void updateDetails(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String lateralityCode,
            BigDecimal quantityValue, String quantityUnitCode, String description, Instant removedAt,
            Instant fixedAt, Instant receivedAt, String labelCode, Instant updatedAt) {
        updateDetails(specimenCode, specimenName, specimenKindCode, sourceKindCode, sourceReference, collectionSite,
                collectionMethodCode, lateralityCode, quantityValue, quantityUnitCode, description, removedAt,
                fixedAt, receivedAt, labelCode, updatedAt);
    }

    public void updateDetails(String specimenCode, String specimenName, String specimenKindCode,
            String sourceKindCode, String sourceReference, String collectionSite, String collectionMethodCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode, Instant updatedAt) {
        ensureNotDeleted();
        this.specimenCode = required(specimenCode, "Specimen code is required");
        this.specimenName = required(specimenName, "Specimen name is required");
        this.specimenKindCode = required(specimenKindCode, "Specimen kind is required");
        this.sourceKindCode = required(sourceKindCode, "Specimen source kind is required");
        this.sourceReference = required(sourceReference, "Specimen source reference is required");
        this.collectionSite = optional(collectionSite);
        this.collectionMethodCode = optional(collectionMethodCode);
        this.lateralityCode = optional(lateralityCode);
        this.quantityValue = validQuantity(quantityValue);
        this.quantityUnitCode = quantityValue == null ? optional(quantityUnitCode)
                : required(quantityUnitCode, "Specimen quantity unit is required");
        this.description = optional(description);
        this.removedAt = removedAt;
        this.fixedAt = fixedAt;
        this.receivedAt = receivedAt;
        this.labelCode = optional(labelCode);
        Objects.requireNonNull(updatedAt, "Specimen update time is required");
        concurrencyVersion++;
    }

    public void updatePreparationMethod(String preparationMethodCode, Instant updatedAt) {
        ensureNotDeleted();
        this.preparationMethodCode = optional(preparationMethodCode);
        Objects.requireNonNull(updatedAt, "Specimen update time is required");
        concurrencyVersion++;
    }

    public void softDelete(String reason, Instant deletedAt) {
        ensureNotDeleted();
        this.deletionReason = required(reason, "Specimen deletion reason is required");
        this.deletedAt = Objects.requireNonNull(deletedAt, "Specimen deletion time is required");
        concurrencyVersion++;
    }

    public boolean deleted() { return deletedAt != null; }
    public UUID id() { return id; }
    public UUID caseId() { return caseId; }
    public String specimenNo() { return specimenNo; }
    public String specimenCode() { return specimenCode; }
    public String specimenName() { return specimenName; }
    public String specimenKindCode() { return specimenKindCode; }
    public String creationSourceCode() { return creationSourceCode; }
    public String sourceKindCode() { return sourceKindCode; }
    public String sourceReference() { return sourceReference; }
    public String collectionSite() { return collectionSite; }
    public String collectionMethodCode() { return collectionMethodCode; }
    public String preparationMethodCode() { return preparationMethodCode; }
    public String lateralityCode() { return lateralityCode; }
    public BigDecimal quantityValue() { return quantityValue; }
    public String quantityUnitCode() { return quantityUnitCode; }
    public String description() { return description; }
    public Instant removedAt() { return removedAt; }
    public Instant fixedAt() { return fixedAt; }
    public Instant receivedAt() { return receivedAt; }
    public String labelCode() { return labelCode; }
    public Instant deletedAt() { return deletedAt; }
    public String deletionReason() { return deletionReason; }
    public long concurrencyVersion() { return concurrencyVersion; }

    private void ensureNotDeleted() {
        if (deleted()) throw new IllegalStateException("A deleted specimen cannot be changed");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static BigDecimal validQuantity(BigDecimal value) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException("Specimen quantity must be greater than zero");
        }
        return value;
    }
}
