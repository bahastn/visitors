package krd.parliament.vms.security;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** RFC 6238 time-based one-time passwords (Google Authenticator, Microsoft Authenticator...). */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final int PERIOD = 30;
    private final SecureRandom random = new SecureRandom();

    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return new Base32().encodeToString(bytes).replace("=", "");
    }

    public String otpAuthUri(String issuer, String account, String secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&digits=" + DIGITS + "&period=" + PERIOD;
    }

    /** Accepts the current code and one step before / after to tolerate clock drift. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{6}")) {
            return false;
        }
        long step = System.currentTimeMillis() / 1000 / PERIOD;
        for (long i = -1; i <= 1; i++) {
            if (generate(secret, step + i).equals(c)) {
                return true;
            }
        }
        return false;
    }

    String generate(String secret, long step) {
        try {
            byte[] key = new Base32().decode(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0xf;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
