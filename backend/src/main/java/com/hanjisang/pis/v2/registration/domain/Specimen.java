package com.hanjisang.pis.v2.registration.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Specimen {

    private final UUID id;
    private final UUID caseId;
    private final String specimenNo;
    private String specimenCode;
    private String specimenKindCode;
    private String sourceKindCode;
    private String sourceReference;
    private String collectionSite;
    private String collectionMethodCode;
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

    private Specimen(UUID id, UUID caseId, String specimenNo, String specimenCode, String specimenKindCode,
            String sourceKindCode, String sourceReference, String collectionSite, String collectionMethodCode,
            String lateralityCode, BigDecimal quantityValue, String quantityUnitCode, String description,
            Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode, Instant deletedAt,
            String deletionReason, long concurrencyVersion) {
        this.id = Objects.requireNonNull(id, "Specimen id is required");
        this.caseId = Objects.requireNonNull(caseId, "Specimen case is required");
        this.specimenNo = required(specimenNo, "Specimen number is required");
        this.specimenCode = required(specimenCode, "Specimen code is required");
        this.specimenKindCode = required(specimenKindCode, "Specimen kind is required");
        this.sourceKindCode = required(sourceKindCode, "Specimen source kind is required");
        this.sourceReference = required(sourceReference, "Specimen source reference is required");
        this.collectionSite = required(collectionSite, "Specimen site is required");
        this.collectionMethodCode = required(collectionMethodCode, "Specimen collection method is required");
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
        if (concurrencyVersion < 0) {
            throw new IllegalArgumentException("Specimen version cannot be negative");
        }
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
        return new Specimen(id, caseId, specimenNo, specimenCode, specimenKindCode, sourceKindCode, sourceReference,
                collectionSite, collectionMethodCode, lateralityCode, quantityValue, quantityUnitCode, description,
                removedAt, fixedAt, receivedAt, labelCode, null, null, 0);
    }

    public static Specimen persisted(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String labelCode, Instant deletedAt, String deletionReason,
            long concurrencyVersion) {
        return persisted(id, caseId, specimenNo, specimenCode, specimenKindCode, sourceKindCode, sourceReference,
                collectionSite, collectionMethodCode, null, null, null, null, null, null, null, labelCode,
                deletedAt, deletionReason, concurrencyVersion);
    }

    public static Specimen persisted(UUID id, UUID caseId, String specimenNo, String specimenCode,
            String specimenKindCode, String sourceKindCode, String sourceReference, String collectionSite,
            String collectionMethodCode, String lateralityCode, BigDecimal quantityValue, String quantityUnitCode,
            String description, Instant removedAt, Instant fixedAt, Instant receivedAt, String labelCode,
            Instant deletedAt, String deletionReason, long concurrencyVersion) {
        return new Specimen(id, caseId, specimenNo, specimenCode, specimenKindCode, sourceKindCode,
                sourceReference, collectionSite, collectionMethodCode, lateralityCode, quantityValue,
                quantityUnitCode, description, removedAt, fixedAt, receivedAt, labelCode, deletedAt,
                deletionReason, concurrencyVersion);
    }

    public void updateDetails(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String labelCode,
            Instant updatedAt) {
        updateDetails(specimenCode, specimenKindCode, sourceKindCode, sourceReference, collectionSite,
                collectionMethodCode, lateralityCode, quantityValue, quantityUnitCode, description, removedAt,
                fixedAt, receivedAt, labelCode, updatedAt);
    }

    public void updateDetails(String specimenCode, String specimenKindCode, String sourceKindCode,
            String sourceReference, String collectionSite, String collectionMethodCode, String lateralityCode,
            BigDecimal quantityValue, String quantityUnitCode, String description, Instant removedAt,
            Instant fixedAt, Instant receivedAt, String labelCode, Instant updatedAt) {
        ensureNotDeleted();
        this.specimenCode = required(specimenCode, "Specimen code is required");
        this.specimenKindCode = required(specimenKindCode, "Specimen kind is required");
        this.sourceKindCode = required(sourceKindCode, "Specimen source kind is required");
        this.sourceReference = required(sourceReference, "Specimen source reference is required");
        this.collectionSite = required(collectionSite, "Specimen site is required");
        this.collectionMethodCode = required(collectionMethodCode, "Specimen collection method is required");
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
    public String specimenKindCode() { return specimenKindCode; }
    public String sourceKindCode() { return sourceKindCode; }
    public String sourceReference() { return sourceReference; }
    public String collectionSite() { return collectionSite; }
    public String collectionMethodCode() { return collectionMethodCode; }
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
        if (deleted()) {
            throw new IllegalStateException("A deleted specimen cannot be changed");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal validQuantity(BigDecimal value) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException("Specimen quantity must be greater than zero");
        }
        return value;
    }
}
