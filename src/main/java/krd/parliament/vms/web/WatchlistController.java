package krd.parliament.vms.web;

import jakarta.validation.Valid;
import krd.parliament.vms.domain.Notification;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.domain.WatchlistEntry;
import krd.parliament.vms.repo.NotificationRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.repo.WatchlistRepository;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.util.Phones;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/** Security officer: expected arrivals, watchlist alerts and the watchlist itself. */
@Controller
public class WatchlistController {

    private final WatchlistRepository repo;
    private final VisitRequestRepository requests;
    private final NotificationRepository notifications;
    private final CryptoService crypto;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final Flash flash;
    @org.springframework.beans.factory.annotation.Autowired
    private OfficeScope scope;

    public WatchlistController(WatchlistRepository repo, VisitRequestRepository requests, NotificationRepository notifications,
                               CryptoService crypto, CurrentUser currentUser, AuditService audit, Flash flash) {
        this.repo = repo;
        this.requests = requests;
        this.notifications = notifications;
        this.crypto = crypto;
        this.currentUser = currentUser;
        this.audit = audit;
        this.flash = flash;
    }

    @InitBinder("entry")
    void bind(WebDataBinder b) {
        b.setAllowedFields("fullName", "phone", "level", "reason", "active");
    }

    @GetMapping("/security")
    public String securityDashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<VisitRequest> expected = requests.findForDay(today.atStartOfDay(), today.plusDays(3).atStartOfDay(),
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING), scope.ids());
        model.addAttribute("expected", expected);
        model.addAttribute("flagged", expected.stream().filter(VisitRequest::isWatchlistFlag).toList());
        List<Notification> alerts = notifications.findByRecipientUserOrderByCreatedAtDesc(currentUser.get(), PageRequest.of(0, 20))
                .getContent().stream().filter(n -> n.getSubject() != null && n.getSubject().contains("⚠")).toList();
        model.addAttribute("alerts", alerts);
        return "security";
    }

    @GetMapping("/watchlist")
    public String list(Model model) {
        model.addAttribute("entries", repo.findAllByOrderByActiveDescCreatedAtDesc());
        model.addAttribute("entry", new WatchlistEntry());
        return "watchlist";
    }

    @PostMapping("/watchlist")
    public String add(@Valid @ModelAttribute("entry") WatchlistEntry entry, BindingResult br,
                      @RequestParam(required = false) String idNumber, RedirectAttributes ra) {
        if (br.hasErrors()) {
            flash.error(ra, "error.required");
            return "redirect:/watchlist";
        }
        if (idNumber != null && !idNumber.isBlank()) {
            entry.setIdNumberHash(crypto.hash(idNumber));
            String t = idNumber.trim();
            entry.setIdHint(t.length() > 4 ? t.substring(t.length() - 4) : t);
        }
        entry.setPhone(Phones.normalize(entry.getPhone()));
        entry.setActive(true);
        entry.setAddedBy(currentUser.get());
        repo.save(entry);
        audit.log(AuditService.SYSTEM, entry.getId(), "WATCHLIST_ADDED", entry.getFullName() + " — " + entry.getLevel());
        flash.ok(ra, "msg.saved");
        return "redirect:/watchlist";
    }

    @PostMapping("/watchlist/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        repo.findById(id).ifPresent(w -> {
            w.setActive(!w.isActive());
            repo.save(w);
            audit.log(AuditService.SYSTEM, id, w.isActive() ? "WATCHLIST_ACTIVATED" : "WATCHLIST_DEACTIVATED", w.getFullName());
        });
        flash.ok(ra, "msg.saved");
        return "redirect:/watchlist";
    }
}
