package krd.parliament.vms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.domain.VisitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4: linking with other offices in parliament. When a request is referred to an office
 * that runs a compatible system, the referral is pushed to its API as JSON.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public static Map<String, Object> toPayload(VisitRequest r, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceReference", r.getReferenceNumber());
        m.put("sourceOffice", r.getOffice() == null ? null : r.getOffice().getCode());
        m.put("subject", r.getSubject());
        m.put("description", r.getDescription());
        m.put("purpose", r.getPurpose().name());
        m.put("priority", r.getPriority().name());
        m.put("note", note);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("firstName", r.getVisitor().getFirstName());
        v.put("fatherName", r.getVisitor().getFatherName());
        v.put("grandfatherName", r.getVisitor().getGrandfatherName());
        v.put("familyName", r.getVisitor().getFamilyName());
        v.put("phone", r.getVisitor().getPhone());
        v.put("email", r.getVisitor().getEmail());
        v.put("organization", r.getVisitor().getOrganization());
        v.put("governorate", r.getVisitor().getGovernorate() == null ? null : r.getVisitor().getGovernorate().name());
        v.put("city", r.getVisitor().getCity());
        v.put("preferredLanguage", r.getVisitor().getPreferredLanguage() == null ? null : r.getVisitor().getPreferredLanguage().name());
        m.put("visitor", v);
        return m;
    }

    @Async
    public void push(Office office, Map<String, Object> payload) {
        if (office.getEndpointUrl() == null || office.getEndpointUrl().isBlank()) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(office.getEndpointUrl()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-API-KEY", office.getApiKey() == null ? "" : office.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Referral {} pushed to {}: HTTP {}", payload.get("sourceReference"), office.getCode(), res.statusCode());
        } catch (Exception e) {
            log.warn("Referral push to {} failed: {}", office.getCode(), e.getMessage());
        }
    }
}
