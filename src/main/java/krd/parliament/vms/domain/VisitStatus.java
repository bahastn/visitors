package krd.parliament.vms.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Visitor lifecycle (section 2 of the plan):
 * Requested -> Under Review -> Approved / Rejected / Postponed / Referred -> Scheduled
 * -> Checked-in -> In Meeting -> Completed (or No-show / Cancelled).
 */
public enum VisitStatus {
    REQUESTED("secondary"),
    UNDER_REVIEW("info"),
    APPROVED("primary"),
    REJECTED("danger"),
    POSTPONED("warning"),
    REFERRED("dark"),
    SCHEDULED("primary"),
    CHECKED_IN("warning"),
    IN_MEETING("success"),
    COMPLETED("success"),
    NO_SHOW("danger"),
    CANCELLED("secondary");

    private final String color;

    VisitStatus(String color) {
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public boolean isFinal() {
        return this == REJECTED || this == REFERRED || this == COMPLETED || this == NO_SHOW || this == CANCELLED;
    }

    /** Requests still waiting for a decision (the waiting list). */
    public boolean isPending() {
        return this == REQUESTED || this == UNDER_REVIEW || this == POSTPONED;
    }

    /** Position on the request progress bar (0 = requested ... 6 = completed). */
    public int getStage() {
        return switch (this) {
            case REQUESTED -> 0;
            case UNDER_REVIEW, POSTPONED, REJECTED, REFERRED -> 1;
            case APPROVED -> 2;
            case SCHEDULED, CANCELLED, NO_SHOW -> 3;
            case CHECKED_IN -> 4;
            case IN_MEETING -> 5;
            case COMPLETED -> 6;
        };
    }

    /** The visit ended without taking place. */
    public boolean isStopped() {
        return this == REJECTED || this == REFERRED || this == CANCELLED || this == NO_SHOW;
    }

    public static Set<VisitStatus> pendingStatuses() {
        return EnumSet.of(REQUESTED, UNDER_REVIEW, POSTPONED);
    }

    public static Set<VisitStatus> occupyingCalendar() {
        return EnumSet.of(SCHEDULED, CHECKED_IN, IN_MEETING, COMPLETED);
    }

    public Set<VisitStatus> allowedNext() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED, POSTPONED, REFERRED, CANCELLED);
            case UNDER_REVIEW -> EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED, POSTPONED, REFERRED, CANCELLED);
            case POSTPONED -> EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED, REFERRED, CANCELLED);
            case APPROVED -> EnumSet.of(SCHEDULED, CHECKED_IN, CANCELLED);
            case SCHEDULED -> EnumSet.of(SCHEDULED, CHECKED_IN, NO_SHOW, CANCELLED);
            case CHECKED_IN -> EnumSet.of(IN_MEETING, COMPLETED, CANCELLED);
            case IN_MEETING -> EnumSet.of(COMPLETED);
            default -> EnumSet.noneOf(VisitStatus.class);
        };
    }

    public boolean canMoveTo(VisitStatus next) {
        return allowedNext().contains(next);
    }
}
