package krd.parliament.vms.web;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.*;
import krd.parliament.vms.repo.*;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/requests")
public class RequestController {

    static final String[] REQUEST_FIELDS = {"channel", "purpose", "subject", "description", "priority", "preferredDate",
            "preferredTime", "durationMinutes", "expectedAttendees", "needsInterpreter", "accessibilityNeeds",
            "vehiclePlate", "referredByPerson", "meetingWith", "location"};

    private final VisitRequestService service;
    private final VisitRequestRepository repo;
    private final VisitorService visitors;
    private final UserRepository users;
    private final OfficeRepository offices;
    private final ConstituentCaseRepository cases;
    private final FollowUpTaskRepository tasks;
    private final AttachmentRepository attachments;
    private final FeedbackRepository feedback;
    private final AuditService audit;
    private final SchedulingService scheduling;
    private final StorageService storage;
    private final QrService qr;
    private final AiSummaryService ai;
    private final NotificationService notify;
    private final SettingsService settings;
    private final CurrentUser currentUser;
    private final OfficeScope scope;
    private final CalendarFeed calendarFeed;
    private final Flash flash;

    public RequestController(VisitRequestService service, VisitRequestRepository repo, VisitorService visitors,
                             UserRepository users, OfficeRepository offices, ConstituentCaseRepository cases,
                             FollowUpTaskRepository tasks, AttachmentRepository attachments, FeedbackRepository feedback,
                             AuditService audit, SchedulingService scheduling, StorageService storage, QrService qr,
                             AiSummaryService ai, NotificationService notify, SettingsService settings,
                             CurrentUser currentUser, OfficeScope scope, CalendarFeed calendarFeed, Flash flash) {
        this.service = service;
        this.repo = repo;
        this.visitors = visitors;
        this.users = users;
        this.offices = offices;
        this.cases = cases;
        this.tasks = tasks;
        this.attachments = attachments;
        this.feedback = feedback;
        this.audit = audit;
        this.scheduling = scheduling;
        this.storage = storage;
        this.qr = qr;
        this.ai = ai;
        this.notify = notify;
        this.settings = settings;
        this.currentUser = currentUser;
        this.scope = scope;
        this.calendarFeed = calendarFeed;
        this.flash = flash;
    }

    @InitBinder("visitRequest")
    void bindRequest(WebDataBinder b) {
        b.setAllowedFields(REQUEST_FIELDS);
    }

    @InitBinder("member")
    void bindMember(WebDataBinder b) {
        b.setAllowedFields("fullName", "jobTitle", "organization", "phone", "email", "idType", "idNumber");
    }

    /** Staff of the request's office (for "managed by" and task owners). */
    private List<AppUser> staffOf(Office o) {
        return users.findWithAccess(EnumSet.of(Role.HEAD, Role.DIRECTOR, Role.SECRETARY, Role.RECEPTION), o);
    }

    // ------------------------------------------------------------------ list & search

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) VisitStatus status,
                       @RequestParam(required = false) Priority priority,
                       @RequestParam(required = false) VisitPurpose purpose,
                       @RequestParam(required = false) RequestChannel channel,
                       @RequestParam(required = false) Long managedBy,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Set<Long> ids = scope.ids();
        Specification<VisitRequest> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(root.get("office").get("id").in(ids));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                var v = root.join("visitor");
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("referenceNumber")), like),
                        cb.like(cb.lower(root.<String>get("subject")), like),
                        cb.like(cb.lower(v.<String>get("firstName")), like),
                        cb.like(cb.lower(cb.coalesce(v.<String>get("fatherName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(v.<String>get("familyName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(v.<String>get("organization"), "")), like),
                        cb.like(v.<String>get("phone"), like)));
            }
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (priority != null) p.add(cb.equal(root.get("priority"), priority));
            if (purpose != null) p.add(cb.equal(root.get("purpose"), purpose));
            if (channel != null) p.add(cb.equal(root.get("channel"), channel));
            if (managedBy != null) p.add(cb.equal(root.get("managedBy").get("id"), managedBy));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), to.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        model.addAttribute("page", repo.findAll(spec, PageRequest.of(page, 25, Sort.by(Sort.Direction.DESC, "createdAt"))));
        model.addAttribute("staff", users.findAllByOrderByFullNameAsc());
        model.addAttribute("f_q", q);
        model.addAttribute("f_status", status);
        model.addAttribute("f_priority", priority);
        model.addAttribute("f_purpose", purpose);
        model.addAttribute("f_channel", channel);
        model.addAttribute("f_managedBy", managedBy);
        model.addAttribute("f_from", from);
        model.addAttribute("f_to", to);
        return "requests/list";
    }

    // ------------------------------------------------------------------ create & edit

    private void formModel(Model model, Visitor v, Office office) {
        model.addAttribute("visitor", v);
        model.addAttribute("offices", scope.accessible());
        model.addAttribute("office", office);
        model.addAttribute("staff", office == null ? List.of() : staffOf(office));
        model.addAttribute("openCases", office == null ? List.of()
                : cases.findByVisitorAndStatusInAndOfficeIdIn(v, EnumSet.of(CaseStatus.OPEN, CaseStatus.IN_PROGRESS), scope.allIds()));
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION','HEAD')")
    public String newForm(@RequestParam(required = false) Long visitorId, @RequestParam(required = false) Long officeId,
                          Model model) {
        if (visitorId == null) {
            return "requests/pick-visitor";
        }
        Visitor v = visitors.get(visitorId);
        Office office = officeId != null ? scope.require(officeId) : scope.defaultOffice();
        VisitRequest r = new VisitRequest();
        if (office != null) {
            r.setDurationMinutes(office.getDefaultDuration());
            r.setLocation(office.getDefaultLocation());
        }
        if (currentUser.is("RECEPTION")) {
            r.setChannel(RequestChannel.WALK_IN);
        }
        model.addAttribute("visitRequest", r);
        formModel(model, v, office);
        return "requests/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION','HEAD')")
    public String create(@RequestParam Long visitorId, @RequestParam Long officeId,
                         @Valid @ModelAttribute("visitRequest") VisitRequest form, BindingResult br,
                         @RequestParam(required = false) Long managedById, @RequestParam(required = false) Long caseId,
                         @RequestParam(required = false) MultipartFile[] files, Model model,
                         RedirectAttributes ra) throws IOException {
        Visitor v = visitors.get(visitorId);
        Office office = scope.require(officeId);
        if (br.hasErrors()) {
            formModel(model, v, office);
            return "requests/form";
        }
        if (managedById != null) {
            form.setManagedBy(users.findById(managedById).orElse(null));
        }
        VisitRequest r = service.create(form, v, office);
        if (caseId != null) {
            service.linkCase(r.getId(), caseId);
        }
        if (files != null) {
            for (MultipartFile f : files) {
                service.addAttachment(r.getId(), f, null);
            }
        }
        flash.ok(ra, "msg.requestCreated", r.getReferenceNumber());
        return "redirect:/requests/" + r.getId();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION','HEAD')")
    public String edit(@PathVariable Long id, Model model) {
        VisitRequest r = service.get(id);
        model.addAttribute("visitRequest", r);
        formModel(model, r.getVisitor(), r.getOffice());
        model.addAttribute("openCases", List.of());
        return "requests/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION','HEAD')")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("visitRequest") VisitRequest form, BindingResult br,
                         @RequestParam(required = false) Long managedById, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            VisitRequest r = service.get(id);
            form.setId(id);
            formModel(model, r.getVisitor(), r.getOffice());
            model.addAttribute("openCases", List.of());
            return "requests/form";
        }
        form.setManagedBy(managedById == null ? null : users.findById(managedById).orElse(null));
        service.updateDetails(id, form);
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id;
    }

    // ------------------------------------------------------------------ detail page

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        VisitRequest r = service.get(id);
        Office o = r.getOffice();
        model.addAttribute("r", r);
        model.addAttribute("trail", audit.trail(AuditService.REQUEST, id));
        model.addAttribute("staff", staffOf(o));
        model.addAttribute("referTargets", offices.findByActiveTrueOrderBySortOrderAscNameCkbAsc().stream()
                .filter(x -> !x.getId().equals(o.getId())).toList());
        model.addAttribute("visitorCases", cases.findByVisitorAndOfficeIdInOrderByCreatedAtDesc(r.getVisitor(), Set.of(o.getId())));
        model.addAttribute("previousVisits", repo.findByVisitorOrderByCreatedAtDesc(r.getVisitor()).stream()
                .filter(x -> !x.getId().equals(id) && scope.canAccess(x.getOffice())).toList());
        model.addAttribute("feedback", feedback.findByRequest(r).orElse(null));
        model.addAttribute("member", new VisitMember());
        model.addAttribute("aiConfigured", ai.isAiConfigured());
        model.addAttribute("canDecide", service.canDecide(o));
        int duration = r.getDurationMinutes() > 0 ? r.getDurationMinutes() : o.getDefaultDuration();
        if (r.getStatus().isPending() || r.getStatus() == VisitStatus.APPROVED || r.getStatus() == VisitStatus.SCHEDULED) {
            model.addAttribute("suggestedSlots", scheduling.nextFreeSlots(o, duration, 6));
        } else {
            model.addAttribute("suggestedSlots", List.of());
        }
        if (r.getQrToken() != null) {
            model.addAttribute("qrImage", qr.dataUri(notify.passLink(r), 220));
            model.addAttribute("passLink", notify.passLink(r));
        }
        model.addAttribute("trackLink", notify.trackLink(r));
        return "requests/view";
    }

    // ------------------------------------------------------------------ workflow actions

    private String backOr(String back, Long id) {
        return "redirect:" + (back != null && back.startsWith("/") ? back : "/requests/" + id);
    }

    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id, RedirectAttributes ra) {
        service.startReview(id);
        flash.ok(ra, "msg.statusChanged");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/recommend")
    public String recommend(@PathVariable Long id, @RequestParam Recommendation recommendation,
                            @RequestParam(required = false) String note, RedirectAttributes ra) {
        service.recommend(id, recommendation, note);
        flash.ok(ra, "msg.recommended");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String reason,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduleAt,
                          @RequestParam(defaultValue = "false") boolean force,
                          @RequestParam(required = false) String back, RedirectAttributes ra) {
        service.approve(id, reason, scheduleAt, force);
        flash.ok(ra, scheduleAt != null ? "msg.scheduled" : "msg.approved");
        return backOr(back, id);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam String reason,
                         @RequestParam(defaultValue = "false") boolean shareReason,
                         @RequestParam(required = false) String back, RedirectAttributes ra) {
        service.reject(id, reason, shareReason);
        flash.ok(ra, "msg.rejected");
        return backOr(back, id);
    }

    @PostMapping("/{id}/postpone")
    public String postpone(@PathVariable Long id,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until,
                           @RequestParam(required = false) String reason, RedirectAttributes ra) {
        service.postpone(id, until, reason);
        flash.ok(ra, "msg.statusChanged");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/refer")
    public String refer(@PathVariable Long id, @RequestParam Long officeId, @RequestParam(required = false) String note,
                        RedirectAttributes ra) {
        service.refer(id, officeId, note);
        flash.ok(ra, "msg.referred");
        // After a transfer the user may no longer have access to the request
        VisitRequest r = repo.findById(id).orElseThrow();
        return scope.canAccess(r.getOffice()) ? "redirect:/requests/" + id : "redirect:/waiting-list";
    }

    @PostMapping("/{id}/schedule")
    public String schedule(@PathVariable Long id,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                           @RequestParam(defaultValue = "0") int duration,
                           @RequestParam(required = false) String location,
                           @RequestParam(required = false) String meetingWith,
                           @RequestParam(defaultValue = "false") boolean force, RedirectAttributes ra) {
        VisitRequest r = service.get(id);
        if (!scheduling.withinWorkingHours(r.getOffice(), start, start.plusMinutes(Math.max(duration, 15)))) {
            flash.warn(ra, "msg.outsideHours");
        }
        service.schedule(id, start, duration, location, meetingWith, force);
        flash.ok(ra, "msg.scheduled");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason, RedirectAttributes ra) {
        service.cancel(id, reason);
        flash.ok(ra, "msg.statusChanged");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/checkin")
    public String checkIn(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean override,
                          @RequestParam(required = false) String back, RedirectAttributes ra) {
        VisitRequest r = service.checkIn(id, override);
        flash.ok(ra, "msg.checkedIn", r.getQueueNumber());
        if (r.isWatchlistFlag()) {
            flash.warn(ra, "msg.watchlistHit");
        }
        return backOr(back, id);
    }

    @PostMapping("/{id}/call")
    public String callIn(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
        service.callIn(id);
        flash.ok(ra, "msg.statusChanged");
        return backOr(back, id);
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, @RequestParam(required = false) String outcome,
                           @RequestParam(required = false) String back, RedirectAttributes ra) {
        service.complete(id, outcome, settings.getBool(SettingsService.FEEDBACK_ENABLED));
        flash.ok(ra, "msg.completed");
        return backOr(back, id);
    }

    @PostMapping("/{id}/noshow")
    public String noShow(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes ra) {
        service.noShow(id);
        flash.ok(ra, "msg.statusChanged");
        return backOr(back, id);
    }

    @PostMapping("/{id}/outcome")
    public String outcome(@PathVariable Long id, @RequestParam String outcome, RedirectAttributes ra) {
        service.saveOutcome(id, outcome);
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id + "?tab=outcome";
    }

    @PostMapping("/bulk")
    public String bulk(@RequestParam(name = "ids", required = false) List<Long> ids, @RequestParam String action,
                       @RequestParam(required = false) String reason, @RequestParam(required = false) String back,
                       RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            flash.error(ra, "error.noneSelected");
        } else if ("approve".equals(action)) {
            flash.ok(ra, "msg.bulkDone", service.bulkApprove(ids));
        } else if ("reject".equals(action)) {
            if (reason == null || reason.isBlank()) {
                flash.error(ra, "error.reasonRequired");
            } else {
                flash.ok(ra, "msg.bulkDone", service.bulkReject(ids, reason));
            }
        }
        return "redirect:" + (back != null && back.startsWith("/") ? back : "/waiting-list");
    }

    // ------------------------------------------------------------------ assignment, notes, members, files

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION','HEAD')")
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long userId, RedirectAttributes ra) {
        service.assignManager(id, userId);
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/notes")
    public String note(@PathVariable Long id, @RequestParam String text, RedirectAttributes ra) {
        service.addNote(id, text);
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id + "?tab=notes";
    }

    @PostMapping("/{id}/members")
    public String addMember(@PathVariable Long id, @Valid @ModelAttribute("member") VisitMember member, BindingResult br,
                            RedirectAttributes ra) {
        if (br.hasErrors()) {
            flash.error(ra, "error.memberName");
        } else {
            service.addMember(id, member);
            flash.ok(ra, "msg.saved");
        }
        return "redirect:/requests/" + id + "?tab=people";
    }

    @PostMapping("/{id}/members/{memberId}/delete")
    public String removeMember(@PathVariable Long id, @PathVariable Long memberId, RedirectAttributes ra) {
        service.removeMember(id, memberId);
        flash.ok(ra, "msg.deleted");
        return "redirect:/requests/" + id + "?tab=people";
    }

    @PostMapping("/{id}/attachments")
    public String upload(@PathVariable Long id, @RequestParam MultipartFile file,
                         @RequestParam(required = false) String description, RedirectAttributes ra) throws IOException {
        service.get(id);
        service.addAttachment(id, file, description);
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id + "?tab=files";
    }

    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> new NotFoundException("Attachment"));
        scope.check(a.getRequest().getOffice());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType() == null ? "application/octet-stream" : a.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(a.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(storage.resolve(a.getStoragePath())));
    }

    @PostMapping("/{id}/attachments/{attachmentId}/delete")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY')")
    public String deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId, RedirectAttributes ra) {
        service.deleteAttachment(id, attachmentId);
        flash.ok(ra, "msg.deleted");
        return "redirect:/requests/" + id + "?tab=files";
    }

    // ------------------------------------------------------------------ cases, tasks, AI summary

    @PostMapping("/{id}/case")
    public String linkCase(@PathVariable Long id, @RequestParam(required = false) Long caseId, RedirectAttributes ra) {
        service.linkCase(id, caseId);
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id;
    }

    @PostMapping("/{id}/case/new")
    public String newCase(@PathVariable Long id, RedirectAttributes ra) {
        VisitRequest r = service.get(id);
        ConstituentCase c = new ConstituentCase();
        c.setTitle(r.getSubject());
        c.setDescription(r.getDescription());
        c.setVisitor(r.getVisitor());
        c.setOffice(r.getOffice());
        c.setAssignedTo(r.getManagedBy() != null ? r.getManagedBy() : currentUser.get());
        c.setCaseNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        c = cases.save(c);
        c.setCaseNumber(String.format("CASE-%d-%04d", LocalDate.now().getYear(), c.getId()));
        cases.save(c);
        audit.log(AuditService.CASE, c.getId(), "CASE_CREATED", c.getTitle());
        service.linkCase(id, c.getId());
        flash.ok(ra, "msg.caseCreated", c.getCaseNumber());
        return "redirect:/cases/" + c.getId();
    }

    @PostMapping("/{id}/tasks")
    public String addTask(@PathVariable Long id, @RequestParam String title, @RequestParam(required = false) Long ownerId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
                          @RequestParam(required = false) String description, RedirectAttributes ra) {
        VisitRequest r = service.get(id);
        FollowUpTask t = new FollowUpTask();
        t.setRequest(r);
        t.setOffice(r.getOffice());
        t.setConstituentCase(r.getConstituentCase());
        t.setTitle(title);
        t.setDescription(description);
        t.setDueDate(dueDate);
        t.setCreatedBy(currentUser.get());
        AppUser owner = ownerId == null ? null : users.findById(ownerId).orElse(null);
        t.setOwner(owner);
        tasks.save(t);
        audit.log(AuditService.REQUEST, id, "TASK_CREATED", title + (owner == null ? "" : " → " + owner.getFullName()));
        if (owner != null) {
            notify.notifyUsers(List.of(owner), "task", new Object[]{title, r.getReferenceNumber()}, "/tasks", r);
        }
        flash.ok(ra, "msg.saved");
        return "redirect:/requests/" + id + "?tab=tasks";
    }

    @PostMapping("/{id}/ai-summary")
    public String aiSummary(@PathVariable Long id, RedirectAttributes ra) {
        VisitRequest r = service.get(id);
        service.saveAiSummary(id, ai.summarize(r));
        flash.ok(ra, "msg.summaryCreated");
        return "redirect:/requests/" + id + "?tab=outcome";
    }

    // ------------------------------------------------------------------ pass, badge, calendar file

    @GetMapping("/{id}/badge")
    public String badge(@PathVariable Long id, Model model) {
        VisitRequest r = service.get(id);
        if (r.getQrToken() == null) {
            throw new BusinessException("error.noPass");
        }
        model.addAttribute("r", r);
        model.addAttribute("qrImage", qr.dataUri(notify.passLink(r), 260));
        return "requests/badge";
    }

    @GetMapping(value = "/{id}/event.ics", produces = "text/calendar")
    public ResponseEntity<String> ics(@PathVariable Long id) {
        VisitRequest r = service.get(id);
        if (r.getScheduledStart() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + r.getReferenceNumber() + ".ics")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(calendarFeed.build(List.of(r), List.of(), r.getReferenceNumber()));
    }

    @ModelAttribute("backUrl")
    String backUrl(HttpServletRequest req) {
        return GlobalAdvice.back(req);
    }
}
