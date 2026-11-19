package krd.parliament.vms.web;

import jakarta.servlet.http.HttpServletRequest;
import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.NotificationRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.OfficeScope;
import krd.parliament.vms.service.BusinessException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;

/** Common model attributes for every page, and friendly handling of rule violations. */
@ControllerAdvice(basePackages = "krd.parliament.vms.web")
public class GlobalAdvice {

    private final CurrentUser currentUser;
    private final NotificationRepository notifications;
    private final VisitRequestRepository requests;
    private final OfficeScope scope;
    private final MessageSource messages;

    public GlobalAdvice(CurrentUser currentUser, NotificationRepository notifications, VisitRequestRepository requests,
                        OfficeScope scope, MessageSource messages) {
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.requests = requests;
        this.scope = scope;
        this.messages = messages;
    }

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        model.addAttribute("lang", lang);
        model.addAttribute("rtl", "ar".equals(lang) || "ckb".equals(lang));
        model.addAttribute("requestUri", request.getRequestURI());
        String qs = request.getQueryString() == null ? "" : request.getQueryString()
                .replaceAll("(^|&)page=\\d*", "").replaceAll("^&", "");
        model.addAttribute("queryNoPage", qs);
        String uri = request.getRequestURI();
        // Language switcher: current URI + current query (without "lang"), ready for "lang=xx" to be appended.
        String qsNoLang = request.getQueryString() == null ? "" : request.getQueryString()
                .replaceAll("(^|&)lang=[^&]*", "").replaceAll("^&", "");
        model.addAttribute("langBase", uri + "?" + (qsNoLang.isEmpty() ? "" : qsNoLang + "&"));
        if (!uri.startsWith("/public") && !uri.startsWith("/api") && !uri.startsWith("/login")) {
            AppUser me = currentUser.get();
            if (me != null) {
                model.addAttribute("me", me);
                model.addAttribute("unreadCount", notifications.countByRecipientUserAndReadFlagFalse(me));
                model.addAttribute("recentNotifications", notifications.findTop8ByRecipientUserOrderByCreatedAtDesc(me));
                model.addAttribute("myOffices", scope.accessible());
                model.addAttribute("currentOffice", scope.current());
                model.addAttribute("navPending", requests.countByStatusInAndOfficeIdIn(VisitStatus.pendingStatuses(), scope.ids()));
                model.addAttribute("navLobby", requests.countByStatusInAndOfficeIdIn(
                        EnumSet.of(VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING), scope.ids()));
                model.addAttribute("myInitials", initials(me.getFullName()));
                // Top navbar (PLAN-v3 3.1): visible apps, the current app and its menus
                Role role = me.getRole();
                model.addAttribute("apps", AppMenu.forRole(role));
                AppMenu app = AppMenu.ofPath(uri);
                if (app != null && app.visibleTo(role)) {
                    List<AppMenu.Item> items = app.itemsFor(role);
                    model.addAttribute("currentApp", app);
                    model.addAttribute("appItems", items);
                    model.addAttribute("activeItemUrl", activeItem(items, uri));
                } else {
                    model.addAttribute("appItems", List.of());
                }
            }
        }
    }

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException ex, HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("error", messages.getMessage(ex.getMessage(), ex.getArgs(), ex.getMessage(),
                LocaleContextHolder.getLocale()));
        return "redirect:" + back(request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String denied(AccessDeniedException ex, HttpServletRequest request, RedirectAttributes ra) {
        ra.addFlashAttribute("error", messages.getMessage("error.page403", null, LocaleContextHolder.getLocale()));
        String b = back(request);
        return "redirect:" + (b.equals(request.getRequestURI()) ? "/" : b);
    }

    /** URL of the menu item the request path belongs to (longest matching prefix), or {@code null}. */
    static String activeItem(List<AppMenu.Item> items, String uri) {
        String best = null;
        for (AppMenu.Item i : items) {
            String u = i.url();
            if ((uri.equals(u) || uri.startsWith(u + "/")) && (best == null || u.length() > best.length())) {
                best = u;
            }
        }
        return best;
    }

    /** Up to two initials for the navbar avatar ("Ahmad Karim" -> "AK"). */
    static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(parts[0].codePointAt(0));
        if (parts.length > 1) {
            sb.appendCodePoint(parts[parts.length - 1].codePointAt(0));
        }
        return sb.toString().toUpperCase();
    }

    static String back(HttpServletRequest request) {
        String ref = request.getHeader("Referer");
        if (ref == null) {
            return "/";
        }
        try {
            URI u = URI.create(ref);
            return (u.getRawPath() == null ? "/" : u.getRawPath()) + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        } catch (Exception e) {
            return "/";
        }
    }
}
