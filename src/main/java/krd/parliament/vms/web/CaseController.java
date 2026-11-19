package krd.parliament.vms.web;

import krd.parliament.vms.domain.ConstituentCase;
import krd.parliament.vms.domain.Enums.CaseStatus;
import krd.parliament.vms.domain.FollowUpTask;
import krd.parliament.vms.repo.ConstituentCaseRepository;
import krd.parliament.vms.repo.FollowUpTaskRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.NotFoundException;
import krd.parliament.vms.service.VisitorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Constituent case tracking: repeat visits about the same issue linked into one case. */
@Controller
@RequestMapping("/cases")
public class CaseController {

    private final ConstituentCaseRepository cases;
    private final FollowUpTaskRepository tasks;
    private final UserRepository users;
    private final VisitorService visitors;
    private final AuditService audit;
    private final Flash flash;
    private final OfficeScope scope;

    public CaseController(ConstituentCaseRepository cases, FollowUpTaskRepository tasks, UserRepository users,
                          VisitorService visitors, AuditService audit, Flash flash, OfficeScope scope) {
        this.scope = scope;
        this.cases = cases;
        this.tasks = tasks;
        this.users = users;
        this.visitors = visitors;
        this.audit = audit;
        this.flash = flash;
    }

    @GetMapping
    public String list(@RequestParam(required = false) CaseStatus status, Model model) {
        List<ConstituentCase> list = cases.findByOfficeIdInOrderByUpdatedAtDesc(scope.ids()).stream()
                .filter(c -> status == null || c.getStatus() == status).toList();
        model.addAttribute("cases", list);
        model.addAttribute("f_status", status);
        return "cases/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        ConstituentCase c = load(id);
        model.addAttribute("c", c);
        model.addAttribute("tasks", tasks.findByConstituentCaseOrderByDueDateAsc(c));
        model.addAttribute("staff", users.findAllByOrderByFullNameAsc());
        model.addAttribute("trail", audit.trail(AuditService.CASE, id));
        return "cases/view";
    }

    private ConstituentCase load(Long id) {
        ConstituentCase c = cases.findById(id).orElseThrow(() -> new NotFoundException("Case"));
        if (c.getOffice() != null) {
            scope.check(c.getOffice());
        }
        return c;
    }

    @PostMapping
    public String create(@RequestParam Long visitorId, @RequestParam String title,
                         @RequestParam(required = false) String description, RedirectAttributes ra) {
        if (scope.defaultOffice() == null) {
            flash.error(ra, "error.officeRequired");
            return "redirect:/visitors/" + visitorId;
        }
        ConstituentCase c = new ConstituentCase();
        c.setOffice(scope.defaultOffice());
        c.setVisitor(visitors.get(visitorId));
        c.setTitle(title);
        c.setDescription(description);
        c.setCaseNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        c = cases.save(c);
        c.setCaseNumber(String.format("CASE-%d-%04d", LocalDate.now().getYear(), c.getId()));
        cases.save(c);
        audit.log(AuditService.CASE, c.getId(), "CASE_CREATED", title);
        flash.ok(ra, "msg.caseCreated", c.getCaseNumber());
        return "redirect:/cases/" + c.getId();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam String title, @RequestParam(required = false) String description,
                         @RequestParam CaseStatus status, @RequestParam(required = false) Long assignedToId,
                         @RequestParam(required = false) String relatedMinistry, @RequestParam(required = false) String resolution,
                         RedirectAttributes ra) {
        ConstituentCase c = load(id);
        CaseStatus old = c.getStatus();
        c.setTitle(title);
        c.setDescription(description);
        c.setStatus(status);
        c.setAssignedTo(assignedToId == null ? null : users.findById(assignedToId).orElse(null));
        c.setRelatedMinistry(relatedMinistry);
        c.setResolution(resolution);
        if ((status == CaseStatus.CLOSED || status == CaseStatus.RESOLVED) && c.getClosedAt() == null) {
            c.setClosedAt(LocalDateTime.now());
        }
        cases.save(c);
        audit.log(AuditService.CASE, id, old != status ? "CASE_STATUS_" + status : "CASE_UPDATED", null);
        flash.ok(ra, "msg.saved");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/tasks")
    public String addTask(@PathVariable Long id, @RequestParam String title, @RequestParam(required = false) Long ownerId,
                          @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                                  iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dueDate,
                          RedirectAttributes ra) {
        ConstituentCase c = load(id);
        FollowUpTask t = new FollowUpTask();
        t.setConstituentCase(c);
        t.setOffice(c.getOffice());
        t.setTitle(title);
        t.setDueDate(dueDate);
        t.setOwner(ownerId == null ? null : users.findById(ownerId).orElse(null));
        tasks.save(t);
        audit.log(AuditService.CASE, id, "TASK_CREATED", title);
        flash.ok(ra, "msg.saved");
        return "redirect:/cases/" + id;
    }
}
