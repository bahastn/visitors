package krd.parliament.vms.web;

import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.OfficeScope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Installable mobile app (PWA) for office heads and directors: one-tap decisions, agenda, lobby. */
@Controller
public class MobileController {

    private final VisitRequestRepository requests;
    private final OfficeScope scope;

    public MobileController(VisitRequestRepository requests, OfficeScope scope) {
        this.requests = requests;
        this.scope = scope;
    }

    @GetMapping("/m")
    public String mobile(Model model) {
        Set<Long> ids = scope.ids();
        LocalDate today = LocalDate.now();
        List<VisitRequest> pending = requests.findByStatusInAndOfficeIdIn(VisitStatus.pendingStatuses(), ids).stream()
                .sorted(Comparator.comparing((VisitRequest r) -> r.getRecommendation() == null ? 1 : 0)
                        .thenComparing(r -> r.getPriority().getRank())
                        .thenComparing(VisitRequest::getCreatedAt)).toList();
        model.addAttribute("pending", pending);
        model.addAttribute("agenda", requests.findForDay(today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING, VisitStatus.COMPLETED), ids));
        model.addAttribute("lobby", requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.CHECKED_IN), ids));
        return "mobile";
    }
}
