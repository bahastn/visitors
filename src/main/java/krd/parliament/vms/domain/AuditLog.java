package krd.parliament.vms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Full audit trail: who did what and when. Drives the request and visitor timelines. */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_entity", columnList = "entityType,entityId"),
        @Index(name = "ix_audit_time", columnList = "timestamp")
})
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String entityType;

    private Long entityId;

    /** Machine action code, translated in the UI with key audit.&lt;action&gt;. */
    @Column(nullable = false, length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VisitStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VisitStatus toStatus;

    @Column(length = 2000)
    private String details;

    /** Visible on the citizen tracking portal. */
    private boolean publicEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser user;

    @Column(length = 120)
    private String actorName;

    @Column(length = 60)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}
