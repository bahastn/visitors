package krd.parliament.vms.web;

import jakarta.validation.Valid;
import krd.parliament.vms.domain.AuditLog;
import krd.parliament.vms.domain.Visitor;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.repo.ConstituentCaseRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.StorageService;
import krd.parliament.vms.service.VisitorService;
import krd.parliament.vms.service.WatchlistService;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Controller
@RequestMapping("/visitors")
public class VisitorController {

    static final String[] VISITOR_FIELDS = {"title", "firstName", "fatherName", "grandfatherName", "familyName", "nameLatin",
            "gender", "dateOfBirth", "nationality", "idType", "idNumber", "idIssuePlace", "idExpiryDate", "phone",
            "phoneSecondary", "whatsappNumber", "email", "preferredContactChannel", "preferredLanguage",
            "consentToMessages", "jobTitle", "organization", "organizationType", "governorate", "city", "district",
            "address", "emergencyContactName", "emergencyContactPhone", "vip", "notes"};

    private final VisitorService visitors;
    private final VisitRequestRepository requests;
    private final ConstituentCaseRepository cases;
    private final AuditService audit;
    private final WatchlistService watchlist;
    private final StorageService storage;
    private final Flash flash;
    private final OfficeScope scope;

    public VisitorController(VisitorService visitors, VisitRequestRepository requests, ConstituentCaseRepository cases,
                             AuditService audit, WatchlistService watchlist, StorageService storage, Flash flash,
                             OfficeScope scope) {
        this.scope = scope;
        this.visitors = visitors;
        this.requests = requests;
        this.cases = cases;
        this.audit = audit;
        this.watchlist = watchlist;
        this.storage = storage;
        this.flash = flash;
    }

    @InitBinder("visitor")
    void bind(WebDataBinder b) {
        b.setAllowedFields(VISITOR_FIELDS);
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", visitors.search(q, PageRequest.of(page, 25)));
        model.addAttribute("q", q);
        return "visitors/list";
    }

    /** JSON search used by the "new request" page to pick an existing visitor. */
    @GetMapping(value = "/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> lookup(@RequestParam String q) {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Visitor v : visitors.search(q, PageRequest.of(0, 10))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("name", v.getFullName());
            m.put("phone", v.getPhone());
            m.put("organization", v.getOrganization());
            res.add(m);
        }
        return res;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION')")
    public String newForm(Model model) {
        model.addAttribute("visitor", new Visitor());
        return "visitors/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION')")
    public String create(@Valid @ModelAttribute("visitor") Visitor visitor, BindingResult br,
                         @RequestParam(required = false) String next,
                         @RequestParam(defaultValue = "false") boolean ignoreDuplicates,
                         Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "visitors/form";
        }
        if (!ignoreDuplicates) {
            List<Visitor> dups = visitors.findDuplicates(visitor);
            if (!dups.isEmpty()) {
                model.addAttribute("duplicates", dups);
                model.addAttribute("next", next);
                return "visitors/form";
            }
        }
        Visitor saved = visitors.save(visitor);
        flash.ok(ra, "msg.saved");
        if (!watchlist.match(saved).isEmpty()) {
            flash.warn(ra, "msg.watchlistHit");
        }
        if (next != null && next.contains("request")) {
            return "redirect:/requests/new?visitorId=" + saved.getId();
        }
        return "redirect:/visitors/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Visitor v = visitors.get(id);
        // Visits to offices the user cannot access are not shown
        List<VisitRequest> history = requests.findByVisitorOrderByCreatedAtDesc(v).stream()
                .filter(r -> scope.canAccess(r.getOffice())).toList();
        // Combined timeline: visitor events and all events of the visitor's requests
        List<AuditLog> timeline = new ArrayList<>(audit.trail(AuditService.VISITOR, id));
        Map<Long, String> refs = new HashMap<>();
        for (VisitRequest r : history) {
            refs.put(r.getId(), r.getReferenceNumber());
            timeline.addAll(audit.trail(AuditService.REQUEST, r.getId()).stream()
                    .filter(a -> a.getToStatus() != null).toList());
        }
        timeline.sort(Comparator.comparing(AuditLog::getTimestamp).reversed());
        model.addAttribute("visitor", v);
        model.addAttribute("history", history);
        model.addAttribute("cases", cases.findByVisitorAndOfficeIdInOrderByCreatedAtDesc(v, scope.allIds()));
        model.addAttribute("timeline", timeline);
        model.addAttribute("refs", refs);
        model.addAttribute("watchHits", watchlist.match(v));
        model.addAttribute("duplicates", visitors.findDuplicates(v));
        return "visitors/view";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION')")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("visitor", visitors.get(id));
        return "visitors/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION')")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("visitor") Visitor form, BindingResult br,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            form.setId(id);
            return "visitors/form";
        }
        Visitor v = visitors.get(id);
        BeanUtils.copyProperties(form, v, "id", "createdAt", "updatedAt", "createdBy", "idNumberHash", "photoPath", "idNumber");
        // The ID number is never sent back to the browser; an empty field keeps the stored value.
        if (form.getIdNumber() != null && !form.getIdNumber().isBlank()) {
            v.setIdNumber(form.getIdNumber());
        }
        visitors.save(v);
        flash.ok(ra, "msg.saved");
        return "redirect:/visitors/" + id;
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY','RECEPTION','SECURITY')")
    public String photo(@PathVariable Long id, @RequestParam MultipartFile file, RedirectAttributes ra) throws IOException {
        Visitor v = visitors.get(id);
        String ct = file.getContentType() == null ? "" : file.getContentType();
        if (!ct.startsWith("image/")) {
            flash.error(ra, "error.fileType", ct);
            return "redirect:/visitors/" + id;
        }
        if (v.getPhotoPath() != null) {
            storage.delete(v.getPhotoPath());
        }
        v.setPhotoPath(storage.store(file));
        visitors.save(v);
        flash.ok(ra, "msg.saved");
        return "redirect:/visitors/" + id;
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable Long id) throws IOException {
        Visitor v = visitors.get(id);
        if (v.getPhotoPath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path p = storage.resolve(v.getPhotoPath());
        String ct = Files.probeContentType(p);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct == null ? "image/jpeg" : ct))
                .body(new FileSystemResource(p));
    }
}
