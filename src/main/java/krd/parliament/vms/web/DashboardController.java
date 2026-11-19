package krd.parliament.vms.web;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.TaskStatus;
import krd.parliament.vms.jobs.ScheduledJobs;
import krd.parliament.vms.repo.FollowUpTaskRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Role-aware dashboard for the selected office (or all of the user's offices). */
@Controller
public class DashboardController {

    private final VisitRequestRepository requests;
    private final FollowUpTaskRepository tasks;
    private final CurrentUser currentUser;
    private final OfficeScope scope;
    private final ReportService reports;
    private final ScheduledJobs jobs;
    private final Flash flash;

    public DashboardController(VisitRequestRepository requests, FollowUpTaskRepository tasks, CurrentUser currentUser,
                               OfficeScope scope, ReportService reports, ScheduledJobs jobs, Flash flash) {
        this.requests = requests;
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.scope = scope;
        this.reports = reports;
        this.jobs = jobs;
        this.flash = flash;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Set<Long> ids = scope.ids();
        LocalDate today = LocalDate.now();
        List<VisitRequest> agenda = requests.findForDay(today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING, VisitStatus.COMPLETED,
                        VisitStatus.NO_SHOW), ids);
        List<VisitRequest> pending = requests.findByStatusInAndOfficeIdIn(VisitStatus.pendingStatuses(), ids).stream()
                .sorted(Comparator.comparing((VisitRequest r) -> r.getPriority().getRank())
                        .thenComparing(VisitRequest::getCreatedAt)).toList();
        List<VisitRequest> lobby = requests.findByStatusInAndOfficeIdIn(
                EnumSet.of(VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING), ids);

        model.addAttribute("agenda", agenda);
        model.addAttribute("pending", pending.stream().limit(8).toList());
        model.addAttribute("pendingCount", pending.size());
        model.addAttribute("overdueCount", pending.stream().filter(r -> r.isOverdue(r.getOffice().getOverdueDays())).count());
        model.addAttribute("awaitingDecision", pending.stream().filter(r -> r.getRecommendation() != null).toList());
        model.addAttribute("lobby", lobby);
        model.addAttribute("approvedUnscheduled", requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.APPROVED), ids));
        model.addAttribute("recent", requests.findTop12ByOfficeIdInOrderByUpdatedAtDesc(ids));
        model.addAttribute("myTasks", tasks.findByOwnerAndStatusInOrderByDueDateAsc(currentUser.require(),
                EnumSet.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS)));
        model.addAttribute("month", reports.build(today.withDayOfMonth(1), today, ids));
        return "dashboard";
    }

    @PostMapping("/dashboard/morning-summary")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HEAD','SECRETARY')")
    public String sendSummary(RedirectAttributes ra) {
        int n = jobs.sendMorningSummary(scope.ids());
        flash.ok(ra, "msg.summarySent", n);
        return "redirect:/dashboard";
    }
}
