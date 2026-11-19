package krd.parliament.vms.domain;

import jakarta.persistence.*;
import krd.parliament.vms.domain.Enums.NotificationChannel;
import krd.parliament.vms.domain.Enums.NotificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Every outgoing message (SMS, WhatsApp, email) and every in-app alert to staff. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Phone number or email address for external messages. */
    @Column(length = 150)
    private String recipientAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser recipientUser;

    @Column(length = 250)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(length = 300)
    private String link;

    @ManyToOne(fetch = FetchType.LAZY)
    private VisitRequest request;

    @Column(length = 1000)
    private String error;

    private LocalDateTime sentAt;

    private boolean readFlag;
}
