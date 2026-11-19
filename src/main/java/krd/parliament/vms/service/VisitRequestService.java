package krd.parliament.vms.service;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.Recommendation;
import krd.parliament.vms.repo.*;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.util.Phones;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static krd.parliament.vms.service.AuditService.k;

/**
 * The visit lifecycle for every office: intake, one- or two-level approval, scheduling,
 * transfer between offices, check-in, meeting and completion. Every change is audited and
 * the right people are notified in their own language.
 */
@Service
@Transactional
public class VisitRequestService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final VisitRequestRepository repo;
    private final VisitMemberRepository members;
    private final AttachmentRepository attachments;
    private final OfficeRepository offices;
    private final UserRepository users;
    private final ConstituentCaseRepository cases;
    private final AuditService audit;
    private final NotificationService notify;
    private final WatchlistService watchlist;
    private final SchedulingService scheduling;
    private final StorageService storage;
    private final ReferralService referrals;
    private final CryptoService crypto;
    private final CurrentUser currentUser;

    public VisitRequestService(VisitRequestRepository repo, VisitMemberRepository members, AttachmentRepository attachments,
                               OfficeRepository offices, UserRepository users, ConstituentCaseRepository cases,
                               AuditService audit, NotificationService notify, WatchlistService watchlist,
                               SchedulingService scheduling, StorageService storage, ReferralService referrals,
                               CryptoService crypto, CurrentUser currentUser) {
        this.repo = repo;
        this.members = members;
        this.attachments = attachments;
        this.offices = offices;
        this.users = users;
        this.cases = cases;
        this.audit = audit;
        this.notify = notify;
        this.watchlist = watchlist;
        this.scheduling = scheduling;
        this.storage = storage;
        this.referrals = referrals;
        this.crypto = crypto;
        this.currentUser = currentUser;
    }

    /** Loads a request and, for logged-in staff, checks they may work on its office. */
    @Transactional(readOnly = true)
    public VisitRequest get(Long id) {
        VisitRequest r = repo.findById(id).orElseThrow(() -> new NotFoundException("Request " + id));
        AppUser me = currentUser.get();
        if (me != null && !me.canAccess(r.getOffice())) {
            throw new AccessDeniedException("No access to this office");
        }
        return r;
    }

    private static Object[] info(VisitRequest r, Object extra) {
        return new Object[]{r.getReferenceNumber(), r.getVisitor().getShortName(), r.getSubject(), extra == null ? "" : extra};
    }

    private static String link(VisitRequest r) {
        return "/requests/" + r.getId();
    }

    // ------------------------------------------------------------------ intake

    public VisitRequest create(VisitRequest r, Visitor visitor, Office office) {
        if (office == null || office.isExternal()) {
            throw new BusinessException("error.officeRequired");
        }
        AppUser me = currentUser.get();
        if (me != null && !me.canAccess(office)) {
            throw new AccessDeniedException("No access to this office");
        }
        r.setOffice(office);
        r.setVisitor(visitor);
        r.setStatus(VisitStatus.REQUESTED);
        r.setReferenceNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        r.setPublicToken(CryptoService.randomToken(18));
        if (r.getDurationMinutes() <= 0) {
            r.setDurationMinutes(office.getDefaultDuration());
        }
        if (r.getLocation() == null || r.getLocation().isBlank()) {
            r.setLocation(office.getDefaultLocation());
        }
        if (visitor.isVip() && r.getPriority() == Priority.NORMAL) {
            r.setPriority(Priority.VIP);
        }
        r.setCreatedBy(me);
        if (r.getManagedBy() == null && me != null && me.getRole() == Role.SECRETARY) {
            r.setManagedBy(me);
        }
        r = repo.save(r);
        r.setReferenceNumber(String.format("%s-%d-%06d", office.getCode().toUpperCase(), LocalDate.now().getYear(), r.getId()));

        List<WatchlistEntry> hits = watchlist.match(visitor);
        if (!hits.isEmpty()) {
            r.setWatchlistFlag(true);
            watchlist.alert(visitor.getShortName(), hits, r, WatchlistService.STAGE_REQUEST);
        }
        audit.log(AuditService.REQUEST, r.getId(), "REQUEST_CREATED", null, VisitStatus.REQUESTED,
                k("audit.d.created", r.getChannel(), r.getSubject()), true);
        audit.log(AuditService.VISITOR, visitor.getId(), "REQUEST_CREATED", r.getReferenceNumber() + " — " + r.getSubject());
        notify.notifyVisitor(r, "received", notify.trackLink(r), null);
        notify.notifyOfficeStaff(office, NotificationService.SCHEDULERS, "newRequest", info(r, office.getCode()), link(r), r);
        if (r.getPriority() == Priority.VIP || r.getPriority() == Priority.URGENT_HUMANITARIAN) {
            notify.notifyOfficeStaff(office, EnumSet.of(Role.HEAD), "priorityRequest",
                    info(r, NotificationService.t(r.getPriority())), link(r), r);
        }
        return r;
    }

    public VisitRequest updateDetails(Long id, VisitRequest form) {
        VisitRequest r = get(id);
        if (r.getStatus().isFinal()) {
            throw new BusinessException("error.finalStatus");
        }
        r.setChannel(form.getChannel());
        r.setPurpose(form.getPurpose());
        r.setSubject(form.getSubject());
        r.setDescription(form.getDescription());
        r.setPriority(form.getPriority());
        r.setPreferredDate(form.getPreferredDate());
        r.setPreferredTime(form.getPreferredTime());
        r.setDurationMinutes(form.getDurationMinutes());
        r.setExpectedAttendees(form.getExpectedAttendees());
        r.setNeedsInterpreter(form.isNeedsInterpreter());
        r.setAccessibilityNeeds(form.getAccessibilityNeeds());
        r.setVehiclePlate(form.getVehiclePlate());
        r.setReferredByPerson(form.getReferredByPerson());
        r.setMeetingWith(form.getMeetingWith());
        r.setLocation(form.getLocation());
        r.setManagedBy(form.getManagedBy());
        audit.log(AuditService.REQUEST, r.getId(), "REQUEST_UPDATED", null);
        return r;
    }

    public void assignManager(Long id, Long userId) {
        VisitRequest r = get(id);
        AppUser u = userId == null ? null : users.findById(userId).orElse(null);
        r.setManagedBy(u);
        audit.log(AuditService.REQUEST, id, "MANAGER_ASSIGNED", u == null ? "-" : u.getFullName());
        if (u != null) {
            notify.notifyUsers(List.of(u), "assigned", info(r, null), link(r), r);
        }
    }

    // ------------------------------------------------------------------ approval workflow

    public void startReview(Long id) {
        VisitRequest r = get(id);
        requireRole(Role.SECRETARY, Role.DIRECTOR, Role.ADMIN, Role.HEAD);
        move(r, VisitStatus.UNDER_REVIEW, "REVIEW_STARTED", null, true);
    }

    /** Level 1: the secretary recommends; the head / director decides. */
    public void recommend(Long id, Recommendation rec, String note) {
        requireRole(Role.SECRETARY, Role.DIRECTOR, Role.ADMIN);
        VisitRequest r = get(id);
        if (!r.getStatus().isPending()) {
            throw new BusinessException("error.transition");
        }
        r.setRecommendation(rec);
        r.setRecommendationNote(note);
        r.setRecommendedBy(currentUser.get());
        r.setRecommendedAt(LocalDateTime.now());
        String details = k("audit.d.recommended", rec, note == null ? "" : note);
        if (r.getStatus() != VisitStatus.UNDER_REVIEW) {
            move(r, VisitStatus.UNDER_REVIEW, "RECOMMENDED", details, true);
        } else {
            audit.log(AuditService.REQUEST, id, "RECOMMENDED", null, null, details, false);
        }
        notify.notifyOfficeStaff(r.getOffice(), NotificationService.DECIDERS, "recommended",
                info(r, NotificationService.t(rec)), link(r), r);
    }

    public void approve(Long id, String reason, LocalDateTime scheduleAt, boolean force) {
        VisitRequest r = get(id);
        requireDecider(r.getOffice());
        r.setDecidedBy(currentUser.get());
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionReason(reason);
        ensureQrToken(r);
        move(r, VisitStatus.APPROVED, "APPROVED", reason, true);
        if (scheduleAt != null) {
            schedule(id, scheduleAt, r.getDurationMinutes(), null, null, force);
        } else {
            notify.notifyVisitor(r, "approved", notify.trackLink(r), null);
            notifyManager(r, "approved", null);
        }
    }

    public int bulkApprove(List<Long> ids) {
        int n = 0;
        for (Long id : ids) {
            VisitRequest r = get(id);
            if (r.getStatus().canMoveTo(VisitStatus.APPROVED)) {
                approve(id, null, null, false);
                n++;
            }
        }
        return n;
    }

    public int bulkReject(List<Long> ids, String reason) {
        int n = 0;
        for (Long id : ids) {
            VisitRequest r = get(id);
            if (r.getStatus().canMoveTo(VisitStatus.REJECTED)) {
                reject(id, reason, false);
                n++;
            }
        }
        return n;
    }

    public void reject(Long id, String reason, boolean shareReasonWithVisitor) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("error.reasonRequired");
        }
        VisitRequest r = get(id);
        requireDecider(r.getOffice());
        r.setDecidedBy(currentUser.get());
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionReason(reason);
        move(r, VisitStatus.REJECTED, "REJECTED", reason, true);
        notify.notifyVisitor(r, "rejected", null, shareReasonWithVisitor ? reason : "");
        notifyManager(r, "rejected", null);
    }

    public void postpone(Long id, LocalDate until, String reason) {
        VisitRequest r = get(id);
        requireDecider(r.getOffice());
        r.setPostponedUntil(until);
        r.setDecisionReason(reason);
        move(r, VisitStatus.POSTPONED, "POSTPONED", k("audit.d.postponed", until == null ? "-" : until, reason == null ? "" : reason), true);
        notify.notifyVisitor(r, "postponed", notify.trackLink(r), until == null ? "" : until.toString());
    }

    /**
     * Refer the request. To another office of the parliament: the request moves to that office's
     * waiting list. To an external body: the request is closed as referred and pushed to its system.
     */
    public void refer(Long id, Long officeId, String note) {
        VisitRequest r = get(id);
        requireDecider(r.getOffice());
        Office target = offices.findById(officeId).orElseThrow(() -> new NotFoundException("Office"));
        if (target.getId().equals(r.getOffice().getId())) {
            throw new BusinessException("error.sameOffice");
        }
        Locale visitorLocale = NotificationService.localeOf(
                r.getVisitor().getPreferredLanguage() == null ? null : r.getVisitor().getPreferredLanguage().name());
        if (!target.isExternal()) {
            Office from = r.getOffice();
            VisitStatus old = r.getStatus();
            r.setReferredFrom(from);
            r.setOffice(target);
            r.setStatus(VisitStatus.REQUESTED);
            r.setRecommendation(null);
            r.setRecommendationNote(null);
            r.setRecommendedBy(null);
            r.setDecidedBy(null);
            r.setDecidedAt(null);
            r.setManagedBy(null);
            r.setScheduledStart(null);
            r.setScheduledEnd(null);
            r.setLocation(target.getDefaultLocation());
            r.setMeetingWith(null);
            repo.save(r);
            audit.log(AuditService.REQUEST, r.getId(), "TRANSFERRED", old, VisitStatus.REQUESTED,
                    k("audit.d.transferred", from.getCode(), target.getCode(), note == null ? "" : note), true);
            notify.notifyVisitor(r, "referred", notify.trackLink(r), target.name(visitorLocale));
            notify.notifyOfficeStaff(target, NotificationService.SCHEDULERS, "transferIn", info(r, from.getCode()), link(r), r);
            return;
        }
        r.setReferredTo(target);
        r.setDecidedBy(currentUser.get());
        r.setDecidedAt(LocalDateTime.now());
        r.setDecisionReason(note);
        move(r, VisitStatus.REFERRED, "REFERRED", k("audit.d.referred", target.getCode(), note == null ? "" : note), true);
        notify.notifyVisitor(r, "referred", notify.trackLink(r), target.name(visitorLocale));
        referrals.push(target, ReferralService.toPayload(r, note));
    }

    // ------------------------------------------------------------------ scheduling

    public void schedule(Long id, LocalDateTime start, int durationMinutes, String location, String meetingWith, boolean force) {
        requireRole(Role.SECRETARY, Role.DIRECTOR, Role.ADMIN, Role.HEAD);
        VisitRequest r = get(id);
        if (r.getStatus().isPending()) {
            approve(id, null, null, force);
            r = get(id);
        }
        if (!r.getStatus().canMoveTo(VisitStatus.SCHEDULED)) {
            throw new BusinessException("error.transition");
        }
        if (durationMinutes <= 0) {
            durationMinutes = r.getDurationMinutes() > 0 ? r.getDurationMinutes() : r.getOffice().getDefaultDuration();
        }
        LocalDateTime end = start.plusMinutes(durationMinutes);
        if (!force) {
            List<SchedulingService.Conflict> c = scheduling.conflicts(r.getOffice(), start, end, r.getId());
            if (!c.isEmpty()) {
                throw new BusinessException("error.conflict", c.get(0).title(),
                        c.get(0).start().toLocalTime() + "-" + c.get(0).end().toLocalTime());
            }
        }
        boolean reschedule = r.getStatus() == VisitStatus.SCHEDULED;
        LocalDateTime old = r.getScheduledStart();
        r.setScheduledStart(start);
        r.setScheduledEnd(end);
        r.setDurationMinutes(durationMinutes);
        r.setReminderSent(false);
        if (location != null && !location.isBlank()) {
            r.setLocation(location);
        }
        if (meetingWith != null && !meetingWith.isBlank()) {
            r.setMeetingWith(meetingWith);
        }
        ensureQrToken(r);
        String details = reschedule && old != null
                ? k("audit.d.rescheduled", old.format(DT), start.format(DT), durationMinutes)
                : k("audit.d.scheduled", start.format(DT), durationMinutes);
        if (force) {
            audit.log(AuditService.REQUEST, r.getId(), "CONFLICT_OVERRIDDEN", null);
        }
        move(r, VisitStatus.SCHEDULED, reschedule ? "RESCHEDULED" : "SCHEDULED", details, true);
        notify.notifyVisitor(r, reschedule ? "rescheduled" : "scheduled", notify.passLink(r), r.getLocation());
        notifyManager(r, reschedule ? "rescheduled" : "scheduled", start.format(DT));
    }

    public void cancel(Long id, String reason) {
        requireRole(Role.SECRETARY, Role.DIRECTOR, Role.ADMIN, Role.HEAD);
        VisitRequest r = get(id);
        r.setDecisionReason(reason);
        move(r, VisitStatus.CANCELLED, "CANCELLED", reason, true);
        notify.notifyVisitor(r, "cancelled", null, reason);
    }

    // ------------------------------------------------------------------ reception

    public VisitRequest findByQr(String token) {
        String t = token.trim();
        if (t.contains("/")) {
            t = t.substring(t.lastIndexOf('/') + 1);
        }
        String finalT = t;
        VisitRequest r = repo.findByQrToken(t)
                .or(() -> repo.findByReferenceNumberIgnoreCase(finalT))
                .orElseThrow(() -> new BusinessException("error.passNotFound"));
        AppUser me = currentUser.get();
        if (me != null && !me.canAccess(r.getOffice())) {
            throw new BusinessException("error.passOtherOffice");
        }
        return r;
    }

    public VisitRequest checkIn(Long id, boolean securityOverride) {
        requireRole(Role.RECEPTION, Role.SECRETARY, Role.SECURITY, Role.DIRECTOR, Role.ADMIN);
        VisitRequest r = get(id);
        if (!r.getStatus().canMoveTo(VisitStatus.CHECKED_IN)) {
            throw new BusinessException("error.transition");
        }
        List<WatchlistEntry> hits = new ArrayList<>(watchlist.match(r.getVisitor()));
        for (VisitMember m : r.getMembers()) {
            List<WatchlistEntry> mh = watchlist.match(m);
            if (!mh.isEmpty()) {
                m.setWatchlistFlag(true);
                hits.addAll(mh);
            }
        }
        if (!hits.isEmpty()) {
            r.setWatchlistFlag(true);
            watchlist.alert(r.getVisitor().getShortName(), hits, r, WatchlistService.STAGE_CHECKIN);
            if (watchlist.isBlocked(hits)) {
                boolean canOverride = currentUser.is("SECURITY", "ADMIN", "DIRECTOR");
                if (!(securityOverride && canOverride)) {
                    audit.log(AuditService.REQUEST, id, "CHECKIN_BLOCKED", null, null, null, false);
                    throw new BusinessException("error.blocked");
                }
                audit.log(AuditService.REQUEST, id, "SECURITY_OVERRIDE", null, null, null, false);
            }
        }
        LocalDate today = LocalDate.now();
        r.setQueueDate(today);
        r.setQueueNumber(repo.maxQueueNumber(today) + 1);
        r.setCheckInAt(LocalDateTime.now());
        r.getMembers().forEach(m -> m.setCheckInAt(LocalDateTime.now()));
        ensureQrToken(r);
        Long late = r.getLateMinutes();
        String details = late == null ? k("audit.d.queue", r.getQueueNumber())
                : late > 0 ? k("audit.d.queueLate", r.getQueueNumber(), late)
                : k("audit.d.queueEarly", r.getQueueNumber(), -late);
        move(r, VisitStatus.CHECKED_IN, "CHECKED_IN", details, true);
        List<AppUser> recipients = new ArrayList<>(users.findOfficeStaff(NotificationService.DECIDERS, r.getOffice()));
        if (r.getManagedBy() != null) {
            recipients.add(r.getManagedBy());
        }
        notify.notifyUsers(recipients, "arrived", info(r, r.getQueueNumber()), link(r), r);
        return r;
    }

    public void callIn(Long id) {
        requireRole(Role.RECEPTION, Role.SECRETARY, Role.DIRECTOR, Role.ADMIN, Role.HEAD);
        VisitRequest r = get(id);
        r.setMeetingStartAt(LocalDateTime.now());
        Long wait = r.getLobbyWaitMinutes();
        move(r, VisitStatus.IN_MEETING, "MEETING_STARTED", k("audit.d.waited", wait == null ? 0 : wait), true);
    }

    public void complete(Long id, String outcome, boolean sendFeedback) {
        requireRole(Role.RECEPTION, Role.SECRETARY, Role.DIRECTOR, Role.ADMIN, Role.HEAD);
        VisitRequest r = get(id);
        LocalDateTime now = LocalDateTime.now();
        if (r.getMeetingStartAt() == null) {
            r.setMeetingStartAt(r.getCheckInAt() != null ? r.getCheckInAt() : now);
        }
        r.setMeetingEndAt(now);
        r.setCheckOutAt(now);
        if (outcome != null && !outcome.isBlank()) {
            r.setOutcomeSummary(outcome);
        }
        move(r, VisitStatus.COMPLETED, "COMPLETED", outcome, true);
        if (sendFeedback) {
            notify.notifyVisitor(r, "feedback", notify.feedbackLink(r), null);
        }
    }

    public void saveOutcome(Long id, String outcome) {
        VisitRequest r = get(id);
        r.setOutcomeSummary(outcome);
        audit.log(AuditService.REQUEST, id, "OUTCOME_UPDATED", outcome);
    }

    public void noShow(Long id) {
        requireRole(Role.RECEPTION, Role.SECRETARY, Role.DIRECTOR, Role.ADMIN);
        VisitRequest r = get(id);
        move(r, VisitStatus.NO_SHOW, "NO_SHOW", null, true);
    }

    // ------------------------------------------------------------------ members, notes, attachments, cases

    public VisitMember addMember(Long id, VisitMember m) {
        VisitRequest r = get(id);
        m.setRequest(r);
        m.setPhone(Phones.normalize(m.getPhone()));
        m.setIdNumberHash(crypto.hash(m.getIdNumber()));
        List<WatchlistEntry> hits = watchlist.match(m);
        if (!hits.isEmpty()) {
            m.setWatchlistFlag(true);
            r.setWatchlistFlag(true);
            watchlist.alert(m.getFullName(), hits, r, WatchlistService.STAGE_MEMBER);
        }
        r.getMembers().add(m);
        r.setExpectedAttendees(Math.max(r.getExpectedAttendees(), r.getMembers().size() + 1));
        members.save(m);
        audit.log(AuditService.REQUEST, id, "MEMBER_ADDED", m.getFullName());
        return m;
    }

    public void removeMember(Long id, Long memberId) {
        VisitRequest r = get(id);
        r.getMembers().removeIf(m -> {
            if (m.getId().equals(memberId)) {
                audit.log(AuditService.REQUEST, id, "MEMBER_REMOVED", m.getFullName());
                return true;
            }
            return false;
        });
    }

    public void addNote(Long id, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        VisitRequest r = get(id);
        VisitNote n = new VisitNote();
        n.setRequest(r);
        n.setAuthor(currentUser.get());
        n.setText(text.trim());
        r.getNotes().add(n);
        audit.log(AuditService.REQUEST, id, "NOTE_ADDED", null);
    }

    public void addAttachment(Long id, MultipartFile file, String description) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        VisitRequest r = repo.findById(id).orElseThrow(() -> new NotFoundException("Request " + id));
        Attachment a = new Attachment();
        a.setRequest(r);
        a.setFileName(file.getOriginalFilename());
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setStoragePath(storage.store(file));
        a.setDescription(description);
        a.setUploadedBy(currentUser.get());
        r.getAttachments().add(a);
        attachments.save(a);
        audit.log(AuditService.REQUEST, id, "ATTACHMENT_ADDED", a.getFileName());
    }

    public void deleteAttachment(Long id, Long attachmentId) {
        VisitRequest r = get(id);
        r.getAttachments().removeIf(a -> {
            if (a.getId().equals(attachmentId)) {
                storage.delete(a.getStoragePath());
                audit.log(AuditService.REQUEST, id, "ATTACHMENT_DELETED", a.getFileName());
                return true;
            }
            return false;
        });
    }

    public void linkCase(Long id, Long caseId) {
        VisitRequest r = get(id);
        ConstituentCase c = caseId == null ? null : cases.findById(caseId).orElse(null);
        r.setConstituentCase(c);
        audit.log(AuditService.REQUEST, id, "CASE_LINKED", c == null ? "-" : c.getCaseNumber());
        if (c != null) {
            audit.log(AuditService.CASE, c.getId(), "VISIT_LINKED", r.getReferenceNumber());
        }
    }

    public void saveAiSummary(Long id, String summary) {
        VisitRequest r = get(id);
        r.setAiSummary(summary);
        audit.log(AuditService.REQUEST, id, "AI_SUMMARY", null);
    }

    public void markReminderSent(VisitRequest r) {
        r.setReminderSent(true);
        repo.save(r);
    }

    // ------------------------------------------------------------------ helpers

    private void move(VisitRequest r, VisitStatus to, String action, String details, boolean publicEvent) {
        VisitStatus from = r.getStatus();
        if (from != to && !from.canMoveTo(to)) {
            throw new BusinessException("error.transition");
        }
        r.setStatus(to);
        repo.save(r);
        audit.log(AuditService.REQUEST, r.getId(), action, from, to, details, publicEvent);
    }

    private void ensureQrToken(VisitRequest r) {
        if (r.getQrToken() == null) {
            r.setQrToken(CryptoService.randomToken(16));
        }
    }

    private void notifyManager(VisitRequest r, String key, Object extra) {
        if (r.getManagedBy() != null) {
            notify.notifyUsers(List.of(r.getManagedBy()), key, info(r, extra), link(r), r);
        }
    }

    /** Head / director decide; with one-level approval the secretary may decide too. */
    public boolean canDecide(Office office) {
        Role role = currentUser.role();
        if (role == null) {
            return false;
        }
        return role.canDecide() || (role == Role.SECRETARY && office != null && !office.isTwoLevelApproval());
    }

    private void requireDecider(Office office) {
        if (currentUser.role() == null) {
            throw new AccessDeniedException("Not logged in");
        }
        if (!canDecide(office)) {
            throw new BusinessException("error.needDecider");
        }
    }

    private void requireRole(Role... roles) {
        Role role = currentUser.role();
        if (role == null || !Arrays.asList(roles).contains(role)) {
            throw new BusinessException("error.notAllowed");
        }
    }
}
