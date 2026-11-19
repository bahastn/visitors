package krd.parliament.vms.repo;

import krd.parliament.vms.domain.ConstituentCase;
import krd.parliament.vms.domain.Enums.CaseStatus;
import krd.parliament.vms.domain.Visitor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConstituentCaseRepository extends JpaRepository<ConstituentCase, Long> {

    List<ConstituentCase> findByOfficeIdInOrderByUpdatedAtDesc(Collection<Long> officeIds);

    List<ConstituentCase> findByVisitorAndOfficeIdInOrderByCreatedAtDesc(Visitor visitor, Collection<Long> officeIds);

    List<ConstituentCase> findByVisitorAndStatusInAndOfficeIdIn(Visitor visitor, Collection<CaseStatus> statuses,
                                                                Collection<Long> officeIds);
    Optional<ConstituentCase> findByCaseNumberIgnoreCase(String caseNumber);

    /** Awesome bar: cases of the given offices by number, title, visitor name or phone (visitor + office fetched). */
    @Query("""
            select c from ConstituentCase c join fetch c.visitor v join fetch c.office o
            where o.id in :offices and (
                lower(coalesce(c.caseNumber,'')) like lower(concat('%', :q, '%'))
                or lower(coalesce(c.title,'')) like lower(concat('%', :q, '%'))
                or lower(concat(coalesce(v.firstName,''),' ',coalesce(v.fatherName,''),' ',coalesce(v.familyName,''))) like lower(concat('%', :q, '%'))
                or v.phone like concat('%', :q, '%'))
            order by c.updatedAt desc
            """)
    List<ConstituentCase> quickSearch(@Param("q") String q, @Param("offices") Collection<Long> officeIds, Pageable pageable);
}
