package krd.parliament.vms.repo;

import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Enums.NotificationChannel;
import krd.parliament.vms.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByRecipientUserAndReadFlagFalse(AppUser user);

    List<Notification> findTop8ByRecipientUserOrderByCreatedAtDesc(AppUser user);

    Page<Notification> findByRecipientUserOrderByCreatedAtDesc(AppUser user, Pageable pageable);

    Page<Notification> findByChannelNotOrderByCreatedAtDesc(NotificationChannel channel, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.readFlag = true where n.recipientUser = :u and n.readFlag = false")
    int markAllRead(@Param("u") AppUser user);
}
