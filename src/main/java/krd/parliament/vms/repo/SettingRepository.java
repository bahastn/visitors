package krd.parliament.vms.repo;

import krd.parliament.vms.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<SystemSetting, String> {
}
