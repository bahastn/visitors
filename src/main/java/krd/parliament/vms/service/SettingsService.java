package krd.parliament.vms.service;

import krd.parliament.vms.domain.SystemSetting;
import krd.parliament.vms.repo.SettingRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parliament-wide settings (Administration → Settings). Office-specific rules such as working hours
 * and approval levels live on each Office. Exposed to templates as {@code @settings}.
 */
@Service("settings")
public class SettingsService {

    public static final String ORG_NAME_CKB = "org.name.ckb";
    public static final String ORG_NAME_AR = "org.name.ar";
    public static final String ORG_NAME_EN = "org.name.en";
    public static final String REQUIRE_2FA_SENIOR = "security.require2faSenior";
    public static final String REMINDER_HOURS = "notify.reminderHours";
    public static final String MORNING_SUMMARY = "notify.morningSummary";
    public static final String FEEDBACK_ENABLED = "notify.feedbackSurvey";
    public static final String PUBLIC_FORM_ENABLED = "public.formEnabled";
    public static final String SHOW_HIJRI = "dates.showHijri";
    public static final String SHOW_KURDISH = "dates.showKurdish";
    /** Interface colour theme: "default" (Odoo purple/teal) or "parliament" (navy/orange). */
    public static final String UI_THEME = "ui.theme";
    public static final java.util.Set<String> THEMES = java.util.Set.of("default", "parliament");

    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(ORG_NAME_CKB, "پەرلەمانی کوردستان - عێراق");
        DEFAULTS.put(ORG_NAME_AR, "برلمان كوردستان - العراق");
        DEFAULTS.put(ORG_NAME_EN, "Kurdistan Parliament - Iraq");
        DEFAULTS.put(REQUIRE_2FA_SENIOR, "false");
        DEFAULTS.put(REMINDER_HOURS, "2");
        DEFAULTS.put(MORNING_SUMMARY, "true");
        DEFAULTS.put(FEEDBACK_ENABLED, "true");
        DEFAULTS.put(PUBLIC_FORM_ENABLED, "true");
        DEFAULTS.put(SHOW_HIJRI, "true");
        DEFAULTS.put(SHOW_KURDISH, "true");
        DEFAULTS.put(UI_THEME, "default");
    }

    public static final java.util.Set<String> BOOLEAN_KEYS = java.util.Set.of(REQUIRE_2FA_SENIOR, MORNING_SUMMARY,
            FEEDBACK_ENABLED, PUBLIC_FORM_ENABLED, SHOW_HIJRI, SHOW_KURDISH);

    private final SettingRepository repo;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SettingRepository repo) {
        this.repo = repo;
    }

    public String get(String key) {
        return cache.computeIfAbsent(key, k -> repo.findById(k).map(SystemSetting::getValue)
                .orElse(DEFAULTS.getOrDefault(k, "")));
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    public boolean getBool(String key) {
        return Boolean.parseBoolean(get(key).trim());
    }

    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        DEFAULTS.keySet().forEach(k -> m.put(k, get(k)));
        return m;
    }

    @Transactional
    public void set(String key, String value) {
        String v = value == null ? "" : value.trim();
        repo.save(new SystemSetting(key, v));
        cache.put(key, v);
    }

    /** Current UI theme, always one of {@link #THEMES} (rendered as {@code <html data-theme>}). */
    public String theme() {
        String v = get(UI_THEME);
        v = v == null ? "" : v.trim().toLowerCase();
        return THEMES.contains(v) ? v : "default";
    }

    /** Organisation name in the current interface language. */
    public String orgName() {
        return orgName(LocaleContextHolder.getLocale().getLanguage());
    }

    public String orgName(String lang) {
        String v = switch (lang) {
            case "ar" -> get(ORG_NAME_AR);
            case "en" -> get(ORG_NAME_EN);
            default -> get(ORG_NAME_CKB);
        };
        return v == null || v.isBlank() ? get(ORG_NAME_CKB) : v;
    }
}
