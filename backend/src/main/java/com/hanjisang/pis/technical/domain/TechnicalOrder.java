package com.hanjisang.pis.technical.domain;

import java.util.Set;

public final class TechnicalOrder {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String RETURNED = "RETURNED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String WAITING_RESULT = "WAITING_RESULT";
    public static final String PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> TERMINAL = Set.of(COMPLETED, CANCELLED);

    private TechnicalOrder() {
    }

    public static void requireDraft(String state) {
        if (!DRAFT.equals(state) && !RETURNED.equals(state)) {
            throw new IllegalStateException("technical order is not editable");
        }
    }

    public static void requireNotTerminal(String state) {
        if (TERMINAL.contains(state)) {
            throw new IllegalStateException("technical order is terminal");
        }
    }

    public static String deriveState(boolean submitted, boolean returned, boolean allApproved, boolean anyHandoff,
            boolean anyWaitingResult, boolean allClosed, boolean anyClosed, boolean allCancelled) {
        if (allCancelled) return CANCELLED;
        if (allClosed) return COMPLETED;
        if (anyHandoff && anyWaitingResult) return WAITING_RESULT;
        if (anyHandoff) return IN_PROGRESS;
        if (anyClosed) return PARTIALLY_COMPLETED;
        if (returned) return RETURNED;
        if (allApproved) return ACCEPTED;
        return submitted ? SUBMITTED : DRAFT;
    }
}
