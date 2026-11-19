package krd.parliament.vms.web;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Enums.OfficeType;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.OfficeRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.NotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * Offices of the parliament (and external bodies for referrals).
 * The administrator manages all offices; a director can edit the rules of his own office.
 */
@Controller
@RequestMapping("/offices")
public class OfficeController {

    private final OfficeRepository offices;
    private final UserRepository users;
    private final VisitRequestRepository requests;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final Flash flash;

    public OfficeController(OfficeRepository offices, UserRepository users, VisitRequestRepository requests,
                            CurrentUser currentUser, AuditService audit, Flash flash) {
        this.offices = offices;
        this.users = users;
        this.requests = requests;
        this.currentUser = currentUser;
        this.audit = audit;
        this.flash = flash;
    }

    private boolean isAdmin() {
        return currentUser.role() == Role.ADMIN;
    }

    private Office editable(Long id) {
        Office o = offices.findById(id).orElseThrow(() -> new NotFoundException("Office"));
        AppUser me = currentUser.require();
        if (!isAdmin() && !me.canAccess(o)) {
            throw new AccessDeniedException("Not your office");
        }
        return o;
    }

    @GetMapping
    public String list(Model model) {
        AppUser me = currentUser.require();
        List<Office> list = offices.findAllByOrderBySortOrderAscNameCkbAsc().stream()
                .filter(o -> isAdmin() || me.canAccess(o)).toList();
        Map<Long, Long> pending = new HashMap<>();
        Map<Long, Integer> staff = new HashMap<>();
        for (Office o : list) {
            pending.put(o.getId(), requests.countByStatusInAndOfficeIdIn(VisitStatus.pendingStatuses(), Set.of(o.getId())));
            staff.put(o.getId(), users.findOfficeStaff(EnumSet.allOf(Role.class), o).size());
        }
        model.addAttribute("offices", list);
        model.addAttribute("pending", pending);
        model.addAttribute("staffCount", staff);
        model.addAttribute("isAdmin", isAdmin());
        return "offices/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!isAdmin()) {
            throw new AccessDeniedException("admin only");
        }
        model.addAttribute("o", new Office());
        model.addAttribute("parents", offices.findAllByOrderBySortOrderAscNameCkbAsc());
        model.addAttribute("isAdmin", true);
        return "offices/form";
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Office o = editable(id);
        model.addAttribute("o", o);
        model.addAttribute("parents", offices.findAllByOrderBySortOrderAscNameCkbAsc().stream()
                .filter(x -> !x.getId().equals(id)).toList());
        model.addAttribute("staff", users.findOfficeStaff(EnumSet.allOf(Role.class), o));
        model.addAttribute("isAdmin", isAdmin());
        return "offices/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam String nameCkb, @RequestParam(required = false) String nameAr,
                       @RequestParam(required = false) String nameEn, @RequestParam(required = false) String code,
                       @RequestParam(required = false) OfficeType type, @RequestParam(required = false) Long parentId,
                       @RequestParam(required = false) String hostTitleCkb, @RequestParam(required = false) String hostTitleAr,
                       @RequestParam(required = false) String hostTitleEn, @RequestParam(required = false) String hostName,
                       @RequestParam(required = false) String defaultLocation, @RequestParam(defaultValue = "#05668d") String color,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime workStart,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime workEnd,
                       @RequestParam Map<String, String> all,
                       @RequestParam(defaultValue = "2") int approvalLevels, @RequestParam(defaultValue = "7") int overdueDays,
                       @RequestParam(defaultValue = "30") int defaultDuration,
                       @RequestParam(defaultValue = "false") boolean acceptsPublicRequests,
                       @RequestParam(defaultValue = "false") boolean external,
                       @RequestParam(defaultValue = "false") boolean active,
                       @RequestParam(defaultValue = "100") int sortOrder,
                       @RequestParam(required = false) String contactPerson, @RequestParam(required = false) String contactEmail,
                       @RequestParam(required = false) String contactPhone, @RequestParam(required = false) String endpointUrl,
                       RedirectAttributes ra) {
        Office o;
        if (id == null) {
            if (!isAdmin()) {
                throw new AccessDeniedException("admin only");
            }
            o = new Office();
        } else {
            o = editable(id);
        }
        if (nameCkb == null || nameCkb.isBlank()) {
            flash.error(ra, "error.required");
            return "redirect:" + (id == null ? "/offices/new" : "/offices/" + id);
        }
        // Identity fields only by the administrator
        if (isAdmin()) {
            String c = code == null ? "" : code.trim().toUpperCase();
            if (!c.matches("[A-Z0-9]{2,12}")) {
                flash.error(ra, "error.officeCode");
                return "redirect:" + (id == null ? "/offices/new" : "/offices/" + id);
            }
            Optional<Office> same = offices.findByCodeIgnoreCase(c);
            if (same.isPresent() && !same.get().getId().equals(id)) {
                flash.error(ra, "error.officeCodeTaken");
                return "redirect:" + (id == null ? "/offices/new" : "/offices/" + id);
            }
            o.setCode(c);
            o.setType(type == null ? OfficeType.DEPARTMENT : type);
            o.setParent(parentId == null ? null : offices.findById(parentId).orElse(null));
            o.setExternal(external);
            o.setActive(id == null || active);
            o.setSortOrder(sortOrder);
            o.setEndpointUrl(blank(endpointUrl));
        }
        o.setNameCkb(nameCkb.trim());
        o.setNameAr(blank(nameAr));
        o.setNameEn(blank(nameEn));
        o.setHostTitleCkb(blank(hostTitleCkb));
        o.setHostTitleAr(blank(hostTitleAr));
        o.setHostTitleEn(blank(hostTitleEn));
        o.setHostName(blank(hostName));
        o.setDefaultLocation(blank(defaultLocation));
        o.setColor(color.matches("#[0-9a-fA-F]{6}") ? color : "#05668d");
        if (workStart != null) o.setWorkStart(workStart);
        if (workEnd != null) o.setWorkEnd(workEnd);
        StringBuilder days = new StringBuilder();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (all.containsKey("day_" + d.name())) {
                days.append(days.isEmpty() ? "" : ",").append(d.name());
            }
        }
        o.setWorkDays(days.toString());
        o.setApprovalLevels(approvalLevels == 1 ? 1 : 2);
        o.setOverdueDays(Math.max(1, overdueDays));
        o.setDefaultDuration(Math.max(5, defaultDuration));
        o.setAcceptsPublicRequests(acceptsPublicRequests);
        o.setContactPerson(blank(contactPerson));
        o.setContactEmail(blank(contactEmail));
        o.setContactPhone(blank(contactPhone));
        offices.save(o);
        audit.log(AuditService.SYSTEM, o.getId(), "OFFICE_SAVED", o.getCode() + " — " + o.getNameCkb());
        flash.ok(ra, "msg.saved");
        return "redirect:/offices";
    }

    @PostMapping("/{id}/api-key")
    public String newKey(@PathVariable Long id, RedirectAttributes ra) {
        if (!isAdmin()) {
            throw new AccessDeniedException("admin only");
        }
        Office o = editable(id);
        o.setApiKey(CryptoService.randomToken(24));
        offices.save(o);
        audit.log(AuditService.SYSTEM, id, "OFFICE_KEY_ROTATED", o.getCode());
        flash.ok(ra, "msg.keyCreated", o.getApiKey());
        return "redirect:/offices/" + id;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
