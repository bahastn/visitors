package krd.parliament.vms.web;

import krd.parliament.vms.domain.CalendarBlock;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.AuditLogRepository;
import krd.parliament.vms.repo.CalendarBlockRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.OfficeScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Daily timeline: planned schedule next to what actually happened (late arrivals, overruns). */
@Controller
public class TimelineController {

    private final VisitRequestRepository requests;
    private final CalendarBlockRepository blocks;
    private final AuditLogRepository auditLogs;
    private final OfficeScope scope;

    public TimelineController(VisitRequestRepository requests, CalendarBlockRepository blocks, AuditLogRepository auditLogs,
                              OfficeScope scope) {
        this.scope = scope;
        this.requests = requests;
        this.blocks = blocks;
        this.auditLogs = auditLogs;
    }

    @GetMapping("/timeline")
    public String daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        LocalDate d = date == null ? LocalDate.now() : date;
        List<VisitRequest> visits = requests.findForDay(d.atStartOfDay(), d.plusDays(1).atStartOfDay(),
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING, VisitStatus.COMPLETED,
                        VisitStatus.NO_SHOW, VisitStatus.CANCELLED), scope.ids());
        List<CalendarBlock> dayBlocks = blocks.findOverlapping(d.atStartOfDay(), d.plusDays(1).atStartOfDay(), scope.ids());
        model.addAttribute("date", d);
        model.addAttribute("visits", visits);
        model.addAttribute("blocks", dayBlocks);
        model.addAttribute("lateCount", visits.stream().map(VisitRequest::getLateMinutes).filter(Objects::nonNull).filter(m -> m > 5).count());
        model.addAttribute("overrunCount", visits.stream().map(VisitRequest::getOverrunMinutes).filter(Objects::nonNull).filter(m -> m > 5).count());
        model.addAttribute("completedCount", visits.stream().filter(v -> v.getStatus() == VisitStatus.COMPLETED).count());
        model.addAttribute("noShowCount", visits.stream().filter(v -> v.getStatus() == VisitStatus.NO_SHOW).count());
        model.addAttribute("plannedMinutes", visits.stream().mapToLong(VisitRequest::getDurationMinutes).sum());
        model.addAttribute("actualMinutes", visits.stream().map(VisitRequest::getActualMeetingMinutes).filter(Objects::nonNull).mapToLong(Long::longValue).sum());
        return "timeline";
    }

    /** System-wide activity log (audit trail of all actions). */
    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','AUDITOR')")
    public String activity(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", auditLogs.findAllByOrderByTimestampDesc(PageRequest.of(page, 50)));
        return "activity";
    }
}
