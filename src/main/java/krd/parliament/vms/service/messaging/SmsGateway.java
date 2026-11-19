package krd.parliament.vms.service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Generic HTTP SMS gateway. Works with local bulk-SMS providers (Korek, Asiacell, Zain resellers)
 * that accept a URL like https://sms.example.iq/send?key={apiKey}&to={to}&from={sender}&text={message}.
 * When no URL is configured the message is only logged (status LOGGED).
 */
@Component
public class SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SmsGateway.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.sms.url:}")
    private String url;
    @Value("${app.sms.method:GET}")
    private String method;
    @Value("${app.sms.api-key:}")
    private String apiKey;
    @Value("${app.sms.sender:}")
    private String sender;

    public boolean isConfigured() {
        return url != null && !url.isBlank();
    }

    /** @return true if delivered to the provider, false if only logged. */
    public boolean send(String to, String message) throws Exception {
        if (!isConfigured()) {
            log.info("[SMS not configured] to={} : {}", to, message);
            return false;
        }
        String target = url.replace("{to}", enc(to)).replace("{message}", enc(message))
                .replace("{sender}", enc(sender)).replace("{apiKey}", enc(apiKey));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(20));
        if ("POST".equalsIgnoreCase(method)) {
            String body = "to=" + enc(to) + "&message=" + enc(message) + "&sender=" + enc(sender) + "&apiKey=" + enc(apiKey);
            b.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.GET();
        }
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("SMS gateway HTTP " + res.statusCode() + ": " + res.body());
        }
        return true;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
