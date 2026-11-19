package krd.parliament.vms.service.messaging;

import krd.parliament.vms.domain.Enums.NotificationStatus;
import krd.parliament.vms.domain.Notification;
import krd.parliament.vms.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Sends queued messages in the background so staff never wait for a gateway. */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository repo;
    private final SmsGateway sms;
    private final WhatsAppGateway whatsapp;
    private final EmailGateway email;

    public NotificationDispatcher(NotificationRepository repo, SmsGateway sms, WhatsAppGateway whatsapp, EmailGateway email) {
        this.repo = repo;
        this.sms = sms;
        this.whatsapp = whatsapp;
        this.email = email;
    }

    @Async
    @Transactional
    public void dispatch(Long notificationId) {
        Notification n = repo.findById(notificationId).orElse(null);
        if (n == null || n.getStatus() == NotificationStatus.SENT) {
            return;
        }
        try {
            boolean delivered = switch (n.getChannel()) {
                case SMS -> sms.send(n.getRecipientAddress(), n.getBody());
                case WHATSAPP -> whatsapp.send(n.getRecipientAddress(), n.getBody());
                case EMAIL -> email.send(n.getRecipientAddress(), n.getSubject(), n.getBody());
                case IN_APP -> true;
            };
            n.setStatus(delivered ? NotificationStatus.SENT : NotificationStatus.LOGGED);
            n.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Notification {} failed: {}", n.getId(), e.getMessage());
            n.setStatus(NotificationStatus.FAILED);
            n.setError(e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage().substring(0, Math.min(1000, e.getMessage().length())));
        }
        repo.save(n);
    }
}
