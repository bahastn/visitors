package krd.parliament.vms.repo;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByCalendarToken(String token);

    List<AppUser> findByRoleInAndEnabledTrue(Collection<Role> roles);

    List<AppUser> findAllByOrderByFullNameAsc();

    boolean existsByUsernameIgnoreCase(String username);

    /** Staff assigned to an office (main or additional office) with one of the roles. */
    @Query("""
            select distinct u from AppUser u left join u.extraOffices x
            where u.enabled = true and u.role in :roles and (u.office = :office or x = :office)
            """)
    List<AppUser> findOfficeStaff(@Param("roles") Collection<Role> roles, @Param("office") Office office);

    /** Staff who can work on an office: assigned to it, or working for all offices. */
    @Query("""
            select distinct u from AppUser u left join u.extraOffices x
            where u.enabled = true and u.role in :roles
              and (u.allOffices = true or u.office = :office or x = :office)
            """)
    List<AppUser> findWithAccess(@Param("roles") Collection<Role> roles, @Param("office") Office office);
}
