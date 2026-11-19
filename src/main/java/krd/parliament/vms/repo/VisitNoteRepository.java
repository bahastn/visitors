package krd.parliament.vms.repo;

import krd.parliament.vms.domain.VisitNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitNoteRepository extends JpaRepository<VisitNote, Long> {
}
