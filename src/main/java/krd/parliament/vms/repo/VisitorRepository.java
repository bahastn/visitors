package krd.parliament.vms.repo;

import krd.parliament.vms.domain.Visitor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {

    @Query("""
            select v from Visitor v
            where (:q = ''
                or lower(concat(coalesce(v.firstName,''),' ',coalesce(v.fatherName,''),' ',coalesce(v.grandfatherName,''),' ',coalesce(v.familyName,''))) like lower(concat('%', :q, '%'))
                or lower(coalesce(v.nameLatin,'')) like lower(concat('%', :q, '%'))
                or v.phone like concat('%', :q, '%')
                or coalesce(v.phoneSecondary,'') like concat('%', :q, '%')
                or lower(coalesce(v.email,'')) like lower(concat('%', :q, '%'))
                or lower(coalesce(v.organization,'')) like lower(concat('%', :q, '%'))
                or coalesce(v.idNumberHash,'') = :idHash)
            order by v.updatedAt desc
            """)
    Page<Visitor> search(@Param("q") String q, @Param("idHash") String idHash, Pageable pageable);

    List<Visitor> findByIdNumberHash(String hash);

    List<Visitor> findByPhone(String phone);
}
