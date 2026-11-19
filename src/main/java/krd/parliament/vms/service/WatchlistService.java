package krd.parliament.vms.service;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.repo.WatchlistRepository;
import krd.parliament.vms.util.Phones;
import org.springframework.stereotype.Service;

import java.util.*;

/** Matches visitors and delegation members against the parliament-wide security watchlist. */
@Service
public class WatchlistService {

    public static final String STAGE_REGISTERED = "watch.stage.registered";
    public static final String STAGE_REQUEST = "watch.stage.request";
    public static final String STAGE_CHECKIN = "watch.stage.checkin";
    public static final String STAGE_MEMBER = "watch.stage.member";

    private final WatchlistRepository repo;
    private final NotificationService notifications;
    private final UserRepository users;

    public WatchlistService(WatchlistRepository repo, NotificationService notifications, UserRepository users) {
        this.repo = repo;
        this.notifications = notifications;
        this.users = users;
    }

    public List<WatchlistEntry> match(String fullName, String idHash, String phone) {
        String name = norm(fullName);
        String ph = Phones.normalize(phone);
        List<WatchlistEntry> hits = new ArrayList<>();
        for (WatchlistEntry w : repo.findByActiveTrue()) {
            boolean hit = (idHash != null && idHash.equals(w.getIdNumberHash()))
                    || (ph != null && !ph.isBlank() && w.getPhone() != null && ph.equals(Phones.normalize(w.getPhone())))
                    || (!name.isEmpty() && name.equals(norm(w.getFullName())));
            if (hit) {
                hits.add(w);
            }
        }
        return hits;
    }

    public List<WatchlistEntry> match(Visitor v) {
        return match(v.getShortName(), v.getIdNumberHash(), v.getPhone());
    }

    public List<WatchlistEntry> match(VisitMember m) {
        return match(m.getFullName(), m.getIdNumberHash(), m.getPhone());
    }

    public boolean isBlocked(List<WatchlistEntry> hits) {
        return hits.stream().anyMatch(w -> w.getLevel() == Enums.WatchlistLevel.BLOCK);
    }

    /** Alerts security (all offices) and the director of the visited office. */
    public void alert(String personName, List<WatchlistEntry> hits, VisitRequest r, String stageKey) {
        if (hits.isEmpty()) {
            return;
        }
        String reasons = String.join("; ", hits.stream().map(WatchlistEntry::getReason).toList());
        Object[] args = {personName, NotificationService.t(stageKey), r != null ? r.getReferenceNumber() : "", reasons};
        String link = r != null ? "/requests/" + r.getId() : "/watchlist";
        List<AppUser> recipients = new ArrayList<>(users.findByRoleInAndEnabledTrue(EnumSet.of(Role.SECURITY)));
        if (r != null && r.getOffice() != null) {
            recipients.addAll(users.findOfficeStaff(EnumSet.of(Role.DIRECTOR), r.getOffice()));
        }
        notifications.notifyUsers(recipients, "watchlist", args, link, r);
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
