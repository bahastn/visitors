package krd.parliament.vms.web;

import krd.parliament.vms.domain.Priority;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.OfficeScope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Waiting list of pending requests and the live lobby queue. */
@Controller
public class WaitingListController {

    private final VisitRequestRepository requests;
    private final OfficeScope scope;

    public WaitingListController(VisitRequestRepository requests, OfficeScope scope) {
        this.requests = requests;
        this.scope = scope;
    }

    @GetMapping("/waiting-list")
    public String waitingList(@RequestParam(defaultValue = "priority") String sort,
                              @RequestParam(required = false) Priority priority,
                              @RequestParam(defaultValue = "false") boolean overdueOnly,
                              @RequestParam(defaultValue = "false") boolean recommendedOnly, Model model) {
        Comparator<VisitRequest> cmp = switch (sort) {
            case "oldest" -> Comparator.comparing(VisitRequest::getCreatedAt);
            case "newest" -> Comparator.comparing(VisitRequest::getCreatedAt).reversed();
            case "preferred" -> Comparator.comparing(VisitRequest::getPreferredDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing((VisitRequest r) -> r.getPriority().getRank()).thenComparing(VisitRequest::getCreatedAt);
        };
        List<VisitRequest> all = requests.findByStatusInAndOfficeIdIn(VisitStatus.pendingStatuses(), scope.ids());
        List<VisitRequest> list = all.stream()
                .filter(r -> priority == null || r.getPriority() == priority)
                .filter(r -> !overdueOnly || r.isOverdue(r.getOffice().getOverdueDays()))
                .filter(r -> !recommendedOnly || r.getRecommendation() != null)
                .sorted(cmp).toList();
        model.addAttribute("list", list);
        model.addAttribute("totalPending", all.size());
        model.addAttribute("overdueTotal", all.stream().filter(r -> r.isOverdue(r.getOffice().getOverdueDays())).count());
        model.addAttribute("recommendedTotal", all.stream().filter(r -> r.getRecommendation() != null).count());
        model.addAttribute("sort", sort);
        model.addAttribute("f_priority", priority);
        model.addAttribute("overdueOnly", overdueOnly);
        model.addAttribute("recommendedOnly", recommendedOnly);
        model.addAttribute("approved", requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.APPROVED), scope.ids()));
        return "waiting-list";
    }

    @GetMapping("/queue")
    public String queue(Model model) {
        List<VisitRequest> lobby = requests.findByStatusInAndOfficeIdIn(
                        EnumSet.of(VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING), scope.ids()).stream()
                .sorted(Comparator.comparing((VisitRequest r) -> r.getStatus() == VisitStatus.IN_MEETING ? 0 : 1)
                        .thenComparing(r -> r.getPriority().getRank())
                        .thenComparing(VisitRequest::getCheckInAt)).toList();
        model.addAttribute("lobby", lobby);
        return "queue";
    }
}
