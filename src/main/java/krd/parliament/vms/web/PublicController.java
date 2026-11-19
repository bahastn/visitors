package krd.parliament.vms.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.*;
import krd.parliament.vms.repo.FeedbackRepository;
import krd.parliament.vms.repo.OfficeRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.repo.VisitorRepository;
import krd.parliament.vms.service.*;
import krd.parliament.vms.util.Phones;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pages that need no login: online request form, citizen tracking portal, visitor pass,
 * feedback survey and the lobby display screen.
 */
@Controller
@RequestMapping("/public")
public class PublicController {

    private static final String[] PUBLIC_VISITOR_FIELDS = {"title", "firstName", "fatherName", "grandfatherName", "familyName",
            "gender", "dateOfBirth", "idType", "idNumber", "phone", "whatsappNumber", "email", "preferredContactChannel",
            "preferredLanguage", "jobTitle", "organization", "organizationType", "governorate", "city", "district", "address"};

    private final VisitRequestRepository requests;
    private final VisitorRepository visitorRepo;
    private final VisitorService visitors;
    private final VisitRequestService service;
    private final FeedbackRepository feedbacks;
    private final AuditService audit;
    private final QrService qr;
    private final NotificationService notify;
    private final SettingsService settings;
    private final Map<String, List<Long>> rate = new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    private OfficeRepository officeRepo;

    private List<Office> publicOffices() {
        return officeRepo.findByActiveTrueAndExternalFalseAndAcceptsPublicRequestsTrueOrderBySortOrderAscNameCkbAsc();
    }

    public PublicController(VisitRequestRepository requests, VisitorRepository visitorRepo, VisitorService visitors,
                            VisitRequestService service, FeedbackRepository feedbacks, AuditService audit, QrService qr,
                            NotificationService notify, SettingsService settings) {
        this.requests = requests;
        this.visitorRepo = visitorRepo;
        this.visitors = visitors;
        this.service = service;
        this.feedbacks = feedbacks;
        this.audit = audit;
        this.qr = qr;
        this.notify = notify;
        this.settings = settings;
    }

    @InitBinder("visitor")
    void bind(WebDataBinder b) {
        b.setAllowedFields(PUBLIC_VISITOR_FIELDS);
    }

    // ---------------------------------------------------------------- online request form

    @GetMapping("/request")
    public String form(Model model, HttpSession session) {
        if (!settings.getBool(SettingsService.PUBLIC_FORM_ENABLED)) {
            return "public/closed";
        }
        model.addAttribute("visitor", new Visitor());
        model.addAttribute("offices", publicOffices());
        captcha(model, session);
        return "public/request";
    }

    @PostMapping("/request")
    public String submit(@Valid @ModelAttribute("visitor") Visitor visitor, BindingResult br,
                         @RequestParam(required = false) Long officeId,
                         @RequestParam VisitPurpose purpose, @RequestParam String subject,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate preferredDate,
                         @RequestParam(required = false) String preferredTime,
                         @RequestParam(defaultValue = "1") int expectedAttendees,
                         @RequestParam(required = false) MultipartFile file,
                         @RequestParam(required = false) String website, // honeypot: must stay empty
                         @RequestParam(required = false) String captcha,
                         HttpSession session, HttpServletRequest http, Model model) throws IOException {
        if (!settings.getBool(SettingsService.PUBLIC_FORM_ENABLED)) {
            return "public/closed";
        }
        Integer expected = (Integer) session.getAttribute("captcha");
        boolean captchaOk = expected != null && captcha != null && captcha.trim().equals(String.valueOf(expected));
        Office office = officeId == null ? null : publicOffices().stream()
                .filter(o -> o.getId().equals(officeId)).findFirst().orElse(null);
        if (br.hasErrors() || subject == null || subject.isBlank() || !captchaOk || office == null) {
            if (!captchaOk) {
                model.addAttribute("captchaError", true);
            }
            if (office == null) {
                model.addAttribute("officeError", true);
            }
            model.addAttribute("offices", publicOffices());
            model.addAttribute("f_office", officeId);
            model.addAttribute("f_purpose", purpose);
            model.addAttribute("f_subject", subject);
            model.addAttribute("f_description", description);
            captcha(model, session);
            return "public/request";
        }
        if ((website != null && !website.isBlank()) || tooMany(http.getRemoteAddr())) {
            return "redirect:/public/request/done?ref=-";
        }
        session.removeAttribute("captcha");

        // Re-use an existing visitor profile with the same phone and first name
        String phone = Phones.normalize(visitor.getPhone());
        Visitor v = visitorRepo.findByPhone(phone).stream()
                .filter(x -> x.getFirstName().trim().equalsIgnoreCase(visitor.getFirstName().trim()))
                .findFirst().orElse(null);
        if (v == null) {
            visitor.setConsentToMessages(true);
            v = visitors.save(visitor);
        }
        VisitRequest r = new VisitRequest();
        r.setChannel(RequestChannel.ONLINE);
        r.setPurpose(purpose);
        r.setSubject(subject.trim());
        r.setDescription(description);
        r.setPreferredDate(preferredDate);
        r.setPreferredTime(preferredTime);
        r.setExpectedAttendees(Math.max(1, Math.min(50, expectedAttendees)));
        r = service.create(r, v, office);
        if (file != null && !file.isEmpty()) {
            try {
                service.addAttachment(r.getId(), file, null);
            } catch (BusinessException ignored) {
                // unsupported file type: the request itself is still saved
            }
        }
        return "redirect:/public/request/done?ref=" + r.getReferenceNumber();
    }

    @GetMapping("/request/done")
    public String done(@RequestParam String ref, Model model) {
        model.addAttribute("ref", ref);
        return "public/done";
    }

    // ---------------------------------------------------------------- citizen tracking portal

    @GetMapping("/track")
    public String track(@RequestParam(required = false) String ref, @RequestParam(required = false) String phone, Model model) {
        model.addAttribute("ref", ref);
        model.addAttribute("phone", phone);
        if (ref != null && phone != null && !ref.isBlank() && !phone.isBlank()) {
            Optional<VisitRequest> o = requests.findByReferenceNumberIgnoreCase(ref.trim());
            String p = Phones.normalize(phone);
            if (o.isPresent() && p != null && p.equals(o.get().getVisitor().getPhone())) {
                VisitRequest r = o.get();
                model.addAttribute("r", r);
                model.addAttribute("events", audit.publicTrail(AuditService.REQUEST, r.getId()));
                if (r.getQrToken() != null && (r.getStatus() == VisitStatus.SCHEDULED || r.getStatus() == VisitStatus.APPROVED)) {
                    model.addAttribute("passLink", "/public/pass/" + r.getQrToken());
                }
                if (r.getStatus() == VisitStatus.COMPLETED && feedbacks.findByRequest(r).isEmpty()) {
                    model.addAttribute("feedbackLink", "/public/feedback/" + r.getPublicToken());
                }
            } else {
                model.addAttribute("notFound", true);
            }
        }
        return "public/track";
    }

    // ---------------------------------------------------------------- visitor pass

    @GetMapping("/pass/{token}")
    public String pass(@PathVariable String token, Model model) {
        VisitRequest r = requests.findByQrToken(token).orElseThrow(() -> new NotFoundException("Pass"));
        model.addAttribute("r", r);
        model.addAttribute("qrImage", qr.dataUri(notify.passLink(r), 280));
        model.addAttribute("valid", r.getStatus() == VisitStatus.SCHEDULED || r.getStatus() == VisitStatus.APPROVED
                || r.getStatus() == VisitStatus.CHECKED_IN);
        return "public/pass";
    }

    @GetMapping(value = "/qr/{token}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrImage(@PathVariable String token) {
        VisitRequest r = requests.findByQrToken(token).orElseThrow(() -> new NotFoundException("Pass"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(qr.png(notify.passLink(r), 320));
    }

    // ---------------------------------------------------------------- feedback survey

    @GetMapping("/feedback/{token}")
    public String feedback(@PathVariable String token, Model model) {
        VisitRequest r = requests.findByPublicToken(token).orElseThrow(() -> new NotFoundException("Visit"));
        model.addAttribute("r", r);
        model.addAttribute("already", feedbacks.findByRequest(r).isPresent());
        return "public/feedback";
    }

    @PostMapping("/feedback/{token}")
    public String saveFeedback(@PathVariable String token, @RequestParam int rating,
                               @RequestParam(required = false) Integer receptionRating,
                               @RequestParam(required = false) Boolean issueAddressed,
                               @RequestParam(required = false) String comment, Model model) {
        VisitRequest r = requests.findByPublicToken(token).orElseThrow(() -> new NotFoundException("Visit"));
        if (r.getStatus() == VisitStatus.COMPLETED && feedbacks.findByRequest(r).isEmpty()) {
            Feedback f = new Feedback();
            f.setRequest(r);
            f.setRating(Math.max(1, Math.min(5, rating)));
            f.setReceptionRating(receptionRating == null ? null : Math.max(1, Math.min(5, receptionRating)));
            f.setIssueAddressed(issueAddressed);
            f.setComment(comment == null ? null : comment.substring(0, Math.min(2000, comment.length())));
            feedbacks.save(f);
            audit.log(AuditService.REQUEST, r.getId(), "FEEDBACK_RECEIVED", null, null, rating + "/5", false);
        }
        model.addAttribute("r", r);
        model.addAttribute("thanks", true);
        model.addAttribute("already", true);
        return "public/feedback";
    }

    // ---------------------------------------------------------------- lobby display screen

    @GetMapping("/lobby")
    public String lobby(@RequestParam(required = false) String office, Model model) {
        Office o = office == null ? null : officeRepo.findByCodeIgnoreCase(office).orElse(null);
        model.addAttribute("lobbyOffice", o);
        return "public/lobby";
    }

    @GetMapping(value = "/lobby/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> lobbyData(@RequestParam(required = false) String office) {
        LocalDate today = LocalDate.now();
        Set<Long> ids = new HashSet<>();
        Optional<Office> one = office == null ? Optional.empty() : officeRepo.findByCodeIgnoreCase(office);
        if (one.isPresent()) {
            ids.add(one.get().getId());
        } else {
            officeRepo.findByActiveTrueAndExternalFalseOrderBySortOrderAscNameCkbAsc().forEach(x -> ids.add(x.getId()));
            ids.add(-1L);
        }
        List<VisitRequest> inMeeting = requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.IN_MEETING), ids).stream()
                .filter(r -> today.equals(r.getQueueDate()))
                .sorted(Comparator.comparing(VisitRequest::getMeetingStartAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        List<VisitRequest> waiting = requests.findByStatusInAndOfficeIdIn(EnumSet.of(VisitStatus.CHECKED_IN), ids).stream()
                .filter(r -> today.equals(r.getQueueDate()))
                .sorted(Comparator.comparing((VisitRequest r) -> r.getPriority().getRank()).thenComparing(VisitRequest::getCheckInAt)).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("now", inMeeting.stream().map(VisitRequest::getQueueNumber).filter(Objects::nonNull).toList());
        m.put("waiting", waiting.stream().map(VisitRequest::getQueueNumber).filter(Objects::nonNull).toList());
        m.put("time", LocalDateTime.now().withNano(0).toLocalTime().toString());
        m.put("org", settings.orgName());
        return m;
    }

    // ---------------------------------------------------------------- helpers

    private void captcha(Model model, HttpSession session) {
        Random rnd = new Random();
        int a = 1 + rnd.nextInt(9);
        int b = 1 + rnd.nextInt(9);
        session.setAttribute("captcha", a + b);
        model.addAttribute("captchaQuestion", a + " + " + b + " = ?");
    }

    private boolean tooMany(String ip) {
        long now = System.currentTimeMillis();
        List<Long> list = rate.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            list.removeIf(t -> now - t > 3_600_000);
            list.add(now);
            return list.size() > 5;
        }
    }
}
