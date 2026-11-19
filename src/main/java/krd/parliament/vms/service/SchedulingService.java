package krd.parliament.vms.service;

import krd.parliament.vms.domain.CalendarBlock;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.domain.VisitRequest;
import krd.parliament.vms.domain.VisitStatus;
import krd.parliament.vms.repo.CalendarBlockRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Conflict detection, working-hours checks and free-slot suggestions, per office calendar. */
@Service
public class SchedulingService {

    public record Conflict(String type, Long id, String title, LocalDateTime start, LocalDateTime end) {
    }

    public record Slot(LocalDateTime start, LocalDateTime end) {
    }

    private final VisitRequestRepository requests;
    private final CalendarBlockRepository blocks;

    public SchedulingService(VisitRequestRepository requests, CalendarBlockRepository blocks) {
        this.requests = requests;
        this.blocks = blocks;
    }

    public List<Conflict> conflicts(Office office, LocalDateTime start, LocalDateTime end, Long excludeRequestId) {
        List<Conflict> res = new ArrayList<>();
        Set<Long> ids = Set.of(office.getId());
        for (VisitRequest r : requests.findScheduledBetween(start, end,
                EnumSet.of(VisitStatus.SCHEDULED, VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING), ids)) {
            if (!Objects.equals(r.getId(), excludeRequestId)) {
                res.add(new Conflict("VISIT", r.getId(), r.getReferenceNumber() + " - " + r.getVisitor().getShortName(),
                        r.getScheduledStart(), r.getScheduledEnd()));
            }
        }
        for (CalendarBlock b : blocks.findOverlapping(start, end, ids)) {
            res.add(new Conflict("BLOCK", b.getId(), b.getTitle(), b.getStartAt(), b.getEndAt()));
        }
        return res;
    }

    public boolean withinWorkingHours(Office office, LocalDateTime start, LocalDateTime end) {
        return office.getWorkDaySet().contains(start.getDayOfWeek())
                && !start.toLocalTime().isBefore(office.getWorkStart())
                && !end.toLocalTime().isAfter(office.getWorkEnd())
                && start.toLocalDate().equals(end.toLocalDate());
    }

    public List<Slot> freeSlots(Office office, LocalDate day, int durationMinutes) {
        List<Slot> res = new ArrayList<>();
        if (!office.getWorkDaySet().contains(day.getDayOfWeek())) {
            return res;
        }
        LocalDateTime t = day.atTime(office.getWorkStart());
        LocalDateTime endOfDay = day.atTime(office.getWorkEnd());
        LocalDateTime now = LocalDateTime.now();
        while (!t.plusMinutes(durationMinutes).isAfter(endOfDay)) {
            LocalDateTime e = t.plusMinutes(durationMinutes);
            if (t.isAfter(now) && conflicts(office, t, e, null).isEmpty()) {
                res.add(new Slot(t, e));
            }
            t = t.plusMinutes(15);
        }
        return res;
    }

    public List<Slot> nextFreeSlots(Office office, int durationMinutes, int max) {
        List<Slot> res = new ArrayList<>();
        LocalDate d = LocalDate.now();
        for (int i = 0; i < 30 && res.size() < max; i++) {
            for (Slot s : freeSlots(office, d.plusDays(i), durationMinutes)) {
                if (res.isEmpty() || !s.start().isBefore(res.get(res.size() - 1).start().plusMinutes(durationMinutes))) {
                    res.add(s);
                }
                if (res.size() >= max) {
                    break;
                }
            }
        }
        return res;
    }
}
