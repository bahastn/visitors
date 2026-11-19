package krd.parliament.vms.security;

import jakarta.servlet.http.HttpSession;
import krd.parliament.vms.domain.AppUser;
import krd.parliament.vms.domain.Office;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.repo.OfficeRepository;
import krd.parliament.vms.service.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/**
 * Which offices the logged-in user can see, and which one is selected in the office switcher.
 * Exposed to templates as {@code @scope}. "All my offices" is the default selection.
 */
@Component("scope")
public class OfficeScope {

    private static final String SESSION_KEY = "selectedOfficeId";

    private final CurrentUser currentUser;
    private final OfficeRepository offices;

    public OfficeScope(CurrentUser currentUser, OfficeRepository offices) {
        this.currentUser = currentUser;
        this.offices = offices;
    }

    /** Internal (non-external) offices the user may work on. */
    public List<Office> accessible() {
        AppUser u = currentUser.get();
        return u == null ? List.of() : allOfficesFor(u);
    }

    /** Offices a given user may work on (also used without a web session, e.g. calendar feeds). */
    public List<Office> allOfficesFor(AppUser u) {
        List<Office> all = offices.findByActiveTrueAndExternalFalseOrderBySortOrderAscNameCkbAsc();
        if (u.isAllOffices() || u.getRole() == Role.ADMIN) {
            return all;
        }
        return all.stream().filter(u::canAccess).toList();
    }

    public boolean isMultiOffice() {
        return accessible().size() > 1;
    }

    /** The office chosen in the switcher, or null for "all my offices". */
    public Office current() {
        Long id = selectedId();
        if (id == null) {
            List<Office> acc = accessible();
            return acc.size() == 1 ? acc.get(0) : null;
        }
        return accessible().stream().filter(o -> o.getId().equals(id)).findFirst().orElse(null);
    }

    public Long selectedId() {
        HttpSession s = session();
        return s == null ? null : (Long) s.getAttribute(SESSION_KEY);
    }

    public void select(Long officeId) {
        HttpSession s = session();
        if (s == null) {
            return;
        }
        if (officeId == null) {
            s.removeAttribute(SESSION_KEY);
            return;
        }
        if (accessible().stream().noneMatch(o -> o.getId().equals(officeId))) {
            throw new AccessDeniedException("Office not accessible");
        }
        s.setAttribute(SESSION_KEY, officeId);
    }

    /** Office ids used to filter every list: the selected office, or all accessible ones. */
    public Set<Long> ids() {
        Office c = current();
        if (c != null) {
            return Set.of(c.getId());
        }
        Set<Long> ids = new LinkedHashSet<>();
        accessible().forEach(o -> ids.add(o.getId()));
        if (ids.isEmpty()) {
            ids.add(-1L); // never matches; keeps "in ()" queries valid
        }
        return ids;
    }

    /** All offices the user may access, ignoring the switcher (used for permission checks). */
    public Set<Long> allIds() {
        Set<Long> ids = new LinkedHashSet<>();
        accessible().forEach(o -> ids.add(o.getId()));
        if (ids.isEmpty()) {
            ids.add(-1L);
        }
        return ids;
    }

    /** Office to use for a new record: the selected one, else the user's main office, else the first accessible. */
    public Office defaultOffice() {
        Office c = current();
        if (c != null) {
            return c;
        }
        AppUser u = currentUser.get();
        if (u != null && u.getOffice() != null && !u.getOffice().isExternal()) {
            return u.getOffice();
        }
        List<Office> acc = accessible();
        return acc.isEmpty() ? null : acc.get(0);
    }

    public Office require(Long officeId) {
        Office o = offices.findById(officeId).orElseThrow(() -> new NotFoundException("Office " + officeId));
        check(o);
        return o;
    }

    /** Throws if the current user may not work on this office's records. */
    public void check(Office o) {
        AppUser u = currentUser.get();
        if (u == null || o == null || !(u.canAccess(o))) {
            throw new AccessDeniedException("No access to this office");
        }
    }

    public boolean canAccess(Office o) {
        AppUser u = currentUser.get();
        return u != null && o != null && u.canAccess(o);
    }

    private static HttpSession session() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getSession(true);
        }
        return null;
    }
}
