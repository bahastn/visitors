package krd.parliament.vms.util;

/** Phone number normalisation for Iraqi / Kurdistan numbers (07xx -> +9647xx). */
public final class Phones {

    private Phones() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        // Eastern Arabic / Persian digits first, then strip spaces and punctuation
        String p = toWesternDigits(phone).replaceAll("[^0-9+]", "");
        if (p.startsWith("00")) {
            p = "+" + p.substring(2);
        }
        if (p.startsWith("07") && p.length() == 11) {
            p = "+964" + p.substring(1);
        } else if (p.startsWith("7") && p.length() == 10) {
            p = "+964" + p;
        } else if (p.startsWith("964")) {
            p = "+" + p;
        }
        return p;
    }

    public static String toWesternDigits(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {
                sb.append((char) ('0' + (c - '۰')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Digits only, international form without "+", as required by WhatsApp Cloud API. */
    public static String forWhatsapp(String phone) {
        String n = normalize(phone);
        return n == null ? null : n.replace("+", "");
    }
}
