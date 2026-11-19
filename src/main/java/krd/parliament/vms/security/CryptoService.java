package krd.parliament.vms.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * AES-256-GCM encryption for sensitive personal data (ID / passport numbers)
 * plus a keyed HMAC used to search and match encrypted values (watchlist, duplicates).
 */
@Component
public class CryptoService {

    private static CryptoService instance;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.crypto.key}")
    private String base64Key;

    private SecretKeySpec aesKey;
    private SecretKeySpec hmacKey;

    private static final String DEV_KEY = "q3J0b0xvY2FsRGV2S2V5Rm9yVk1TLUNoYW5nZU1lISE=";

    @PostConstruct
    void init() {
        if (base64Key == null || base64Key.isBlank()) {
            org.slf4j.LoggerFactory.getLogger(CryptoService.class)
                    .warn("app.crypto.key is not set - using the DEVELOPMENT key. Set APP_CRYPTO_KEY in production!");
            base64Key = DEV_KEY;
        }
        byte[] key = Base64.getDecoder().decode(base64Key.trim());
        if (key.length != 32) {
            throw new IllegalStateException("app.crypto.key must be 32 bytes encoded as Base64");
        }
        aesKey = new SecretKeySpec(key, "AES");
        hmacKey = new SecretKeySpec(key, "HmacSHA256");
        instance = this;
    }

    public static CryptoService get() {
        return instance;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) {
            return plain;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(128, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc);
            return "enc:" + Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith("enc:")) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(4));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[unreadable]";
        }
    }

    /** Deterministic keyed hash of a normalised value, for lookups on encrypted data. */
    public String hash(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            String norm = value.replaceAll("[\\s\\-/]", "").toUpperCase(Locale.ROOT);
            return HexFormat.of().formatHex(mac.doFinal(norm.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
