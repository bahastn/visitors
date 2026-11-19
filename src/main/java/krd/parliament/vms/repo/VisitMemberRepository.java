package krd.parliament.vms.repo;

import krd.parliament.vms.domain.VisitMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitMemberRepository extends JpaRepository<VisitMember, Long> {
}
