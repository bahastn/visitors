package krd.parliament.vms.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.security.TotpService;
import krd.parliament.vms.security.VmsUserDetails;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.QrService;
import krd.parliament.vms.service.SettingsService;
import krd.parliament.vms.util.Phones;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

/** Own profile: contact details, language, password, two-factor login and calendar feed. */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final PasswordEncoder encoder;
    private final TotpService totp;
    private final QrService qr;
    private final SettingsService settings;
    private final AuditService audit;
    private final LocaleResolver localeResolver;
    private final Flash flash;

    public ProfileController(UserRepository users, CurrentUser currentUser, PasswordEncoder encoder, TotpService totp,
                             QrService qr, SettingsService settings, AuditService audit, LocaleResolver localeResolver, Flash flash) {
        this.users = users;
        this.currentUser = currentUser;
        this.encoder = encoder;
        this.totp = totp;
        this.qr = qr;
        this.settings = settings;
        this.audit = audit;
        this.localeResolver = localeResolver;
        this.flash = flash;
    }

    @GetMapping
    public String profile(Model model) {
        model.addAttribute("u", currentUser.require());
        return "profile/index";
    }

    @PostMapping
    public String save(@RequestParam String fullName, @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone, @RequestParam String preferredLanguage,
                       HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        AppUser u = currentUser.require();
        u.setFullName(fullName.trim());
        u.setEmail(email == null || email.isBlank() ? null : email.trim());
        u.setPhone(phone == null || phone.isBlank() ? null : Phones.normalize(phone));
        u.setPreferredLanguage(preferredLanguage);
        users.save(u);
        localeResolver.setLocale(req, res, Locale.forLanguageTag(preferredLanguage));
        flash.ok(ra, "msg.saved");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String password(@RequestParam String current, @RequestParam String password, @RequestParam String confirm,
                           RedirectAttributes ra) {
        AppUser u = currentUser.require();
        if (!encoder.matches(current, u.getPasswordHash())) {
            flash.error(ra, "error.currentPassword");
        } else if (password.length() < 8 || !password.equals(confirm)) {
            flash.error(ra, "error.passwordLength");
        } else {
            u.setPasswordHash(encoder.encode(password));
            users.save(u);
            audit.log(AuditService.USER, u.getId(), "PASSWORD_CHANGED", null);
            flash.ok(ra, "msg.passwordChanged");
        }
        return "redirect:/profile";
    }

    @GetMapping("/2fa")
    public String twoFactor(Model model, HttpServletRequest req) {
        AppUser u = currentUser.require();
        String secret = (String) req.getSession().getAttribute("pendingTotp");
        if (secret == null) {
            secret = totp.newSecret();
            req.getSession().setAttribute("pendingTotp", secret);
        }
        String uri = totp.otpAuthUri("Kurdistan Parliament VMS", u.getUsername(), secret);
        model.addAttribute("u", u);
        model.addAttribute("secret", secret);
        model.addAttribute("qrImage", qr.dataUri(uri, 220));
        model.addAttribute("required", req.getParameter("required") != null);
        return "profile/2fa";
    }

    @PostMapping("/2fa/enable")
    public String enable2fa(@RequestParam String code, HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        AppUser u = currentUser.require();
        String secret = (String) req.getSession().getAttribute("pendingTotp");
        if (secret == null || !totp.verify(secret, code)) {
            flash.error(ra, "error.totp");
            return "redirect:/profile/2fa";
        }
        u.setTotpSecret(secret);
        u.setTotpEnabled(true);
        users.save(u);
        req.getSession().removeAttribute("pendingTotp");
        refreshPrincipal(u, req, res);
        audit.log(AuditService.USER, u.getId(), "2FA_ENABLED", null);
        flash.ok(ra, "msg.twoFactorOn");
        return "redirect:/profile";
    }

    @PostMapping("/2fa/disable")
    public String disable2fa(@RequestParam String code, HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        AppUser u = currentUser.require();
        if (!totp.verify(u.getTotpSecret(), code)) {
            flash.error(ra, "error.totp");
            return "redirect:/profile";
        }
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        users.save(u);
        refreshPrincipal(u, req, res);
        audit.log(AuditService.USER, u.getId(), "2FA_DISABLED", null);
        flash.ok(ra, "msg.saved");
        return "redirect:/profile";
    }

    @PostMapping("/calendar-token")
    public String newCalendarToken(RedirectAttributes ra) {
        AppUser u = currentUser.require();
        u.setCalendarToken(CryptoService.randomToken(24));
        users.save(u);
        flash.ok(ra, "msg.saved");
        return "redirect:/calendar";
    }

    /** Updates the logged-in principal so the 2FA requirement check sees the new state immediately. */
    private void refreshPrincipal(AppUser u, HttpServletRequest req, HttpServletResponse res) {
        VmsUserDetails d = new VmsUserDetails(u);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(d, null, d.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        new HttpSessionSecurityContextRepository().saveContext(ctx, req, res);
    }
}
