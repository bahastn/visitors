package krd.parliament.vms.web;

import krd.parliament.vms.domain.CalendarBlock;
import krd.parliament.vms.domain.VisitRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * iCalendar (.ics) output. Staff subscribe to their personal feed URL in Google Calendar or
 * Outlook ("Add calendar from URL"), which keeps the visit schedule in sync automatically.
 */
@Component
public class CalendarFeed {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public String build(List<VisitRequest> visits, List<CalendarBlock> blocks, String name) {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//Kurdistan Parliament//Visitor Management//EN");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "METHOD:PUBLISH");
        line(sb, "X-WR-CALNAME:" + esc(name));
        line(sb, "X-WR-TIMEZONE:Asia/Baghdad");
        line(sb, "REFRESH-INTERVAL;VALUE=DURATION:PT15M");
        for (VisitRequest r : visits) {
            if (r.getScheduledStart() == null) {
                continue;
            }
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:visit-" + r.getId() + "@vms.parliament.krd");
            line(sb, "DTSTAMP:" + utc(LocalDateTime.now()));
            line(sb, "DTSTART:" + utc(r.getScheduledStart()));
            line(sb, "DTEND:" + utc(r.getScheduledEnd() != null ? r.getScheduledEnd() : r.getScheduledStart().plusMinutes(30)));
            line(sb, "SUMMARY:" + esc(r.getVisitor().getShortName() + " — " + r.getSubject()));
            line(sb, "LOCATION:" + esc(r.getLocation() == null ? "" : r.getLocation()));
            String desc = r.getReferenceNumber() + "\nPhone: " + r.getVisitor().getPhone()
                    + (r.getVisitor().getOrganization() != null ? "\nOrganisation: " + r.getVisitor().getOrganization() : "")
                    + "\nStatus: " + r.getStatus()
                    + (r.getManagedBy() != null ? "\nManaged by: " + r.getManagedBy().getFullName() : "");
            line(sb, "DESCRIPTION:" + esc(desc));
            line(sb, "STATUS:" + (r.getStatus().name().equals("CANCELLED") ? "CANCELLED" : "CONFIRMED"));
            line(sb, "END:VEVENT");
        }
        for (CalendarBlock b : blocks) {
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:block-" + b.getId() + "@vms.parliament.krd");
            line(sb, "DTSTAMP:" + utc(LocalDateTime.now()));
            line(sb, "DTSTART:" + utc(b.getStartAt()));
            line(sb, "DTEND:" + utc(b.getEndAt()));
            line(sb, "SUMMARY:" + esc("[" + b.getType() + "] " + b.getTitle()));
            line(sb, "TRANSP:OPAQUE");
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        return sb.toString();
    }

    private static String utc(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private static void line(StringBuilder sb, String l) {
        sb.append(l).append("\r\n");
    }
}
