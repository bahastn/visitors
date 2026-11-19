package krd.parliament.vms.security;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Access to the logged-in staff member. Exposed to Thymeleaf as {@code @currentUser}. */
@Component("currentUser")
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<VmsUserDetails> details() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof VmsUserDetails d) {
            return Optional.of(d);
        }
        return Optional.empty();
    }

    public AppUser get() {
        return details().flatMap(d -> users.findById(d.getId())).orElse(null);
    }

    public AppUser require() {
        AppUser u = get();
        if (u == null) {
            throw new IllegalStateException("Not logged in");
        }
        return u;
    }

    public Role role() {
        return details().map(VmsUserDetails::getRole).orElse(null);
    }

    public boolean is(String... roles) {
        Role r = role();
        if (r == null) {
            return false;
        }
        for (String s : roles) {
            if (r.name().equals(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean canDecide() {
        Role r = role();
        return r != null && r.canDecide();
    }

    public String name() {
        return details().map(VmsUserDetails::getFullName).orElse("");
    }
}
