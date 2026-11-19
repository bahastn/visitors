package krd.parliament.vms.service;

import jakarta.servlet.http.HttpServletRequest;
import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.AuditLog;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.AuditLogRepository;
import krd.parliament.vms.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class AuditService {

    public static final String REQUEST = "VisitRequest";
    public static final String VISITOR = "Visitor";
    public static final String USER = "User";
    public static final String SYSTEM = "System";
    public static final String CASE = "Case";

    private final AuditLogRepository repo;
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repo, CurrentUser currentUser) {
        this.repo = repo;
        this.currentUser = currentUser;
    }

    public AuditLog log(String entityType, Long entityId, String action, String details) {
        return log(entityType, entityId, action, null, null, details, false);
    }

    public AuditLog log(String entityType, Long entityId, String action, VisitStatus from, VisitStatus to,
                        String details, boolean publicEvent) {
        AuditLog a = new AuditLog();
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setAction(action);
        a.setFromStatus(from);
        a.setToStatus(to);
        a.setDetails(details == null ? null : details.length() > 2000 ? details.substring(0, 2000) : details);
        a.setPublicEvent(publicEvent);
        AppUser u = currentUser.get();
        a.setUser(u);
        a.setActorName(u != null ? u.getFullName() : "System");
        a.setIpAddress(clientIp());
        return repo.save(a);
    }

    /**
     * Translatable details: stored as "#key|arg|arg" and shown in the reader's language.
     * An argument starting with "@" is itself a message key (e.g. an enum label).
     */
    public static String k(String key, Object... args) {
        StringBuilder sb = new StringBuilder("#").append(key);
        for (Object a : args) {
            String v;
            if (a instanceof Enum<?> e) {
                v = "@enum." + e.getDeclaringClass().getSimpleName() + "." + e.name();
            } else {
                v = a == null ? "" : String.valueOf(a);
            }
            sb.append('|').append(v.replace("|", "/"));
        }
        return sb.toString();
    }

    public List<AuditLog> trail(String entityType, Long id) {
        return repo.findByEntityTypeAndEntityIdOrderByTimestampAsc(entityType, id);
    }

    public List<AuditLog> publicTrail(String entityType, Long id) {
        return repo.findByEntityTypeAndEntityIdAndPublicEventTrueOrderByTimestampAsc(entityType, id);
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest r = sra.getRequest();
            String xf = r.getHeader("X-Forwarded-For");
            return xf != null ? xf.split(",")[0].trim() : r.getRemoteAddr();
        }
        return null;
    }
}
