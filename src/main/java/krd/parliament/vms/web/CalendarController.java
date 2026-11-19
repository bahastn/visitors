package krd.parliament.vms.web;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.BlockType;
import krd.parliament.vms.repo.CalendarBlockRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.*;
import krd.parliament.vms.util.Labels;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** The calendar page and its JSON endpoints (events, drag & drop, quick scheduling, blocked time). */
@Controller
public class CalendarController {

    private static final Set<VisitStatus> SHOWN = EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN,
            VisitStatus.IN_MEETING, VisitStatus.COMPLETED, VisitStatus.NO_SHOW);

    private final VisitRequestRepository requests;
    private final CalendarBlockRepository blocks;
    private final UserRepository users;
    private final VisitRequestService service;
    private final SchedulingService scheduling;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final OfficeScope scope;
    private final CalendarFeed feed;
    private final Labels labels;
    private final MessageSource messages;

    public CalendarController(VisitRequestRepository requests, CalendarBlockRepository blocks, UserRepository users,
                              VisitRequestService service, SchedulingService scheduling, AuditService audit,
                              CurrentUser currentUser, OfficeScope scope, CalendarFeed feed, Labels labels,
                              MessageSource messages) {
        this.requests = requests;
        this.blocks = blocks;
        this.users = users;
        this.service = service;
        this.scheduling = scheduling;
        this.audit = audit;
        this.currentUser = currentUser;
        this.scope = scope;
        this.feed = feed;
        this.labels = labels;
        this.messages = messages;
    }

    @GetMapping("/calendar")
    public String calendar(Model model) {
        AppUser me = currentUser.require();
        if (me.getCalendarToken() == null) {
            me.setCalendarToken(CryptoService.randomToken(24));
            users.save(me);
        }
        model.addAttribute("feedToken", me.getCalendarToken());
        model.addAttribute("approvedUnscheduled", requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.APPROVED), scope.ids()));
        model.addAttribute("canPlan", currentUser.is("SECRETARY", "DIRECTOR", "ADMIN", "HEAD"));
        model.addAttribute("defaultOffice", scope.defaultOffice());
        return "calendar";
    }

    /** Events for FullCalendar: visits and blocked time of the selected offices. */
    @GetMapping(value = "/calendar/events", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> events(@RequestParam String start, @RequestParam String end,
                                            @RequestParam(required = false) Long office,
                                            @RequestParam(defaultValue = "true") boolean showBlocks,
                                            @RequestParam(defaultValue = "true") boolean showDone) {
        LocalDateTime from = parse(start);
        LocalDateTime to = parse(end);
        Set<Long> ids = office != null ? Set.of(scope.require(office).getId()) : scope.ids();
        Set<VisitStatus> statuses = showDone ? SHOWN : EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING);
        List<Map<String, Object>> res = new ArrayList<>();
        for (VisitRequest r : requests.findScheduledBetween(from, to, statuses, ids)) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", "r" + r.getId());
            e.put("title", r.getVisitor().getShortName());
            e.put("start", r.getScheduledStart().toString());
            e.put("end", r.getScheduledEnd().toString());
            e.put("classNames", List.of("ev", "ev-" + r.getStatus().name().toLowerCase(), "pr-" + r.getPriority().name().toLowerCase()));
            e.put("editable", r.getStatus() == VisitStatus.SCHEDULED);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", "visit");
            p.put("requestId", r.getId());
            p.put("ref", r.getReferenceNumber());
            p.put("subject", r.getSubject());
            p.put("visitor", r.getVisitor().getFullName());
            p.put("phone", r.getVisitor().getPhone());
            p.put("organization", r.getVisitor().getOrganization() == null ? "" : r.getVisitor().getOrganization());
            p.put("office", r.getOffice().getName());
            p.put("officeColor", r.getOffice().getColor());
            p.put("status", labels.t(r.getStatus()));
            p.put("statusKey", r.getStatus().name());
            p.put("priority", labels.t(r.getPriority()));
            p.put("priorityKey", r.getPriority().name());
            p.put("location", r.getLocation() == null ? "" : r.getLocation());
            p.put("attendees", r.getExpectedAttendees());
            p.put("flag", r.isWatchlistFlag());
            e.put("extendedProps", p);
            res.add(e);
        }
        if (showBlocks) {
            for (CalendarBlock b : blocks.findOverlapping(from, to, ids)) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", "b" + b.getId());
                e.put("title", b.getTitle());
                e.put("start", b.getStartAt().toString());
                e.put("end", b.getEndAt().toString());
                e.put("allDay", b.isAllDay());
                e.put("classNames", List.of("ev", "ev-block"));
                e.put("editable", false);
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("type", "block");
                p.put("blockId", b.getId());
                p.put("blockType", labels.t(b.getType()));
                p.put("office", b.getOffice().getName());
                p.put("officeColor", b.getOffice().getColor());
                p.put("notes", b.getNotes() == null ? "" : b.getNotes());
                e.put("extendedProps", p);
                res.add(e);
            }
        }
        return res;
    }

    private ResponseEntity<Map<String, Object>> fail(BusinessException e) {
        return ResponseEntity.ok(Map.of("ok", false, "error",
                messages.getMessage(e.getMessage(), e.getArgs(), e.getMessage(), LocaleContextHolder.getLocale())));
    }

    /** Drag & drop: move or resize a scheduled visit. */
    @PostMapping(value = "/calendar/events/{requestId}/move", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> move(@PathVariable Long requestId, @RequestParam String start,
                                                    @RequestParam(required = false) String end,
                                                    @RequestParam(defaultValue = "false") boolean force) {
        try {
            LocalDateTime s = parse(start);
            VisitRequest r = service.get(requestId);
            int minutes = end != null && !end.isBlank() ? (int) Duration.between(s, parse(end)).toMinutes() : r.getDurationMinutes();
            service.schedule(requestId, s, minutes, null, null, force);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (BusinessException e) {
            return fail(e);
        }
    }

    /** Schedule an approved (or pending) request from the calendar: drop from the side list, or the "new" dialog. */
    @PostMapping(value = "/calendar/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> schedule(@RequestParam Long requestId, @RequestParam String start,
                                                        @RequestParam(defaultValue = "0") int duration,
                                                        @RequestParam(defaultValue = "false") boolean force) {
        try {
            service.schedule(requestId, parse(start), duration, null, null, force);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (BusinessException e) {
            return fail(e);
        }
    }

    @GetMapping(value = "/calendar/slots", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, String>> slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(defaultValue = "30") int duration,
                                           @RequestParam Long office) {
        List<Map<String, String>> res = new ArrayList<>();
        for (SchedulingService.Slot s : scheduling.freeSlots(scope.require(office), date, duration)) {
            res.add(Map.of("start", s.start().toString(), "label", s.start().toLocalTime() + " – " + s.end().toLocalTime()));
        }
        return res;
    }

    @PostMapping(value = "/calendar/blocks", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addBlock(@RequestParam Long officeId, @RequestParam String title,
                                                        @RequestParam BlockType type, @RequestParam String start,
                                                        @RequestParam String end,
                                                        @RequestParam(defaultValue = "false") boolean allDay,
                                                        @RequestParam(required = false) String notes) {
        if (!currentUser.is("SECRETARY", "DIRECTOR", "ADMIN", "HEAD")) {
            throw new AccessDeniedException("not allowed");
        }
        Office o = scope.require(officeId);
        LocalDateTime s = parse(start);
        LocalDateTime e = parse(end);
        if (title.isBlank() || !e.isAfter(s)) {
            return fail(new BusinessException("error.blockTimes"));
        }
        CalendarBlock b = new CalendarBlock();
        b.setOffice(o);
        b.setTitle(title.trim());
        b.setType(type);
        b.setStartAt(s);
        b.setEndAt(e);
        b.setAllDay(allDay);
        b.setNotes(notes);
        b.setCreatedBy(currentUser.get());
        blocks.save(b);
        audit.log(AuditService.SYSTEM, b.getId(), "BLOCK_CREATED", o.getCode() + " — " + b.getTitle() + " " + b.getStartAt());
        long clashes = scheduling.conflicts(o, s, e, null).stream().filter(c -> c.type().equals("VISIT")).count();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        if (clashes > 0) {
            res.put("warning", messages.getMessage("msg.blockClashes", new Object[]{clashes}, LocaleContextHolder.getLocale()));
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping(value = "/calendar/blocks/{id}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> deleteBlock(@PathVariable Long id) {
        if (!currentUser.is("SECRETARY", "DIRECTOR", "ADMIN", "HEAD")) {
            throw new AccessDeniedException("not allowed");
        }
        blocks.findById(id).ifPresent(b -> {
            scope.check(b.getOffice());
            audit.log(AuditService.SYSTEM, id, "BLOCK_DELETED", b.getTitle());
            blocks.delete(b);
        });
        return Map.of("ok", true);
    }

    /** Personal subscription feed for Google Calendar / Outlook (secret token in the URL). */
    @GetMapping(value = "/calendar/feed/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        AppUser u = users.findByCalendarToken(token).filter(AppUser::isEnabled).orElse(null);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        // No session here: work out the user's offices directly
        Set<Long> ids = new HashSet<>();
        for (Office o : scope.allOfficesFor(u)) {
            ids.add(o.getId());
        }
        if (ids.isEmpty()) {
            ids.add(-1L);
        }
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to = LocalDateTime.now().plusDays(120);
        List<VisitRequest> visits = requests.findScheduledBetween(from, to,
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING, VisitStatus.COMPLETED), ids);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(feed.build(visits, blocks.findOverlapping(from, to, ids), u.getFullName()));
    }

    static LocalDateTime parse(String s) {
        String t = s.trim().replace(' ', 'T');
        if (t.length() == 10) {
            return LocalDate.parse(t).atStartOfDay();
        }
        return LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t);
    }
}
