package krd.parliament.vms.web.api;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.*;
import krd.parliament.vms.repo.OfficeRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.repo.VisitorRepository;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.VisitRequestService;
import krd.parliament.vms.service.VisitorService;
import krd.parliament.vms.util.Phones;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 4: API for linking with other offices in parliament.
 * Other offices authenticate with their key in the X-API-KEY header (see Settings → Offices).
 *
 * POST /api/v1/referrals            create a visit request referred by another office
 * GET  /api/v1/referrals/{ref}      status of a request (for the referring office)
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final OfficeRepository offices;
    private final VisitRequestRepository requests;
    private final VisitorRepository visitorRepo;
    private final VisitorService visitors;
    private final VisitRequestService service;
    private final AuditService audit;

    public ApiController(OfficeRepository offices, VisitRequestRepository requests, VisitorRepository visitorRepo,
                         VisitorService visitors, VisitRequestService service, AuditService audit) {
        this.offices = offices;
        this.requests = requests;
        this.visitorRepo = visitorRepo;
        this.visitors = visitors;
        this.service = service;
        this.audit = audit;
    }

    @PostMapping("/referrals")
    @Transactional
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> receive(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                                       @RequestBody Map<String, Object> body) {
        Optional<Office> office = key == null ? Optional.empty() : offices.findByApiKeyAndActiveTrue(key);
        if (office.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid API key"));
        }
        // Target office: "office" code in the body, otherwise the office that owns the key
        Office target = office.get();
        if (body.get("office") != null) {
            target = offices.findByCodeIgnoreCase(String.valueOf(body.get("office"))).orElse(null);
        }
        if (target == null || target.isExternal() || !target.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown or external target office"));
        }
        Map<String, Object> v = (Map<String, Object>) body.getOrDefault("visitor", Map.of());
        String firstName = str(v.get("firstName"));
        String phone = Phones.normalize(str(v.get("phone")));
        String subject = str(body.get("subject"));
        if (firstName == null || phone == null || subject == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "visitor.firstName, visitor.phone and subject are required"));
        }
        Visitor visitor = visitorRepo.findByPhone(phone).stream()
                .filter(x -> x.getFirstName().equalsIgnoreCase(firstName)).findFirst().orElseGet(() -> {
                    Visitor nv = new Visitor();
                    nv.setFirstName(firstName);
                    nv.setFatherName(str(v.get("fatherName")));
                    nv.setGrandfatherName(str(v.get("grandfatherName")));
                    nv.setFamilyName(str(v.get("familyName")));
                    nv.setPhone(phone);
                    nv.setEmail(str(v.get("email")));
                    nv.setOrganization(str(v.get("organization")));
                    nv.setCity(str(v.get("city")));
                    nv.setGovernorate(enumOr(Governorate.class, v.get("governorate"), null));
                    nv.setPreferredLanguage(enumOr(Language.class, v.get("preferredLanguage"), Language.ckb));
                    return visitors.save(nv);
                });
        VisitRequest r = new VisitRequest();
        r.setChannel(RequestChannel.OTHER_OFFICE);
        if (!office.get().getId().equals(target.getId())) {
            r.setReferredFrom(office.get());
        }
        r.setExternalReference(str(body.get("sourceReference")));
        r.setSubject(subject);
        r.setDescription(str(body.get("description")));
        r.setPurpose(enumOr(VisitPurpose.class, body.get("purpose"), VisitPurpose.OTHER));
        r.setPriority(enumOr(Priority.class, body.get("priority"), Priority.NORMAL));
        r.setReferredByPerson(office.get().getNameCkb() + (body.get("note") != null ? ": " + body.get("note") : ""));
        r = service.create(r, visitor, target);
        audit.log(AuditService.REQUEST, r.getId(), "RECEIVED_FROM_OFFICE", office.get().getName());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reference", r.getReferenceNumber());
        res.put("status", r.getStatus().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/referrals/{ref}")
    public ResponseEntity<Map<String, Object>> status(@RequestHeader(value = "X-API-KEY", required = false) String key,
                                                      @PathVariable String ref) {
        Optional<Office> office = key == null ? Optional.empty() : offices.findByApiKeyAndActiveTrue(key);
        if (office.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid API key"));
        }
        return requests.findByReferenceNumberIgnoreCase(ref)
                .filter(r -> (r.getReferredFrom() != null && r.getReferredFrom().getId().equals(office.get().getId()))
                        || r.getOffice().getId().equals(office.get().getId()))
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reference", r.getReferenceNumber());
                    m.put("externalReference", r.getExternalReference());
                    m.put("status", r.getStatus().name());
                    m.put("office", r.getOffice().getCode());
                    m.put("scheduledStart", r.getScheduledStart() == null ? null : r.getScheduledStart().toString());
                    m.put("updatedAt", r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not found")));
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString().trim();
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, Object v, E def) {
        try {
            return v == null ? def : Enum.valueOf(type, v.toString().trim());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
