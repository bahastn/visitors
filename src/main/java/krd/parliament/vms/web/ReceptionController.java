package krd.parliament.vms.web;

import krd.parliament.vms.domain.VisitMember;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.domain.WatchlistEntry;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.BusinessException;
import krd.parliament.vms.service.VisitRequestService;
import krd.parliament.vms.service.WatchlistService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/** Front desk: expected visitors, QR scanning, check-in and badges. Works offline (see reception.js). */
@Controller
public class ReceptionController {

    private final VisitRequestRepository requests;
    private final VisitRequestService service;
    private final WatchlistService watchlist;
    private final MessageSource messages;
    private final Flash flash;
    private final OfficeScope scope;

    public ReceptionController(VisitRequestRepository requests, VisitRequestService service, WatchlistService watchlist,
                               MessageSource messages, Flash flash, OfficeScope scope) {
        this.scope = scope;
        this.requests = requests;
        this.service = service;
        this.watchlist = watchlist;
        this.messages = messages;
        this.flash = flash;
    }

    @GetMapping("/reception")
    public String reception(Model model) {
        LocalDate today = LocalDate.now();
        List<VisitRequest> day = requests.findForDay(today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING, VisitStatus.COMPLETED, VisitStatus.NO_SHOW), scope.ids());
        model.addAttribute("expected", day.stream().filter(r -> r.getStatus() == VisitStatus.SCHEDULED).toList());
        model.addAttribute("inside", day.stream().filter(r -> r.getStatus() == VisitStatus.CHECKED_IN || r.getStatus() == VisitStatus.IN_MEETING).toList());
        model.addAttribute("done", day.stream().filter(r -> r.getStatus() == VisitStatus.COMPLETED || r.getStatus() == VisitStatus.NO_SHOW).toList());
        model.addAttribute("approvedWalkIns", requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.APPROVED), scope.ids()));
        return "reception/index";
    }

    @PostMapping("/reception/scan")
    public String scan(@RequestParam String code, RedirectAttributes ra) {
        try {
            VisitRequest r = service.findByQr(code);
            return "redirect:/reception/visit/" + r.getId();
        } catch (BusinessException e) {
            flash.error(ra, e.getMessage());
            return "redirect:/reception";
        }
    }

    @GetMapping("/reception/visit/{id}")
    public String visit(@PathVariable Long id, Model model) {
        VisitRequest r = service.get(id);
        List<WatchlistEntry> hits = new ArrayList<>(watchlist.match(r.getVisitor()));
        for (VisitMember m : r.getMembers()) {
            hits.addAll(watchlist.match(m));
        }
        model.addAttribute("r", r);
        model.addAttribute("hits", hits);
        model.addAttribute("blocked", watchlist.isBlocked(hits));
        model.addAttribute("isToday", r.getScheduledStart() != null && r.getScheduledStart().toLocalDate().equals(LocalDate.now()));
        return "reception/visit";
    }

    /** Today's list for the offline cache of the reception page. */
    @GetMapping(value = "/api/reception/today", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> today() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> res = new ArrayList<>();
        for (VisitRequest r : requests.findForDay(today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING), scope.ids())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("ref", r.getReferenceNumber());
            m.put("name", r.getVisitor().getFullName());
            m.put("phone", r.getVisitor().getPhone());
            m.put("time", r.getScheduledStart() == null ? "" : r.getScheduledStart().toLocalTime().toString());
            m.put("qr", r.getQrToken());
            m.put("status", r.getStatus().name());
            m.put("office", r.getOffice().getName());
            m.put("flag", r.isWatchlistFlag());
            res.add(m);
        }
        return res;
    }

    /** Check-ins recorded while the network was down are sent here when the connection returns. */
    @PostMapping(value = "/api/reception/sync", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> sync(@RequestBody List<Map<String, Object>> items) {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Long id = Long.valueOf(String.valueOf(it.get("id")));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            try {
                VisitRequest r = service.checkIn(id, false);
                m.put("ok", true);
                m.put("queue", r.getQueueNumber());
            } catch (BusinessException e) {
                m.put("ok", false);
                m.put("error", messages.getMessage(e.getMessage(), e.getArgs(), e.getMessage(), LocaleContextHolder.getLocale()));
            }
            res.add(m);
        }
        return res;
    }
}
