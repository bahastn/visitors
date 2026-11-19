package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import krd.parliament.vms.domain.Enums.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** A request to visit the Deputy Speaker, from intake through to completion. */
@Entity
@Table(name = "visit_requests", indexes = {
        @Index(name = "ix_req_status", columnList = "status"),
        @Index(name = "ix_req_sched", columnList = "scheduledStart"),
        @Index(name = "ix_req_qr", columnList = "qrToken", unique = true)
})
@Getter
@Setter
public class VisitRequest extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String referenceNumber;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Visitor visitor;

    /** The office the visitor will visit. */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Office office;

    // ---- Request details ----
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestChannel channel = RequestChannel.WALK_IN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VisitPurpose purpose = VisitPurpose.CONSTITUENT_COMPLAINT;

    @NotBlank
    @Size(max = 250)
    @Column(nullable = false, length = 250)
    private String subject;

    @Size(max = 5000)
    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status = VisitStatus.REQUESTED;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate preferredDate;

    @Size(max = 100)
    @Column(length = 100)
    private String preferredTime;

    /** Requested meeting length: 15, 30, 45, 60 or 90 minutes. */
    private int durationMinutes = 30;

    /** Number of people in the delegation including the main visitor. */
    private int expectedAttendees = 1;

    private boolean needsInterpreter;

    @Size(max = 300)
    @Column(length = 300)
    private String accessibilityNeeds;

    @Size(max = 30)
    @Column(length = 30)
    private String vehiclePlate;

    @Size(max = 300)
    @Column(length = 300)
    private String referredByPerson; // e.g. "MP ... recommended this visit"

    // ---- Who manages the visit ----
    /** Staff member responsible for following this visit (case officer). */
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser managedBy;

    /** Who the visitor will meet (normally the Deputy Speaker; may be the director on his behalf). */
    @Size(max = 150)
    @Column(length = 150)
    private String meetingWith;

    @Size(max = 100)
    @Column(length = 100)
    private String location; // meeting room

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;

    // ---- Recommendation (level 1) ----
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Recommendation recommendation;

    @Column(length = 1000)
    private String recommendationNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser recommendedBy;

    private LocalDateTime recommendedAt;

    // ---- Decision (level 2) ----
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 1000)
    private String decisionReason;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate postponedUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    private Office referredTo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Office referredFrom;

    @Column(length = 60)
    private String externalReference; // reference in the other office's system

    // ---- Schedule & actual times ----
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime checkInAt;
    private LocalDateTime meetingStartAt;
    private LocalDateTime meetingEndAt;
    private LocalDateTime checkOutAt;

    private Integer queueNumber;
    private LocalDate queueDate;

    @Column(length = 64)
    private String qrToken;

    private boolean reminderSent;
    private boolean watchlistFlag;

    // ---- Outcome ----
    @Column(length = 5000)
    private String outcomeSummary;

    @Column(length = 5000)
    private String aiSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    private ConstituentCase constituentCase;

    @Column(length = 64, unique = true)
    private String publicToken; // for feedback and public tracking

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<VisitMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt desc")
    private List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt desc")
    private List<VisitNote> notes = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dueDate")
    private List<FollowUpTask> tasks = new ArrayList<>();

    // ---- Derived values used by the waiting list, timeline and reports ----

    public long getDaysWaiting() {
        LocalDateTime end = decidedAt != null ? decidedAt : LocalDateTime.now();
        return ChronoUnit.DAYS.between(createdAt == null ? LocalDateTime.now() : createdAt, end);
    }

    public boolean isOverdue(int overdueDays) {
        return status.isPending() && getDaysWaiting() >= overdueDays;
    }

    /** Minutes waited in the lobby (check-in until meeting start, or until now). */
    public Long getLobbyWaitMinutes() {
        if (checkInAt == null) {
            return null;
        }
        LocalDateTime end = meetingStartAt != null ? meetingStartAt : LocalDateTime.now();
        return Duration.between(checkInAt, end).toMinutes();
    }

    /** Minutes late compared to the scheduled time (negative = early). */
    public Long getLateMinutes() {
        if (scheduledStart == null || checkInAt == null) {
            return null;
        }
        return Duration.between(scheduledStart, checkInAt).toMinutes();
    }

    /** Minutes the meeting ran over its scheduled end. */
    public Long getOverrunMinutes() {
        if (scheduledEnd == null || meetingEndAt == null) {
            return null;
        }
        return Duration.between(scheduledEnd, meetingEndAt).toMinutes();
    }

    public Long getActualMeetingMinutes() {
        if (meetingStartAt == null || meetingEndAt == null) {
            return null;
        }
        return Duration.between(meetingStartAt, meetingEndAt).toMinutes();
    }
}
