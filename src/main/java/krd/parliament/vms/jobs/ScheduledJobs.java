package krd.parliament.vms.jobs;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.repo.OfficeRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.service.NotificationService;
import krd.parliament.vms.service.SettingsService;
import krd.parliament.vms.service.VisitRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Morning agenda for each office's head and director, and visit reminders to visitors. */
@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final VisitRequestRepository requests;
    private final OfficeRepository offices;
    private final UserRepository users;
    private final NotificationService notify;
    private final SettingsService settings;
    private final VisitRequestService requestService;

    public ScheduledJobs(VisitRequestRepository requests, OfficeRepository offices, UserRepository users,
                         NotificationService notify, SettingsService settings, VisitRequestService requestService) {
        this.requests = requests;
        this.offices = offices;
        this.users = users;
        this.notify = notify;
        this.settings = settings;
        this.requestService = requestService;
    }

    @Scheduled(cron = "${app.jobs.morning-summary-cron}", zone = "${app.time-zone}")
    @Transactional
    public void morningSummary() {
        if (settings.getBool(SettingsService.MORNING_SUMMARY)) {
            sendMorningSummary(null);
        }
    }

    /** Sends today's agenda to head and director of each office (or of the given offices). Returns recipients. */
    @Transactional
    public int sendMorningSummary(Collection<Long> officeIds) {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Office o : offices.findByActiveTrueAndExternalFalseOrderBySortOrderAscNameCkbAsc()) {
            if (officeIds != null && !officeIds.contains(o.getId())) {
                continue;
            }
            List<VisitRequest> agenda = requests.findScheduledBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                    EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN), Set.of(o.getId()));
            long pending = requests.countByStatusInAndOfficeIdIn(VisitStatus.pendingStatuses(), Set.of(o.getId()));
            if (agenda.isEmpty() && pending == 0) {
                continue;
            }
            for (AppUser u : users.findOfficeStaff(NotificationService.DECIDERS, o)) {
                Locale loc = NotificationService.localeOf(u.getPreferredLanguage());
                StringBuilder body = new StringBuilder(notify.text(u, "notif.morning.header", o.name(loc), agenda.size()));
                for (VisitRequest r : agenda) {
                    body.append('\n').append(r.getScheduledStart().format(HM)).append("  ").append(r.getVisitor().getFullName());
                    if (r.getVisitor().getOrganization() != null) {
                        body.append(" (").append(r.getVisitor().getOrganization()).append(")");
                    }
                    body.append(" — ").append(r.getSubject());
                }
                body.append("\n\n").append(notify.text(u, "notif.morning.pending", pending));
                String subject = notify.text(u, "notif.morning.subject", today.toString());
                notify.inApp(u, subject, body.toString(), "/timeline?date=" + today);
                notify.emailUser(u, subject, body.toString());
                count++;
            }
        }
        log.info("Morning summary sent to {} users", count);
        return count;
    }

    @Scheduled(cron = "${app.jobs.reminder-cron}", zone = "${app.time-zone}")
    @Transactional
    public void reminders() {
        int hours = settings.getInt(SettingsService.REMINDER_HOURS);
        LocalDateTime now = LocalDateTime.now();
        for (VisitRequest r : requests.findNeedingReminder(now, now.plusHours(hours))) {
            notify.notifyVisitor(r, "reminder", notify.passLink(r), r.getLocation());
            requestService.markReminderSent(r);
        }
    }
}
