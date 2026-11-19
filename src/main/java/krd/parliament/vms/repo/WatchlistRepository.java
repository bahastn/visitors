package krd.parliament.vms.repo;

import krd.parliament.vms.domain.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {

    List<WatchlistEntry> findByActiveTrue();

    List<WatchlistEntry> findAllByOrderByActiveDescCreatedAtDesc();
}
