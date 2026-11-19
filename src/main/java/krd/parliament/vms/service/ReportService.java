package krd.parliament.vms.service;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.repo.FeedbackRepository;
import krd.parliament.vms.repo.VisitRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Statistics for the dashboard and reports, for one office or several (parliament-wide). */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Set<VisitStatus> APPROVED_LIKE = EnumSet.of(VisitStatus.APPROVED, VisitStatus.SCHEDULED,
            VisitStatus.CHECKED_IN, VisitStatus.IN_MEETING, VisitStatus.COMPLETED, VisitStatus.NO_SHOW);

    /** One row of the per-office comparison table. */
    public record OfficeRow(Office office, long total, long completed, long pending, long rejected,
                            double approvalRate, double avgWaitDays) {
    }

    public static class Report {
        public LocalDate from;
        public LocalDate to;
        public List<VisitRequest> requests = new ArrayList<>();
        public long total;
        public long completed;
        public long approved;
        public long rejected;
        public long referred;
        public long pending;
        public long noShow;
        public double approvalRate;
        public double rejectionRate;
        public double noShowRate;
        public double avgWaitDays;
        public double avgLobbyMinutes;
        public double avgMeetingMinutes;
        public double avgRating;
        public long feedbackCount;
        public long delegationVisits;
        public long totalAttendees;
        public Map<VisitStatus, Long> byStatus = new LinkedHashMap<>();
        public Map<Enums.VisitPurpose, Long> byPurpose = new LinkedHashMap<>();
        public Map<Enums.Governorate, Long> byGovernorate = new LinkedHashMap<>();
        public Map<Enums.OrganizationType, Long> byOrgType = new LinkedHashMap<>();
        public Map<Enums.RequestChannel, Long> byChannel = new LinkedHashMap<>();
        public Map<Priority, Long> byPriority = new LinkedHashMap<>();
        public Map<Enums.Gender, Long> byGender = new LinkedHashMap<>();
        public Map<String, Long> topOrganizations = new LinkedHashMap<>();
        public Map<String, Long> byManager = new LinkedHashMap<>();
        public Map<String, Long> byOffice = new LinkedHashMap<>();
        public List<OfficeRow> officeRows = new ArrayList<>();
        public Map<String, Long> visitsPerDay = new LinkedHashMap<>();
        public Map<String, Long> requestsPerDay = new LinkedHashMap<>();
        public Map<String, Long> visitsPerWeek = new LinkedHashMap<>();
        public Map<String, Long> visitsPerMonth = new LinkedHashMap<>();

        public LocalDate getFrom() { return from; }
        public LocalDate getTo() { return to; }
        public List<VisitRequest> getRequests() { return requests; }
        public long getTotal() { return total; }
        public long getCompleted() { return completed; }
        public long getApproved() { return approved; }
        public long getRejected() { return rejected; }
        public long getReferred() { return referred; }
        public long getPending() { return pending; }
        public long getNoShow() { return noShow; }
        public double getApprovalRate() { return approvalRate; }
        public double getRejectionRate() { return rejectionRate; }
        public double getNoShowRate() { return noShowRate; }
        public double getAvgWaitDays() { return avgWaitDays; }
        public double getAvgLobbyMinutes() { return avgLobbyMinutes; }
        public double getAvgMeetingMinutes() { return avgMeetingMinutes; }
        public double getAvgRating() { return avgRating; }
        public long getFeedbackCount() { return feedbackCount; }
        public long getDelegationVisits() { return delegationVisits; }
        public long getTotalAttendees() { return totalAttendees; }
        public Map<VisitStatus, Long> getByStatus() { return byStatus; }
        public Map<Enums.VisitPurpose, Long> getByPurpose() { return byPurpose; }
        public Map<Enums.Governorate, Long> getByGovernorate() { return byGovernorate; }
        public Map<Enums.OrganizationType, Long> getByOrgType() { return byOrgType; }
        public Map<Enums.RequestChannel, Long> getByChannel() { return byChannel; }
        public Map<Priority, Long> getByPriority() { return byPriority; }
        public Map<Enums.Gender, Long> getByGender() { return byGender; }
        public Map<String, Long> getTopOrganizations() { return topOrganizations; }
        public Map<String, Long> getByManager() { return byManager; }
        public Map<String, Long> getByOffice() { return byOffice; }
        public List<OfficeRow> getOfficeRows() { return officeRows; }
        public Map<String, Long> getVisitsPerDay() { return visitsPerDay; }
        public Map<String, Long> getRequestsPerDay() { return requestsPerDay; }
        public Map<String, Long> getVisitsPerWeek() { return visitsPerWeek; }
        public Map<String, Long> getVisitsPerMonth() { return visitsPerMonth; }
    }

    private final VisitRequestRepository repo;
    private final FeedbackRepository feedback;

    public ReportService(VisitRequestRepository repo, FeedbackRepository feedback) {
        this.repo = repo;
        this.feedback = feedback;
    }

    public Report build(LocalDate from, LocalDate to, Collection<Long> officeIds) {
        Report rep = new Report();
        rep.from = from;
        rep.to = to;
        List<VisitRequest> list = new ArrayList<>(repo.findCreatedBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), officeIds));
        list.sort(Comparator.comparing(VisitRequest::getCreatedAt));
        rep.requests = list;
        rep.total = list.size();

        for (VisitStatus s : VisitStatus.values()) {
            rep.byStatus.put(s, list.stream().filter(r -> r.getStatus() == s).count());
        }
        rep.completed = rep.byStatus.get(VisitStatus.COMPLETED);
        rep.noShow = rep.byStatus.get(VisitStatus.NO_SHOW);
        rep.rejected = rep.byStatus.get(VisitStatus.REJECTED);
        rep.referred = rep.byStatus.get(VisitStatus.REFERRED);
        rep.approved = list.stream().filter(r -> APPROVED_LIKE.contains(r.getStatus())).count();
        rep.pending = list.stream().filter(r -> r.getStatus().isPending()).count();
        long decided = rep.approved + rep.rejected + rep.referred;
        rep.approvalRate = pct(rep.approved, decided);
        rep.rejectionRate = pct(rep.rejected, decided);
        rep.noShowRate = pct(rep.noShow, rep.completed + rep.noShow);
        rep.avgWaitDays = avgWait(list);
        rep.avgLobbyMinutes = round(list.stream().filter(r -> r.getCheckInAt() != null && r.getMeetingStartAt() != null)
                .mapToLong(VisitRequest::getLobbyWaitMinutes).average().orElse(0));
        rep.avgMeetingMinutes = round(list.stream().map(VisitRequest::getActualMeetingMinutes).filter(Objects::nonNull)
                .mapToLong(Long::longValue).average().orElse(0));
        rep.delegationVisits = list.stream().filter(r -> r.getExpectedAttendees() > 1 || !r.getMembers().isEmpty()).count();
        rep.totalAttendees = list.stream().filter(r -> r.getStatus() == VisitStatus.COMPLETED)
                .mapToLong(r -> 1 + r.getMembers().size()).sum();

        rep.byPurpose = count(list, VisitRequest::getPurpose);
        rep.byGovernorate = count(list, r -> r.getVisitor().getGovernorate());
        rep.byOrgType = count(list, r -> r.getVisitor().getOrganizationType());
        rep.byChannel = count(list, VisitRequest::getChannel);
        rep.byPriority = count(list, VisitRequest::getPriority);
        rep.byGender = count(list, r -> r.getVisitor().getGender());
        rep.topOrganizations = top(count(list, r -> blankToNull(r.getVisitor().getOrganization())), 10);
        rep.byManager = top(count(list, r -> r.getManagedBy() == null ? null : r.getManagedBy().getFullName()), 20);
        rep.byOffice = count(list, r -> r.getOffice().getName());

        Map<Office, List<VisitRequest>> perOffice = list.stream()
                .collect(Collectors.groupingBy(VisitRequest::getOffice, LinkedHashMap::new, Collectors.toList()));
        perOffice.forEach((o, l) -> {
            long appr = l.stream().filter(r -> APPROVED_LIKE.contains(r.getStatus())).count();
            long rej = l.stream().filter(r -> r.getStatus() == VisitStatus.REJECTED).count();
            long refd = l.stream().filter(r -> r.getStatus() == VisitStatus.REFERRED).count();
            rep.officeRows.add(new OfficeRow(o, l.size(),
                    l.stream().filter(r -> r.getStatus() == VisitStatus.COMPLETED).count(),
                    l.stream().filter(r -> r.getStatus().isPending()).count(),
                    rej, pct(appr, appr + rej + refd), avgWait(l)));
        });
        rep.officeRows.sort(Comparator.comparingLong(OfficeRow::total).reversed());

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            rep.visitsPerDay.put(d.toString(), 0L);
            rep.requestsPerDay.put(d.toString(), 0L);
        }
        for (VisitRequest r : list) {
            rep.requestsPerDay.merge(r.getCreatedAt().toLocalDate().toString(), 1L, Long::sum);
        }
        for (VisitRequest r : repo.findForDay(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                EnumSet.of(VisitStatus.COMPLETED, VisitStatus.IN_MEETING, VisitStatus.CHECKED_IN), officeIds)) {
            LocalDateTime when = r.getMeetingStartAt() != null ? r.getMeetingStartAt()
                    : r.getCheckInAt() != null ? r.getCheckInAt() : r.getScheduledStart();
            if (when == null) {
                continue;
            }
            LocalDate d = when.toLocalDate();
            rep.visitsPerDay.merge(d.toString(), 1L, Long::sum);
            rep.visitsPerWeek.merge(d.getYear() + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)), 1L, Long::sum);
            rep.visitsPerMonth.merge(d.getYear() + "-" + String.format("%02d", d.getMonthValue()), 1L, Long::sum);
        }
        rep.visitsPerWeek = new TreeMap<>(rep.visitsPerWeek);
        rep.visitsPerMonth = new TreeMap<>(rep.visitsPerMonth);

        List<Feedback> fb = list.isEmpty() ? List.of() : feedback.findByRequestIn(list);
        rep.feedbackCount = fb.size();
        rep.avgRating = round(fb.stream().mapToInt(Feedback::getRating).average().orElse(0));
        return rep;
    }

    private static double avgWait(List<VisitRequest> list) {
        return round(list.stream()
                .filter(r -> r.getScheduledStart() != null || r.getMeetingStartAt() != null)
                .mapToDouble(r -> ChronoUnit.HOURS.between(r.getCreatedAt(),
                        r.getMeetingStartAt() != null ? r.getMeetingStartAt() : r.getScheduledStart()) / 24.0)
                .average().orElse(0));
    }

    private static <K> Map<K, Long> count(List<VisitRequest> list, Function<VisitRequest, K> key) {
        Map<K, Long> m = list.stream().map(key).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return m.entrySet().stream().sorted(Map.Entry.<K, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static <K> Map<K, Long> top(Map<K, Long> m, int n) {
        return m.entrySet().stream().limit(n)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private static double pct(long a, long b) {
        return b == 0 ? 0 : round(100.0 * a / b);
    }

    private static double round(double d) {
        return Math.round(d * 10) / 10.0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
