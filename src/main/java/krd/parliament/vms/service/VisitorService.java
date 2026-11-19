package krd.parliament.vms.service;

import krd.parliament.vms.domain.Visitor;
import krd.parliament.vms.domain.WatchlistEntry;
import krd.parliament.vms.repo.VisitorRepository;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.util.Phones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class VisitorService {

    private final VisitorRepository repo;
    private final CryptoService crypto;
    private final WatchlistService watchlist;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public VisitorService(VisitorRepository repo, CryptoService crypto, WatchlistService watchlist,
                          AuditService audit, CurrentUser currentUser) {
        this.repo = repo;
        this.crypto = crypto;
        this.watchlist = watchlist;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public Page<Visitor> search(String q, Pageable p) {
        String query = q == null ? "" : q.trim();
        String hash = query.isEmpty() ? "-" : crypto.hash(query);
        return repo.search(query, hash == null ? "-" : hash, p);
    }

    public Visitor get(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Visitor " + id));
    }

    /** Possible duplicates by phone or ID number, to avoid registering the same person twice. */
    public List<Visitor> findDuplicates(Visitor v) {
        Set<Visitor> res = new LinkedHashSet<>();
        if (v.getPhone() != null) {
            res.addAll(repo.findByPhone(Phones.normalize(v.getPhone())));
        }
        String h = crypto.hash(v.getIdNumber());
        if (h != null) {
            res.addAll(repo.findByIdNumberHash(h));
        }
        res.removeIf(x -> x.getId().equals(v.getId()));
        return List.copyOf(res);
    }

    @Transactional
    public Visitor save(Visitor v) {
        boolean isNew = v.getId() == null;
        v.setPhone(Phones.normalize(v.getPhone()));
        v.setPhoneSecondary(blankToNull(Phones.normalize(v.getPhoneSecondary())));
        v.setWhatsappNumber(blankToNull(Phones.normalize(v.getWhatsappNumber())));
        v.setEmergencyContactPhone(blankToNull(Phones.normalize(v.getEmergencyContactPhone())));
        v.setEmail(blankToNull(v.getEmail()));
        v.setIdNumber(blankToNull(v.getIdNumber()));
        v.setIdNumberHash(crypto.hash(v.getIdNumber()));
        if (isNew) {
            v.setCreatedBy(currentUser.get());
        }
        Visitor saved = repo.save(v);
        audit.log(AuditService.VISITOR, saved.getId(), isNew ? "VISITOR_CREATED" : "VISITOR_UPDATED", saved.getFullName());
        List<WatchlistEntry> hits = watchlist.match(saved);
        if (!hits.isEmpty()) {
            watchlist.alert(saved.getShortName(), hits, null, WatchlistService.STAGE_REGISTERED);
        }
        return saved;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
