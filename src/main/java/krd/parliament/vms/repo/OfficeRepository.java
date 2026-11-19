package krd.parliament.vms.repo;

import krd.parliament.vms.domain.Office;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficeRepository extends JpaRepository<Office, Long> {

    List<Office> findAllByOrderBySortOrderAscNameCkbAsc();

    List<Office> findByActiveTrueAndExternalFalseOrderBySortOrderAscNameCkbAsc();

    List<Office> findByActiveTrueOrderBySortOrderAscNameCkbAsc();

    List<Office> findByActiveTrueAndExternalFalseAndAcceptsPublicRequestsTrueOrderBySortOrderAscNameCkbAsc();

    Optional<Office> findByCodeIgnoreCase(String code);

    Optional<Office> findByApiKeyAndActiveTrue(String apiKey);
}
