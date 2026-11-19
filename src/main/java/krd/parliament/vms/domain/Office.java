package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An office of the parliament that receives visitors (Speaker, Deputy Speaker, a committee, an MP...)
 * or an external body that visits can be referred to. Every visit, calendar block, case and task
 * belongs to one office, and each office has its own working hours and approval rules.
 */
@Entity
@Table(name = "offices")
@Getter
@Setter
public class Office extends BaseEntity {

    // ---- Names in the three interface languages ----
    @NotBlank
    @Column(nullable = false, length = 200)
    private String nameCkb;

    @Column(length = 200)
    private String nameAr;

    @Column(length = 200)
    private String nameEn;

    @NotBlank
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.OfficeType type = Enums.OfficeType.DEPARTMENT;

    @ManyToOne(fetch = FetchType.LAZY)
    private Office parent;

    /** Title of the person visitors meet, e.g. "Deputy Speaker", in three languages. */
    @Column(length = 150)
    private String hostTitleCkb;
    @Column(length = 150)
    private String hostTitleAr;
    @Column(length = 150)
    private String hostTitleEn;

    /** Name of the current office holder (optional). */
    @Column(length = 150)
    private String hostName;

    @Column(length = 200)
    private String defaultLocation;

    /** Colour used for this office in the calendar and reports. */
    @Column(length = 9)
    private String color = "#05668d";

    // ---- Working rules ----
    private LocalTime workStart = LocalTime.of(9, 0);
    private LocalTime workEnd = LocalTime.of(15, 0);

    @Column(length = 80)
    private String workDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY";

    /** 1 = secretary may approve, 2 = secretary recommends and head / director decides. */
    private int approvalLevels = 2;
    private int overdueDays = 7;
    private int defaultDuration = 30;

    /** Shown in the public online request form. */
    private boolean acceptsPublicRequests = true;

    /** Body outside this system (ministry, other institution): referral only, no calendar. */
    private boolean external;

    private boolean active = true;
    private int sortOrder = 100;

    // ---- Contact & system linking ----
    @Column(length = 150)
    private String contactPerson;
    @Column(length = 150)
    private String contactEmail;
    @Column(length = 30)
    private String contactPhone;

    /** If set, referrals are pushed to this URL of the other body's system (JSON POST). */
    @Column(length = 300)
    private String endpointUrl;

    /** Key another system uses to call this system's API on behalf of this office. */
    @Column(length = 80, unique = true)
    private String apiKey;

    // ---- Localised helpers (used in templates as o.name / o.hostTitle) ----

    public String getName() {
        return name(LocaleContextHolder.getLocale());
    }

    public String name(Locale locale) {
        return pick(locale, nameCkb, nameAr, nameEn);
    }

    public String getHostTitle() {
        return hostTitle(LocaleContextHolder.getLocale());
    }

    public String hostTitle(Locale locale) {
        String t = pick(locale, hostTitleCkb, hostTitleAr, hostTitleEn);
        return t == null || t.isBlank() ? name(locale) : t;
    }

    public Set<DayOfWeek> getWorkDaySet() {
        return Arrays.stream((workDays == null ? "" : workDays).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(DayOfWeek::valueOf).collect(Collectors.toSet());
    }

    public boolean isTwoLevelApproval() {
        return approvalLevels >= 2;
    }

    private static String pick(Locale locale, String ckb, String ar, String en) {
        String lang = locale == null ? "ckb" : locale.getLanguage();
        String v = switch (lang) {
            case "ar" -> ar;
            case "en" -> en;
            default -> ckb;
        };
        if (v != null && !v.isBlank()) {
            return v;
        }
        // fall back: Kurdish, then English, then Arabic
        if (ckb != null && !ckb.isBlank()) return ckb;
        if (en != null && !en.isBlank()) return en;
        return ar;
    }
}
