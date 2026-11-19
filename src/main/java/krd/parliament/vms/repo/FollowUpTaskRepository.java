package krd.parliament.vms.repo;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.ConstituentCase;
import krd.parliament.vms.domain.Enums.TaskStatus;
import krd.parliament.vms.domain.FollowUpTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, Long> {

    List<FollowUpTask> findByOwnerAndStatusInOrderByDueDateAsc(AppUser owner, Collection<TaskStatus> statuses);

    List<FollowUpTask> findByStatusInAndOfficeIdInOrderByDueDateAsc(Collection<TaskStatus> statuses, Collection<Long> officeIds);

    List<FollowUpTask> findByOfficeIdInOrderByCreatedAtDesc(Collection<Long> officeIds);

    List<FollowUpTask> findByConstituentCaseOrderByDueDateAsc(ConstituentCase c);
}
