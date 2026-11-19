package krd.parliament.vms.util;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * Date display helpers, used in templates as {@code @dates}. One format everywhere (v3):
 * dates {@code 2026-11-19}, times 24h {@code 14:30}, Latin digits. Formatters are locale-free
 * (DateTimeFormatter uses DecimalStyle.STANDARD), so ckb/ar never get Arabic-Indic digits.
 */
@Component("dates")
public class Dates {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String[] MONTHS_CKB = {"کانوونی دووەم", "شوبات", "ئازار", "نیسان", "ئایار", "حوزەیران",
            "تەمموز", "ئاب", "ئەیلوول", "تشرینی یەکەم", "تشرینی دووەم", "کانوونی یەکەم"};
    private static final String[] MONTHS_AR = {"كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
            "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"};
    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};
    private static final String[] DAYS_SHORT_CKB = {"دوو", "سێ", "چوار", "پێنج", "هەینی", "شەممە", "یەک"};
    private static final String[] DAYS_SHORT_AR = {"اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت", "أحد"};
    private static final String[] DAYS_SHORT_EN = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] DAYS_EN = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] DAYS_CKB = {"دووشەممە", "سێشەممە", "چوارشەممە", "پێنجشەممە", "هەینی", "شەممە", "یەکشەممە"};
    private static final String[] DAYS_AR = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};


    public String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    public String date(LocalDateTime d) {
        return d == null ? "" : d.format(DATE);
    }

    public String time(LocalDateTime d) {
        return d == null ? "" : d.format(TIME);
    }

    public String dt(LocalDateTime d) {
        return d == null ? "" : d.format(DATETIME);
    }

    // ---- Day ribbon on the dashboard: 07:00 to 18:00 ----
    private static final int RIBBON_START = 7 * 60;
    private static final int RIBBON_SPAN = 11 * 60;

    /** Position of a time on the day ribbon, in percent. */
    public double ribbonPos(LocalDateTime t) {
        if (t == null) {
            return 0;
        }
        int m = t.getHour() * 60 + t.getMinute() - RIBBON_START;
        return Math.max(0, Math.min(100, m * 100.0 / RIBBON_SPAN));
    }

    /** Width of a duration on the day ribbon, in percent. */
    public double ribbonLen(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }
        return Math.max(0.8, ribbonPos(to) - ribbonPos(from));
    }

    public double ribbonNow() {
        return ribbonPos(LocalDateTime.now());
    }

    /** Value for an HTML datetime-local input. */
    public String input(LocalDateTime d) {
        return d == null ? "" : d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    public String iso(LocalDateTime d) {
        return d == null ? "" : d.withNano(0).toString();
    }

    public String dayName(LocalDate d) {
        if (d == null) {
            return "";
        }
        String lang = LocaleContextHolder.getLocale().getLanguage();
        int i = d.getDayOfWeek().getValue() - 1;
        return switch (lang) {
            case "ckb" -> DAYS_CKB[i];
            case "ar" -> DAYS_AR[i];
            default -> d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        };
    }

    private static String lang() {
        return LocaleContextHolder.getLocale().getLanguage();
    }

    /** Gregorian month names in the current language (January first). */
    public String[] monthNames() {
        return switch (lang()) {
            case "ckb" -> MONTHS_CKB;
            case "ar" -> MONTHS_AR;
            default -> MONTHS_EN;
        };
    }

    /** Day names starting with Sunday (JavaScript order). */
    public String[] dayNamesJs() {
        String[] src = switch (lang()) {
            case "ckb" -> DAYS_CKB;
            case "ar" -> DAYS_AR;
            default -> DAYS_EN;
        };
        String[] r = new String[7];
        r[0] = src[6];
        System.arraycopy(src, 0, r, 1, 6);
        return r;
    }

    /** Short day names starting with Sunday (JavaScript order). */
    public String[] dayShortNamesJs() {
        String[] src = switch (lang()) {
            case "ckb" -> DAYS_SHORT_CKB;
            case "ar" -> DAYS_SHORT_AR;
            default -> DAYS_SHORT_EN;
        };
        String[] r = new String[7];
        r[0] = src[6];
        System.arraycopy(src, 0, r, 1, 6);
        return r;
    }

    /** Plain YYYY-MM-DD date (v3: one date format everywhere). */
    public String longDate(LocalDate d) {
        return date(d);
    }

    public String longDate(LocalDateTime d) {
        return date(d);
    }

    public String dayNameOf(String dayOfWeek) {
        return dayName(DayOfWeek.valueOf(dayOfWeek));
    }

    public String dayName(DayOfWeek d) {
        return dayName(LocalDate.now().with(ChronoField.DAY_OF_WEEK, d.getValue()));
    }

    /** Hijri dates are no longer shown (v3): returns the plain YYYY-MM-DD date. */
    public String hijri(LocalDate d) {
        return date(d);
    }

    public String hijri(LocalDateTime d) {
        return date(d);
    }

    /** Kurdish solar dates are no longer shown (v3): returns the plain YYYY-MM-DD date. */
    public String kurdish(LocalDate d) {
        return date(d);
    }

    public String kurdish(LocalDateTime d) {
        return date(d);
    }

    /**
     * Plain YYYY-MM-DD date. The Hijri / Kurdish additions and the dates.showHijri / dates.showKurdish
     * settings are no-ops since v3.
     */
    public String full(LocalDate d) {
        return date(d);
    }

    public String full(LocalDateTime d) {
        return date(d);
    }

    public LocalDate today() {
        return LocalDate.now();
    }

    public String minutes(Long m) {
        if (m == null) {
            return "-";
        }
        long abs = Math.abs(m);
        String s = abs >= 60 ? (abs / 60) + "h " + (abs % 60) + "m" : abs + "m";
        return m < 0 ? "-" + s : s;
    }

    /** Standard Gregorian to Solar Hijri (Jalali) conversion. Returns {year, month, day}. */
    static int[] gregorianToSolar(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        long days = 355666 + (365L * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1];
        long jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm;
        int jd;
        if (days < 186) {
            jm = (int) (1 + days / 31);
            jd = (int) (1 + days % 31);
        } else {
            jm = (int) (7 + (days - 186) / 30);
            jd = (int) (1 + (days - 186) % 30);
        }
        return new int[]{(int) jy, jm, jd};
    }
}
