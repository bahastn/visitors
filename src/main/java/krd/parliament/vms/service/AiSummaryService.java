package krd.parliament.vms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import krd.parliament.vms.domain.FollowUpTask;
import krd.parliament.vms.domain.VisitNote;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.util.Labels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Phase 4: AI summaries of what a visit was about. Uses the Anthropic API when a key is configured,
 * otherwise produces a structured summary from the recorded data.
 */
@Service
public class AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryService.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final Labels labels;

    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.model:claude-haiku-5-5}")
    private String model;

    public AiSummaryService(Labels labels) {
        this.labels = labels;
    }

    public boolean isAiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String summarize(VisitRequest r) {
        String facts = facts(r);
        if (isAiConfigured()) {
            try {
                return callAnthropic(facts);
            } catch (Exception e) {
                log.warn("AI summary failed, using built-in summary: {}", e.getMessage());
            }
        }
        return builtIn(r);
    }

    private String facts(VisitRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reference: ").append(r.getReferenceNumber()).append('\n');
        sb.append("Office visited: ").append(r.getOffice().name(java.util.Locale.ENGLISH)).append('\n');
        sb.append("Visitor: ").append(r.getVisitor().getFullName());
        if (r.getVisitor().getJobTitle() != null) sb.append(", ").append(r.getVisitor().getJobTitle());
        if (r.getVisitor().getOrganization() != null) sb.append(", ").append(r.getVisitor().getOrganization());
        sb.append('\n');
        if (r.getVisitor().getGovernorate() != null) sb.append("From: ").append(r.getVisitor().getGovernorate()).append(' ')
                .append(r.getVisitor().getCity() == null ? "" : r.getVisitor().getCity()).append('\n');
        sb.append("Purpose: ").append(r.getPurpose()).append('\n');
        sb.append("Subject: ").append(r.getSubject()).append('\n');
        if (r.getDescription() != null) sb.append("Description: ").append(r.getDescription()).append('\n');
        if (r.getMembers().size() > 0) sb.append("Delegation members: ").append(r.getMembers().size()).append('\n');
        if (r.getOutcomeSummary() != null) sb.append("Meeting outcome notes: ").append(r.getOutcomeSummary()).append('\n');
        for (VisitNote n : r.getNotes()) sb.append("Staff note: ").append(n.getText()).append('\n');
        for (FollowUpTask t : r.getTasks()) sb.append("Follow-up task: ").append(t.getTitle())
                .append(t.getDueDate() == null ? "" : " (due " + t.getDueDate() + ")").append('\n');
        return sb.toString();
    }

    private String callAnthropic(String facts) throws Exception {
        String lang = switch (LocaleContextHolder.getLocale().getLanguage()) {
            case "ckb" -> "Central Kurdish (Sorani, Arabic script)";
            case "ar" -> "Arabic";
            default -> "English";
        };
        String prompt = "You assist an office of the Kurdistan Parliament. "
                + "Write a short, neutral summary (max 120 words) of this visit for the office records, in " + lang + ". "
                + "Cover: who came, what they asked for, what was agreed, and any follow-up actions. "
                + "Do not invent facts that are not in the record.\n\n" + facts;
        String body = json.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", 600,
                "messages", List.of(Map.of("role", "user", "content", prompt))));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " " + res.body());
        }
        JsonNode node = json.readTree(res.body());
        StringBuilder out = new StringBuilder();
        for (JsonNode c : node.path("content")) {
            if ("text".equals(c.path("type").asText())) {
                out.append(c.path("text").asText());
            }
        }
        return out.toString().trim();
    }

    private String builtIn(VisitRequest r) {
        StringBuilder sb = new StringBuilder();
        sb.append(labels.msg("ai.who")).append(": ").append(r.getVisitor().getFullName());
        if (r.getVisitor().getOrganization() != null) sb.append(" (").append(r.getVisitor().getOrganization()).append(")");
        if (!r.getMembers().isEmpty()) sb.append(" + ").append(r.getMembers().size()).append(" ").append(labels.msg("ai.members"));
        sb.append("\n").append(labels.msg("ai.purpose")).append(": ").append(labels.t(r.getPurpose())).append(" — ").append(r.getSubject());
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            String d = r.getDescription().trim();
            sb.append("\n").append(labels.msg("ai.request")).append(": ").append(d.length() > 300 ? d.substring(0, 300) + "…" : d);
        }
        sb.append("\n").append(labels.msg("ai.status")).append(": ").append(labels.t(r.getStatus()));
        if (r.getActualMeetingMinutes() != null) sb.append(" (").append(r.getActualMeetingMinutes()).append(" min)");
        if (r.getOutcomeSummary() != null && !r.getOutcomeSummary().isBlank()) {
            sb.append("\n").append(labels.msg("ai.outcome")).append(": ").append(r.getOutcomeSummary().trim());
        }
        if (!r.getTasks().isEmpty()) {
            sb.append("\n").append(labels.msg("ai.followups")).append(":");
            for (FollowUpTask t : r.getTasks()) {
                sb.append("\n • ").append(t.getTitle());
                if (t.getOwner() != null) sb.append(" — ").append(t.getOwner().getFullName());
                if (t.getDueDate() != null) sb.append(" (").append(t.getDueDate()).append(")");
            }
        }
        return sb.toString();
    }
}
