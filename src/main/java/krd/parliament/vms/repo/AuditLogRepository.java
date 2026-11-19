package krd.parliament.vms.repo;

import krd.parliament.vms.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampAsc(String entityType, Long entityId);

    List<AuditLog> findByEntityTypeAndEntityIdAndPublicEventTrueOrderByTimestampAsc(String entityType, Long entityId);

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
