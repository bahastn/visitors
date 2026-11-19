package krd.parliament.vms.security;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class VmsUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final Role role;
    private final boolean enabled;
    private final boolean totpEnabled;
    private final String totpSecret;

    public VmsUserDetails(AppUser u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.password = u.getPasswordHash();
        this.fullName = u.getFullName();
        this.role = u.getRole();
        this.enabled = u.isEnabled();
        this.totpEnabled = u.isTotpEnabled();
        this.totpSecret = u.getTotpSecret();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    String getTotpSecret() {
        return totpSecret;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
