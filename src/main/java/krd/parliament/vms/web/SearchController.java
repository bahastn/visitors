package krd.parliament.vms.web;

import krd.parliament.vms.domain.ConstituentCase;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.Visitor;
import krd.parliament.vms.repo.ConstituentCaseRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.repo.VisitorRepository;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.VisitorService;
import krd.parliament.vms.util.Phones;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;
import java.util.regex.Pattern;

import static krd.parliament.vms.domain.Role.*;

/** Global search: the full {@code /search} page and the navbar awesome bar ({@code GET /api/search}). */
@Controller
public class SearchController {

    private static final int MAX_Q = 100;
    private static final int PER_GROUP = 5;
    private static final Pattern PHONE_LIKE = Pattern.compile("^\\+?[0-9\\s()\\-]{7,}$");
    private static final List<Locale> LOCALES = List.of(
            Locale.forLanguageTag("ckb"), Locale.forLanguageTag("ar"), Locale.ENGLISH);

    /** Awesome-bar actions; roles mirror the @PreAuthorize / SecurityConfig rules of the target pages. */
    private enum Action {
        NEW_VISIT("search.action.newVisit", "/requests/new", "bi-plus-lg",
                EnumSet.of(ADMIN, DIRECTOR, SECRETARY, RECEPTION, HEAD)),
        NEW_VISITOR("search.action.newVisitor", "/visitors/new", "bi-person-plus",
                EnumSet.of(ADMIN, DIRECTOR, SECRETARY, RECEPTION)),
        CALENDAR("search.action.calendar", "/calendar", "bi-calendar3",
                EnumSet.of(HEAD, DIRECTOR, SECRETARY, ADMIN, AUDITOR)),
        SCAN("search.action.scan", "/reception", "bi-qr-code-scan",
                EnumSet.of(RECEPTION, SECRETARY, SECURITY, ADMIN, DIRECTOR));

        final String key;
        final String url;
        final String icon;
        final Set<Role> roles;

        Action(String key, String url, String icon, Set<Role> roles) {
            this.key = key;
            this.url = url;
            this.icon = icon;
            this.roles = roles;
        }
    }

    private final VisitorService visitors;
    private final VisitorRepository visitorRepo;
    private final VisitRequestRepository requests;
    private final ConstituentCaseRepository cases;
    private final OfficeScope scope;
    private final CurrentUser currentUser;
    private final MessageSource messages;

    public SearchController(VisitorService visitors, VisitorRepository visitorRepo, VisitRequestRepository requests,
                            ConstituentCaseRepository cases, OfficeScope scope, CurrentUser currentUser,
                            MessageSource messages) {
        this.visitors = visitors;
        this.visitorRepo = visitorRepo;
        this.requests = requests;
        this.cases = cases;
        this.scope = scope;
        this.currentUser = currentUser;
        this.messages = messages;
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        String query = clean(q);
        model.addAttribute("q", query);
        if (query.length() >= 2) {
            // A full reference number goes straight to the request
            List<VisitRequest> exact = requests.findByReferenceNumberIgnoreCase(query).stream()
                    .filter(r -> scope.canAccess(r.getOffice())).toList();
            if (!exact.isEmpty()) {
                return "redirect:/requests/" + exact.get(0).getId();
            }
            model.addAttribute("visitors", visitors.search(query, PageRequest.of(0, 20)).getContent());
            model.addAttribute("requests", requests.quickSearch(query, scope.allIds(), PageRequest.of(0, 30)));
        } else {
            model.addAttribute("visitors", List.of());
            model.addAttribute("requests", List.of());
        }
        return "search";
    }

    /**
     * Awesome bar. Shape: {@code {"goto": url|null, "groups": [{"key","label","items":[{"label","sub","url","icon"}]}]}}.
     * Same scoping as the {@code /search} page: visits and cases only from offices the user may access
     * ({@link OfficeScope#allIds()}); visitors are global, as on the visitor pages.
     */
    @GetMapping(value = "/api/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> api(@RequestParam(defaultValue = "") String q) {
        String query = clean(q);
        Role role = currentUser.role();
        Locale locale = LocaleContextHolder.getLocale();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goto", null);
        List<Map<String, Object>> groups = new ArrayList<>();
        out.put("groups", groups);
        if (query.isEmpty() || role == null) {
            return out;
        }

        String phoneNeedle = phoneNeedle(query);
        if (query.length() >= 2) {
            Set<Long> offices = scope.allIds();
            out.put("goto", gotoUrl(query, phoneNeedle, offices));

            // Visits
            List<Map<String, Object>> visitItems = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (String needle : needles(query, phoneNeedle)) {
                for (VisitRequest r : requests.searchWithVisitor(needle, offices, PageRequest.of(0, PER_GROUP))) {
                    if (visitItems.size() < PER_GROUP && seen.add(r.getId()) && scope.canAccess(r.getOffice())) {
                        String sub = r.getReferenceNumber() + (r.getSubject() == null ? "" : " · " + r.getSubject());
                        visitItems.add(item(r.getVisitor().getFullName(), sub, "/requests/" + r.getId(), "bi-calendar2-check"));
                    }
                }
            }
            addGroup(groups, "visits", "search.group.visits", visitItems, locale);

            // Visitors (global, like /search and /visitors)
            List<Map<String, Object>> visitorItems = new ArrayList<>();
            seen.clear();
            for (String needle : needles(query, phoneNeedle)) {
                for (Visitor v : visitors.search(needle, PageRequest.of(0, PER_GROUP)).getContent()) {
                    if (visitorItems.size() < PER_GROUP && seen.add(v.getId())) {
                        String sub = v.getPhone() == null ? "" : v.getPhone();
                        if (v.getOrganization() != null && !v.getOrganization().isBlank()) {
                            sub = sub.isEmpty() ? v.getOrganization() : sub + " · " + v.getOrganization();
                        }
                        visitorItems.add(item(v.getFullName(), sub, "/visitors/" + v.getId(), "bi-person"));
                    }
                }
            }
            addGroup(groups, "visitors", "search.group.visitors", visitorItems, locale);

            // Cases
            List<Map<String, Object>> caseItems = new ArrayList<>();
            seen.clear();
            for (String needle : needles(query, phoneNeedle)) {
                for (ConstituentCase c : cases.quickSearch(needle, offices, PageRequest.of(0, PER_GROUP))) {
                    if (caseItems.size() < PER_GROUP && seen.add(c.getId()) && scope.canAccess(c.getOffice())) {
                        String sub = (c.getCaseNumber() == null ? "" : c.getCaseNumber())
                                + (c.getVisitor() == null ? "" : " · " + c.getVisitor().getFullName());
                        caseItems.add(item(c.getTitle(), sub, "/cases/" + c.getId(), "bi-folder2-open"));
                    }
                }
            }
            addGroup(groups, "cases", "search.group.cases", caseItems, locale);
        }

        // Actions: matched against their words in all three languages
        String lq = query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> actionItems = new ArrayList<>();
        for (Action a : Action.values()) {
            if (a.roles.contains(role) && actionMatches(a, lq)) {
                actionItems.add(item(msg(a.key, locale), "", a.url, a.icon));
            }
        }
        addGroup(groups, "actions", "search.group.actions", actionItems, locale);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private String gotoUrl(String query, String phoneNeedle, Set<Long> offices) {
        Optional<VisitRequest> ref = requests.findByReferenceNumberIgnoreCase(query)
                .filter(r -> r.getOffice() != null && offices.contains(r.getOffice().getId()) && scope.canAccess(r.getOffice()));
        if (ref.isPresent()) {
            return "/requests/" + ref.get().getId();
        }
        Optional<ConstituentCase> cs = cases.findByCaseNumberIgnoreCase(query)
                .filter(c -> c.getOffice() != null && offices.contains(c.getOffice().getId()) && scope.canAccess(c.getOffice()));
        if (cs.isPresent()) {
            return "/cases/" + cs.get().getId();
        }
        if (phoneNeedle != null) {
            List<Visitor> byPhone = visitorRepo.findByPhone(Phones.normalize(query));
            if (byPhone.size() == 1) {
                return "/visitors/" + byPhone.get(0).getId();
            }
        }
        return null;
    }

    private boolean actionMatches(Action a, String lq) {
        for (Locale l : LOCALES) {
            String label = messages.getMessage(a.key, null, "", l);
            String words = messages.getMessage(a.key + ".words", null, "", l);
            for (String phrase : (label + "," + words).split(",")) {
                String p = phrase.trim().toLowerCase(Locale.ROOT);
                if (!p.isEmpty() && phraseMatches(p, lq)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when the phrase contains the query, or every query word is the start of a word in the phrase. */
    private static boolean phraseMatches(String phrase, String lq) {
        if (phrase.contains(lq)) {
            return true;
        }
        String[] words = phrase.split("\\s+");
        for (String t : lq.split("\\s+")) {
            if (t.isEmpty()) {
                continue;
            }
            boolean hit = false;
            for (String w : words) {
                if (w.startsWith(t)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /** Trim, Latin digits, collapse spaces, cap the length. */
    private static String clean(String q) {
        if (q == null) {
            return "";
        }
        String s = Phones.toWesternDigits(q).trim().replaceAll("\\s+", " ");
        return s.length() > MAX_Q ? s.substring(0, MAX_Q).trim() : s;
    }

    /**
     * For phone-like input, the digits to look for inside stored (normalised, +964...) numbers:
     * "0750 123 4567" -> "7501234567". Null when the input is not phone-like.
     */
    private static String phoneNeedle(String q) {
        if (!PHONE_LIKE.matcher(q).matches()) {
            return null;
        }
        String n = Phones.normalize(q);
        String digits = n == null ? "" : n.replaceAll("[^0-9]", "");
        if (digits.startsWith("964")) {
            digits = digits.substring(3);
        }
        digits = digits.replaceFirst("^0+", "");
        return digits.length() >= 6 ? digits : null;
    }

    private static List<String> needles(String query, String phoneNeedle) {
        return phoneNeedle == null || phoneNeedle.equals(query) ? List.of(query) : List.of(query, phoneNeedle);
    }

    private static Map<String, Object> item(String label, String sub, String url, String icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label == null ? "" : label);
        m.put("sub", sub == null ? "" : sub);
        m.put("url", url);
        m.put("icon", icon);
        return m;
    }

    private void addGroup(List<Map<String, Object>> groups, String key, String labelKey,
                          List<Map<String, Object>> items, Locale locale) {
        if (items.isEmpty()) {
            return;
        }
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("key", key);
        g.put("label", msg(labelKey, locale));
        g.put("items", items.size() > PER_GROUP ? items.subList(0, PER_GROUP) : items);
        groups.add(g);
    }

    private String msg(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }
}
