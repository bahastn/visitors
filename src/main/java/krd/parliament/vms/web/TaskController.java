package krd.parliament.vms.web;

import krd.parliament.vms.domain.Enums.TaskStatus;
import krd.parliament.vms.domain.FollowUpTask;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.repo.FollowUpTaskRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.NotFoundException;
import krd.parliament.vms.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/** Follow-up tasks: promises made during visits, with owner and deadline. */
@Controller
@RequestMapping("/tasks")
public class TaskController {

    private final FollowUpTaskRepository tasks;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final OfficeScope scope;
    private final AuditService audit;
    private final NotificationService notify;
    private final Flash flash;

    public TaskController(FollowUpTaskRepository tasks, UserRepository users, CurrentUser currentUser, OfficeScope scope,
                          AuditService audit, NotificationService notify, Flash flash) {
        this.tasks = tasks;
        this.users = users;
        this.currentUser = currentUser;
        this.scope = scope;
        this.audit = audit;
        this.notify = notify;
        this.flash = flash;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "mine") String view, Model model) {
        List<FollowUpTask> list = switch (view) {
            case "open" -> tasks.findByStatusInAndOfficeIdInOrderByDueDateAsc(EnumSet.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS), scope.ids());
            case "all" -> tasks.findByOfficeIdInOrderByCreatedAtDesc(scope.ids());
            default -> tasks.findByOwnerAndStatusInOrderByDueDateAsc(currentUser.require(), EnumSet.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS));
        };
        model.addAttribute("tasks", list);
        model.addAttribute("view", view);
        model.addAttribute("staff", users.findAllByOrderByFullNameAsc());
        return "tasks";
    }

    @PostMapping
    public String create(@RequestParam String title, @RequestParam(required = false) String description,
                         @RequestParam(required = false) Long ownerId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
                         RedirectAttributes ra) {
        Office office = scope.defaultOffice();
        if (office == null) {
            flash.error(ra, "error.officeRequired");
            return "redirect:/tasks";
        }
        FollowUpTask t = new FollowUpTask();
        t.setTitle(title);
        t.setDescription(description);
        t.setDueDate(dueDate);
        t.setOffice(office);
        t.setOwner(ownerId == null ? null : users.findById(ownerId).orElse(null));
        t.setCreatedBy(currentUser.get());
        tasks.save(t);
        if (t.getOwner() != null) {
            notify.notifyUsers(List.of(t.getOwner()), "task", new Object[]{title, ""}, "/tasks", null);
        }
        flash.ok(ra, "msg.saved");
        return "redirect:/tasks?view=open";
    }

    private FollowUpTask load(Long id) {
        FollowUpTask t = tasks.findById(id).orElseThrow(() -> new NotFoundException("Task"));
        boolean mine = t.getOwner() != null && t.getOwner().getId().equals(currentUser.require().getId());
        if (!mine && t.getOffice() != null) {
            scope.check(t.getOffice());
        }
        return t;
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam TaskStatus status, @RequestParam(required = false) String resultNote,
                         @RequestParam(required = false) String back, RedirectAttributes ra) {
        FollowUpTask t = load(id);
        t.setStatus(status);
        if (resultNote != null && !resultNote.isBlank()) {
            t.setResultNote(resultNote);
        }
        t.setCompletedAt(status == TaskStatus.DONE ? LocalDateTime.now() : null);
        tasks.save(t);
        if (t.getRequest() != null) {
            audit.log(AuditService.REQUEST, t.getRequest().getId(), "TASK_" + status, t.getTitle());
        }
        flash.ok(ra, "msg.saved");
        return "redirect:" + (back != null && back.startsWith("/") ? back : "/tasks");
    }

    @PostMapping("/{id}/owner")
    public String owner(@PathVariable Long id, @RequestParam(required = false) Long ownerId, RedirectAttributes ra) {
        FollowUpTask t = load(id);
        t.setOwner(ownerId == null ? null : users.findById(ownerId).orElse(null));
        tasks.save(t);
        if (t.getOwner() != null) {
            notify.notifyUsers(List.of(t.getOwner()), "task", new Object[]{t.getTitle(),
                    t.getRequest() == null ? "" : t.getRequest().getReferenceNumber()}, "/tasks", t.getRequest());
        }
        flash.ok(ra, "msg.saved");
        return "redirect:/tasks?view=open";
    }
}
