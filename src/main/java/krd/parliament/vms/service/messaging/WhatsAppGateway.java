package krd.parliament.vms.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import krd.parliament.vms.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Meta WhatsApp Business Cloud API.
 * Note: messages to a visitor who has not written to you in the last 24 hours must use an
 * approved template; configure templates in Meta Business Manager for production use.
 */
@Component
public class WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppGateway.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;
    @Value("${app.whatsapp.access-token:}")
    private String token;
    @Value("${app.whatsapp.api-version:v21.0}")
    private String version;

    public boolean isConfigured() {
        return phoneNumberId != null && !phoneNumberId.isBlank() && token != null && !token.isBlank();
    }

    public boolean send(String to, String message) throws Exception {
        if (!isConfigured()) {
            log.info("[WhatsApp not configured] to={} : {}", to, message);
            return false;
        }
        String body = json.writeValueAsString(Map.of(
                "messaging_product", "whatsapp",
                "to", Phones.forWhatsapp(to),
                "type", "text",
                "text", Map.of("preview_url", true, "body", message)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(
                        "https://graph.facebook.com/" + version + "/" + phoneNumberId + "/messages"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("WhatsApp API HTTP " + res.statusCode() + ": " + res.body());
        }
        return true;
    }
}
