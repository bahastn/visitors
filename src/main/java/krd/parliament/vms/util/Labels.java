package krd.parliament.vms.util;

import krd.parliament.vms.domain.Enums;
import krd.parliament.vms.domain.Priority;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.domain.VisitStatus;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Translates enum values for the UI. Used in templates as {@code @labels.t(value)}. */
@Component("labels")
public class Labels {

    private static final Map<String, Enum<?>[]> TYPES = new HashMap<>();

    static {
        TYPES.put("Role", Role.values());
        TYPES.put("VisitStatus", VisitStatus.values());
        TYPES.put("Priority", Priority.values());
        TYPES.put("RequestChannel", Enums.RequestChannel.values());
        TYPES.put("VisitPurpose", Enums.VisitPurpose.values());
        TYPES.put("OrganizationType", Enums.OrganizationType.values());
        TYPES.put("Gender", Enums.Gender.values());
        TYPES.put("IdType", Enums.IdType.values());
        TYPES.put("Governorate", Enums.Governorate.values());
        TYPES.put("ContactChannel", Enums.ContactChannel.values());
        TYPES.put("Language", Enums.Language.values());
        TYPES.put("Recommendation", Enums.Recommendation.values());
        TYPES.put("BlockType", Enums.BlockType.values());
        TYPES.put("WatchlistLevel", Enums.WatchlistLevel.values());
        TYPES.put("NotificationChannel", Enums.NotificationChannel.values());
        TYPES.put("NotificationStatus", Enums.NotificationStatus.values());
        TYPES.put("TaskStatus", Enums.TaskStatus.values());
        TYPES.put("CaseStatus", Enums.CaseStatus.values());
        TYPES.put("OfficeType", Enums.OfficeType.values());
    }

    private final MessageSource messages;

    /**
     * The same locale with the Unicode extension {@code nu-latn}, so every locale-aware number formatter
     * (MessageFormat, NumberFormat, String.format, Thymeleaf #numbers) uses Latin digits 0-9 also for
     * ckb and ar. Language, message bundle lookup and {@link Locale#getLanguage()} are unchanged.
     */
    public static Locale latn(Locale locale) {
        if (locale == null || "latn".equals(locale.getUnicodeLocaleType("nu"))) {
            return locale;
        }
        try {
            return new Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build();
        } catch (java.util.IllformedLocaleException e) {
            return locale;
        }
    }

    public Labels(MessageSource messages) {
        this.messages = messages;
    }

    public String t(Enum<?> e) {
        return t(e, LocaleContextHolder.getLocale());
    }

    public String t(Enum<?> e, Locale locale) {
        if (e == null) {
            return "";
        }
        String key = "enum." + e.getDeclaringClass().getSimpleName() + "." + e.name();
        return messages.getMessage(key, null, e.name(), latn(locale));
    }

    /** Enum-keyed counts translated to the current language (zero values removed), for charts. */
    public Map<String, Long> translateMap(Map<? extends Enum<?>, Long> data) {
        Map<String, Long> res = new java.util.LinkedHashMap<>();
        if (data != null) {
            data.forEach((k, v) -> {
                if (v != null && v > 0) {
                    res.put(t(k), v);
                }
            });
        }
        return res;
    }

    /** Renders audit details written with AuditService.k(...) in the current language. */
    public String details(String details) {
        if (details == null || !details.startsWith("#")) {
            return details;
        }
        String[] parts = details.substring(1).split("\\|", -1);
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            String a = parts[i];
            args[i - 1] = a.startsWith("@") ? msg(a.substring(1)) : a;
        }
        return messages.getMessage(parts[0], args, parts[0], LocaleContextHolder.getLocale());
    }

    public Enum<?>[] values(String type) {
        return TYPES.getOrDefault(type, new Enum<?>[0]);
    }

    public String msg(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    public String msg(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, key, latn(locale));
    }
}
