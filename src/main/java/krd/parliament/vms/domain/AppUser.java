package krd.parliament.vms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A staff member who logs into the system. */
@Entity
@Table(name = "users")
@Getter
@Setter
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 150)
    private String jobTitle;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    private boolean enabled = true;

    @Column(length = 5)
    private String preferredLanguage = "ckb";

    /** Base32 TOTP secret for two-factor login. */
    @Column(length = 64)
    private String totpSecret;

    private boolean totpEnabled;

    /** Secret token for the personal calendar feed (Google / Outlook subscription). */
    @Column(length = 64, unique = true)
    private String calendarToken;

    /** Main office of this user. */
    @ManyToOne(fetch = FetchType.EAGER)
    private Office office;

    /** Additional offices this user works for (e.g. a secretary serving two committees). */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_offices")
    private java.util.Set<Office> extraOffices = new java.util.HashSet<>();

    /** Works for every office (central reception, security, admin, auditors). */
    private boolean allOffices;

    private LocalDateTime lastLoginAt;

    private int failedLogins;

    public boolean canAccess(Office o) {
        if (o == null) {
            return false;
        }
        if (allOffices || role == Role.ADMIN) {
            return true;
        }
        if (office != null && office.getId().equals(o.getId())) {
            return true;
        }
        return extraOffices.stream().anyMatch(x -> x.getId().equals(o.getId()));
    }
}
