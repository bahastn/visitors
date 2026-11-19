package krd.parliament.vms.repo;

import krd.parliament.vms.domain.Visitor;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Queries take the set of office ids the current user may see (see OfficeScope). */
public interface VisitRequestRepository extends JpaRepository<VisitRequest, Long>, JpaSpecificationExecutor<VisitRequest> {

    Optional<VisitRequest> findByReferenceNumberIgnoreCase(String ref);

    Optional<VisitRequest> findByQrToken(String token);

    Optional<VisitRequest> findByPublicToken(String token);

    List<VisitRequest> findByStatusInAndOfficeIdIn(Collection<VisitStatus> statuses, Collection<Long> officeIds);

    long countByStatusInAndOfficeIdIn(Collection<VisitStatus> statuses, Collection<Long> officeIds);

    List<VisitRequest> findByVisitorOrderByCreatedAtDesc(Visitor visitor);

    List<VisitRequest> findTop12ByOfficeIdInOrderByUpdatedAtDesc(Collection<Long> officeIds);

    @Query("""
            select r from VisitRequest r
            where r.scheduledStart < :to and r.scheduledEnd > :from
              and r.status in :statuses and r.office.id in :offices
            order by r.scheduledStart
            """)
    List<VisitRequest> findScheduledBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                            @Param("statuses") Collection<VisitStatus> statuses,
                                            @Param("offices") Collection<Long> officeIds);

    @Query("""
            select r from VisitRequest r
            where r.status in :statuses and r.office.id in :offices
              and ((r.scheduledStart >= :from and r.scheduledStart < :to) or (r.checkInAt >= :from and r.checkInAt < :to))
            order by coalesce(r.scheduledStart, r.checkInAt)
            """)
    List<VisitRequest> findForDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                  @Param("statuses") Collection<VisitStatus> statuses,
                                  @Param("offices") Collection<Long> officeIds);

    /** Queue numbers are unique per day for the whole building (central reception). */
    @Query("select coalesce(max(r.queueNumber), 0) from VisitRequest r where r.queueDate = :day")
    int maxQueueNumber(@Param("day") LocalDate day);

    @Query("select r from VisitRequest r where r.createdAt >= :from and r.createdAt < :to and r.office.id in :offices")
    List<VisitRequest> findCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                          @Param("offices") Collection<Long> officeIds);

    @Query("""
            select r from VisitRequest r
            where r.status = krd.parliament.vms.domain.VisitStatus.SCHEDULED
              and r.reminderSent = false and r.scheduledStart between :from and :to
            """)
    List<VisitRequest> findNeedingReminder(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select r from VisitRequest r join r.visitor v
            where r.office.id in :offices and (
                lower(r.referenceNumber) like lower(concat('%', :q, '%'))
                or lower(r.subject) like lower(concat('%', :q, '%'))
                or lower(concat(coalesce(v.firstName,''),' ',coalesce(v.fatherName,''),' ',coalesce(v.familyName,''))) like lower(concat('%', :q, '%'))
                or v.phone like concat('%', :q, '%'))
            order by r.createdAt desc
            """)
    List<VisitRequest> quickSearch(@Param("q") String q, @Param("offices") Collection<Long> officeIds,
                                   org.springframework.data.domain.Pageable pageable);
    /** Awesome bar: like {@link #quickSearch} but fetches visitor and office in the same query (no N+1). */
    @Query("""
            select r from VisitRequest r join fetch r.visitor v join fetch r.office o
            where o.id in :offices and (
                lower(r.referenceNumber) like lower(concat('%', :q, '%'))
                or lower(coalesce(r.subject,'')) like lower(concat('%', :q, '%'))
                or lower(concat(coalesce(v.firstName,''),' ',coalesce(v.fatherName,''),' ',coalesce(v.familyName,''))) like lower(concat('%', :q, '%'))
                or v.phone like concat('%', :q, '%'))
            order by r.createdAt desc
            """)
    List<VisitRequest> searchWithVisitor(@Param("q") String q, @Param("offices") Collection<Long> officeIds,
                                         org.springframework.data.domain.Pageable pageable);

    /** App home "Expected": visits still scheduled to start in [from, to). */
    @Query("""
            select count(r) from VisitRequest r
            where r.status = krd.parliament.vms.domain.VisitStatus.SCHEDULED
              and r.scheduledStart >= :from and r.scheduledStart < :to and r.office.id in :offices
            """)
    long countScheduledStartBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                    @Param("offices") Collection<Long> officeIds);

    /**
     * Minimal rows to count overdue requests exactly like {@link VisitRequest#isOverdue(int)} with each
     * office's own overdue days: [createdAt, decidedAt, office.overdueDays].
     */
    @Query("""
            select r.createdAt, r.decidedAt, o.overdueDays from VisitRequest r join r.office o
            where r.status in :statuses and o.id in :offices
            """)
    List<Object[]> findAgeRows(@Param("statuses") Collection<VisitStatus> statuses,
                               @Param("offices") Collection<Long> officeIds);
}
