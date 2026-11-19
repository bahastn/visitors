package krd.parliament.vms.service;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.ContactChannel;
import krd.parliament.vms.domain.Enums.NotificationChannel;
import krd.parliament.vms.domain.Enums.NotificationStatus;
import krd.parliament.vms.repo.NotificationRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.service.messaging.EmailGateway;
import krd.parliament.vms.service.messaging.NotificationDispatcher;
import krd.parliament.vms.util.Dates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/**
 * Messages to visitors (SMS / WhatsApp / email) and in-app alerts to staff.
 * Every text is taken from the message files and written in the recipient's own language.
 */
@Service
public class NotificationService {

    public static final Set<Role> DECIDERS = EnumSet.of(Role.HEAD, Role.DIRECTOR);
    public static final Set<Role> SCHEDULERS = EnumSet.of(Role.SECRETARY, Role.DIRECTOR);

    private final NotificationRepository repo;
    private final UserRepository users;
    private final NotificationDispatcher dispatcher;
    private final MessageSource messages;
    private final Dates dates;
    private final EmailGateway email;

    @Value("${app.base-url}")
    private String baseUrl;

    public NotificationService(NotificationRepository repo, UserRepository users, NotificationDispatcher dispatcher,
                               MessageSource messages, Dates dates, EmailGateway email) {
        this.repo = repo;
        this.users = users;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.dates = dates;
        this.email = email;
    }

    /** A translatable argument, resolved in each recipient's language (e.g. an enum label). */
    public static MessageSourceResolvable t(String key) {
        return new DefaultMessageSourceResolvable(new String[]{key}, key);
    }

    public static MessageSourceResolvable t(Enum<?> e) {
        return t("enum." + e.getDeclaringClass().getSimpleName() + "." + e.name());
    }

    public static Locale localeOf(String lang) {
        return Locale.forLanguageTag(lang == null || lang.isBlank() || "kmr".equals(lang) ? "ckb" : lang);
    }

    public String baseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String passLink(VisitRequest r) {
        return baseUrl() + "/public/pass/" + r.getQrToken();
    }

    public String trackLink(VisitRequest r) {
        return baseUrl() + "/public/track?ref=" + r.getReferenceNumber();
    }

    public String feedbackLink(VisitRequest r) {
        return baseUrl() + "/public/feedback/" + r.getPublicToken();
    }

    // ------------------------------------------------------------------ visitors

    /**
     * Message to the visitor, template "msg.visitor.&lt;event&gt;":
     * {0} name, {1} reference, {2} date, {3} time, {4} link, {5} extra, {6} office / host, {7} location.
     */
    public void notifyVisitor(VisitRequest r, String event, String link, Object extra) {
        Visitor v = r.getVisitor();
        if (v == null || !v.isConsentToMessages()) {
            return;
        }
        Locale locale = localeOf(v.getPreferredLanguage() == null ? null : v.getPreferredLanguage().name());
        Office o = r.getOffice();
        Object[] args = {v.getShortName(), r.getReferenceNumber(),
                r.getScheduledStart() == null ? "" : dates.date(r.getScheduledStart()),
                r.getScheduledStart() == null ? "" : dates.time(r.getScheduledStart()),
                link == null ? "" : link,
                extra == null ? "" : extra,
                o == null ? "" : o.hostTitle(locale),
                r.getLocation() == null ? "" : r.getLocation()};
        String body = messages.getMessage("msg.visitor." + event, args, locale);
        String subject = messages.getMessage("msg.visitor.subject",
                new Object[]{r.getReferenceNumber(), o == null ? "" : o.name(locale)}, locale);

        ContactChannel pref = v.getPreferredContactChannel() == null ? ContactChannel.SMS : v.getPreferredContactChannel();
        Notification n = new Notification();
        n.setRequest(r);
        n.setSubject(subject);
        n.setBody(body);
        n.setLink(link);
        switch (pref) {
            case WHATSAPP -> {
                n.setChannel(NotificationChannel.WHATSAPP);
                n.setRecipientAddress(v.getWhatsappNumber() != null && !v.getWhatsappNumber().isBlank()
                        ? v.getWhatsappNumber() : v.getPhone());
            }
            case EMAIL -> {
                if (v.getEmail() != null && !v.getEmail().isBlank()) {
                    n.setChannel(NotificationChannel.EMAIL);
                    n.setRecipientAddress(v.getEmail());
                } else {
                    n.setChannel(NotificationChannel.SMS);
                    n.setRecipientAddress(v.getPhone());
                }
            }
            default -> {
                n.setChannel(NotificationChannel.SMS);
                n.setRecipientAddress(v.getPhone());
            }
        }
        queue(repo.save(n));

        if (n.getChannel() != NotificationChannel.EMAIL && v.getEmail() != null && !v.getEmail().isBlank()
                && email.isConfigured()) {
            Notification e = new Notification();
            e.setRequest(r);
            e.setChannel(NotificationChannel.EMAIL);
            e.setRecipientAddress(v.getEmail());
            e.setSubject(subject);
            e.setBody(body);
            e.setLink(link);
            queue(repo.save(e));
        }
    }

    // ------------------------------------------------------------------ staff (in-app)

    /** In-app alert using "notif.&lt;key&gt;.subject" and "notif.&lt;key&gt;.body", in each user's language. */
    public void notifyUsers(Collection<AppUser> recipients, String key, Object[] args, String link, VisitRequest r) {
        Set<Long> seen = new HashSet<>();
        for (AppUser u : recipients) {
            if (u == null || !seen.add(u.getId())) {
                continue;
            }
            Locale loc = localeOf(u.getPreferredLanguage());
            Notification n = new Notification();
            n.setChannel(NotificationChannel.IN_APP);
            n.setRecipientUser(u);
            n.setSubject(messages.getMessage("notif." + key + ".subject", args, loc));
            n.setBody(messages.getMessage("notif." + key + ".body", args, loc));
            n.setLink(link);
            n.setRequest(r);
            n.setStatus(NotificationStatus.SENT);
            repo.save(n);
        }
    }

    /** Staff assigned to the office with one of the roles. */
    public void notifyOfficeStaff(Office office, Set<Role> roles, String key, Object[] args, String link, VisitRequest r) {
        if (office != null) {
            notifyUsers(users.findOfficeStaff(roles, office), key, args, link, r);
        }
    }

    /** Staff with access to the office, including central teams (reception, security). */
    public void notifyWithAccess(Office office, Set<Role> roles, String key, Object[] args, String link, VisitRequest r) {
        if (office != null) {
            notifyUsers(users.findWithAccess(roles, office), key, args, link, r);
        }
    }

    /** Staff message rendered in the user's language, for email / SMS copies (e.g. morning summary). */
    public String text(AppUser u, String key, Object... args) {
        return messages.getMessage(key, args, localeOf(u.getPreferredLanguage()));
    }

    public void emailUser(AppUser u, String subject, String body) {
        if (u.getEmail() == null || u.getEmail().isBlank()) {
            return;
        }
        Notification n = new Notification();
        n.setChannel(NotificationChannel.EMAIL);
        n.setRecipientAddress(u.getEmail());
        n.setSubject(subject);
        n.setBody(body);
        queue(repo.save(n));
    }

    public void inApp(AppUser u, String subject, String body, String link) {
        Notification n = new Notification();
        n.setChannel(NotificationChannel.IN_APP);
        n.setRecipientUser(u);
        n.setSubject(subject);
        n.setBody(body);
        n.setLink(link);
        n.setStatus(NotificationStatus.SENT);
        repo.save(n);
    }

    public void retry(Notification n) {
        n.setStatus(NotificationStatus.PENDING);
        n.setError(null);
        queue(repo.save(n));
    }

    private void queue(Notification n) {
        if (n.getChannel() == NotificationChannel.IN_APP) {
            return;
        }
        Long id = n.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatch(id);
                }
            });
        } else {
            dispatcher.dispatch(id);
        }
    }
}
