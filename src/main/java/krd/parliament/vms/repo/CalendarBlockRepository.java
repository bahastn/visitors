package krd.parliament.vms.repo;

import krd.parliament.vms.domain.CalendarBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CalendarBlockRepository extends JpaRepository<CalendarBlock, Long> {

    @Query("select b from CalendarBlock b where b.startAt < :to and b.endAt > :from and b.office.id in :offices order by b.startAt")
    List<CalendarBlock> findOverlapping(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                        @Param("offices") Collection<Long> officeIds);
}
