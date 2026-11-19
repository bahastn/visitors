package krd.parliament.vms.repo;

import krd.parliament.vms.domain.Feedback;
import krd.parliament.vms.domain.VisitRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Optional<Feedback> findByRequest(VisitRequest request);

    List<Feedback> findByRequestIn(List<VisitRequest> requests);
}
