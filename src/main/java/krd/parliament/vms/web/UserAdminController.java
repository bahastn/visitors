package krd.parliament.vms.web;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.repo.OfficeRepository;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.NotFoundException;
import krd.parliament.vms.util.Phones;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Administrator: users, their roles and the offices they work for. */
@Controller
@RequestMapping("/users")
public class UserAdminController {

    private final UserRepository users;
    private final OfficeRepository offices;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Flash flash;

    public UserAdminController(UserRepository users, OfficeRepository offices, PasswordEncoder encoder, AuditService audit,
                               CurrentUser currentUser, Flash flash) {
        this.users = users;
        this.offices = offices;
        this.encoder = encoder;
        this.audit = audit;
        this.currentUser = currentUser;
        this.flash = flash;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long office, @RequestParam(required = false) Role role, Model model) {
        List<AppUser> list = users.findAllByOrderByFullNameAsc().stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> office == null || (u.getOffice() != null && u.getOffice().getId().equals(office))
                        || u.getExtraOffices().stream().anyMatch(o -> o.getId().equals(office)))
                .toList();
        model.addAttribute("users", list);
        model.addAttribute("offices", offices.findByActiveTrueAndExternalFalseOrderBySortOrderAscNameCkbAsc());
        model.addAttribute("f_office", office);
        model.addAttribute("f_role", role);
        return "users/list";
    }

    private void formModel(Model model, AppUser u) {
        model.addAttribute("u", u);
        model.addAttribute("offices", offices.findByActiveTrueAndExternalFalseOrderBySortOrderAscNameCkbAsc());
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        AppUser u = new AppUser();
        u.setRole(Role.SECRETARY);
        formModel(model, u);
        return "users/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        formModel(model, users.findById(id).orElseThrow(() -> new NotFoundException("User")));
        return "users/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id, @RequestParam String username, @RequestParam String fullName,
                       @RequestParam(required = false) String jobTitle, @RequestParam(required = false) String email,
                       @RequestParam(required = false) String phone, @RequestParam Role role,
                       @RequestParam(required = false) Long officeId,
                       @RequestParam(required = false) List<Long> extraOfficeIds,
                       @RequestParam(defaultValue = "false") boolean allOffices,
                       @RequestParam(defaultValue = "ckb") String preferredLanguage,
                       @RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam(required = false) String password, RedirectAttributes ra) {
        AppUser u = id == null ? new AppUser() : users.findById(id).orElseThrow(() -> new NotFoundException("User"));
        String back = "redirect:" + (id == null ? "/users/new" : "/users/" + id + "/edit");
        String uname = username.trim().toLowerCase();
        if (!uname.matches("[a-z0-9._-]{3,60}")) {
            flash.error(ra, "error.username");
            return back;
        }
        if ((id == null || !uname.equalsIgnoreCase(u.getUsername())) && users.existsByUsernameIgnoreCase(uname)) {
            flash.error(ra, "error.usernameTaken");
            return back;
        }
        if (id == null && (password == null || password.length() < 8)) {
            flash.error(ra, "error.passwordLength");
            return back;
        }
        if (id != null && id.equals(currentUser.require().getId()) && (!enabled || role != Role.ADMIN)) {
            flash.error(ra, "error.selfLockout");
            return back;
        }
        Office main = officeId == null ? null : offices.findById(officeId).orElse(null);
        boolean central = allOffices || role == Role.ADMIN;
        if (main == null && !central) {
            flash.error(ra, "error.userOffice");
            return back;
        }
        u.setUsername(uname);
        u.setFullName(fullName.trim());
        u.setJobTitle(jobTitle);
        u.setEmail(email == null || email.isBlank() ? null : email.trim());
        u.setPhone(phone == null || phone.isBlank() ? null : Phones.normalize(phone));
        u.setRole(role);
        u.setOffice(main);
        Set<Office> extra = new HashSet<>();
        if (extraOfficeIds != null) {
            extra.addAll(offices.findAllById(extraOfficeIds));
        }
        if (main != null) {
            extra.removeIf(o -> o.getId().equals(main.getId()));
        }
        u.getExtraOffices().clear();
        u.getExtraOffices().addAll(extra);
        u.setAllOffices(central);
        u.setPreferredLanguage(preferredLanguage);
        u.setEnabled(id == null || enabled);
        if (password != null && !password.isBlank()) {
            if (password.length() < 8) {
                flash.error(ra, "error.passwordLength");
                return back;
            }
            u.setPasswordHash(encoder.encode(password));
        }
        if (u.getCalendarToken() == null) {
            u.setCalendarToken(CryptoService.randomToken(24));
        }
        users.save(u);
        audit.log(AuditService.USER, u.getId(), id == null ? "USER_CREATED" : "USER_UPDATED", u.getUsername() + " / " + u.getRole());
        flash.ok(ra, "msg.saved");
        return "redirect:/users";
    }

    @PostMapping("/{id}/reset-2fa")
    public String reset2fa(@PathVariable Long id, RedirectAttributes ra) {
        AppUser u = users.findById(id).orElseThrow(() -> new NotFoundException("User"));
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        users.save(u);
        audit.log(AuditService.USER, id, "2FA_RESET", u.getUsername());
        flash.ok(ra, "msg.saved");
        return "redirect:/users";
    }
}
