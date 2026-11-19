package krd.parliament.vms.config;

import krd.parliament.vms.domain.*;
import krd.parliament.vms.domain.Enums.*;
import krd.parliament.vms.repo.*;
import krd.parliament.vms.security.CryptoService;
import krd.parliament.vms.util.Phones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * First start: creates the parliament's offices, one account per role (change the passwords!) and,
 * if app.demo-data=true, sample visitors and visits so every screen has data.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final OfficeRepository offices;
    private final VisitorRepository visitors;
    private final VisitRequestRepository requests;
    private final CalendarBlockRepository blocks;
    private final WatchlistRepository watchlist;
    private final AuditLogRepository audit;
    private final FollowUpTaskRepository tasks;
    private final ConstituentCaseRepository cases;
    private final PasswordEncoder encoder;
    private final CryptoService crypto;
    private final JdbcTemplate jdbc;

    @Value("${app.demo-data:true}")
    private boolean demoData;

    public DataInitializer(UserRepository users, OfficeRepository offices, VisitorRepository visitors,
                           VisitRequestRepository requests, CalendarBlockRepository blocks, WatchlistRepository watchlist,
                           AuditLogRepository audit, FollowUpTaskRepository tasks, ConstituentCaseRepository cases,
                           PasswordEncoder encoder, CryptoService crypto, JdbcTemplate jdbc) {
        this.users = users;
        this.offices = offices;
        this.visitors = visitors;
        this.requests = requests;
        this.blocks = blocks;
        this.watchlist = watchlist;
        this.audit = audit;
        this.tasks = tasks;
        this.cases = cases;
        this.encoder = encoder;
        this.crypto = crypto;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        log.warn("First start: creating offices and default accounts. CHANGE THE PASSWORDS IMMEDIATELY.");

        Office spk = office("SPK", OfficeType.PRESIDENCY, 10, "#003049",
                "نووسینگەی سەرۆکی پەرلەمان", "مكتب رئيس البرلمان", "Office of the Speaker",
                "سەرۆکی پەرلەمان", "رئيس البرلمان", "Speaker of Parliament", "نهۆمی سێیەم - هۆڵی کۆبوونەوەی سەرۆکایەتی");
        Office dsp = office("DSP", OfficeType.PRESIDENCY, 20, "#05668d",
                "نووسینگەی جێگری سەرۆکی پەرلەمان", "مكتب نائب رئيس البرلمان", "Office of the Deputy Speaker",
                "جێگری سەرۆکی پەرلەمان", "نائب رئيس البرلمان", "Deputy Speaker", "نهۆمی سێیەم - ژووری کۆبوونەوەی ١");
        Office sec = office("SEC", OfficeType.PRESIDENCY, 30, "#373d20",
                "نووسینگەی سکرتێری پەرلەمان", "مكتب سكرتير البرلمان", "Office of the Secretary of Parliament",
                "سکرتێری پەرلەمان", "سكرتير البرلمان", "Secretary of Parliament", "نهۆمی سێیەم - ژووری ٣٠٥");
        Office sgn = office("SGN", OfficeType.SECRETARIAT_GENERAL, 40, "#9e2a2b",
                "ئەمینداریی گشتی", "الأمانة العامة", "General Secretariat",
                "ئەمینداری گشتی", "الأمين العام", "Secretary-General", "نهۆمی دووەم");
        Office leg = office("LEG", OfficeType.COMMITTEE, 50, "#f77f00",
                "لیژنەی یاسایی", "اللجنة القانونية", "Legal Committee",
                "سەرۆکی لیژنەی یاسایی", "رئيس اللجنة القانونية", "Chair of the Legal Committee", "هۆڵی لیژنەکان - ژووری ٢");
        Office hrc = office("HRC", OfficeType.COMMITTEE, 60, "#f68e5f",
                "لیژنەی مافەکانی مرۆڤ", "لجنة حقوق الإنسان", "Human Rights Committee",
                "سەرۆکی لیژنەی مافەکانی مرۆڤ", "رئيس لجنة حقوق الإنسان", "Chair of the Human Rights Committee", "هۆڵی لیژنەکان - ژووری ٥");
        Office cmp = office("CMP", OfficeType.COMMITTEE, 70, "#001d3d",
                "لیژنەی سکاڵاکان", "لجنة الشكاوى", "Complaints Committee",
                "سەرۆکی لیژنەی سکاڵاکان", "رئيس لجنة الشكاوى", "Chair of the Complaints Committee", "هۆڵی لیژنەکان - ژووری ٧");
        Office moi = office("MOI", OfficeType.EXTERNAL, 200, "#6c757d",
                "وەزارەتی ناوخۆی هەرێمی کوردستان", "وزارة الداخلية في إقليم كوردستان", "KRG Ministry of Interior",
                null, null, null, null);
        moi.setExternal(true);
        moi.setAcceptsPublicRequests(false);
        Office moe = office("MOE", OfficeType.EXTERNAL, 210, "#6c757d",
                "وەزارەتی پەروەردە", "وزارة التربية", "KRG Ministry of Education", null, null, null, null);
        moe.setExternal(true);
        moe.setAcceptsPublicRequests(false);
        dsp.setApiKey("dsp-office-key-change-me");

        AppUser admin = user("admin", "Admin@12345", "بەڕێوەبەری سیستەم", Role.ADMIN, null, "ckb", true);
        AppUser speaker = user("speaker", "Speaker@12345", "د. ئارام محەمەد", Role.HEAD, spk, "ckb", false);
        AppUser deputy = user("deputy", "Deputy@12345", "د. سۆران جەلال", Role.HEAD, dsp, "ckb", false);
        user("secparl", "Secretary@12345", "بەهار عەلی", Role.HEAD, sec, "ckb", false);
        AppUser director = user("director", "Director@12345", "کاروان ئەحمەد", Role.DIRECTOR, dsp, "ckb", false);
        user("director.spk", "Director@12345", "هەڵۆ کەریم", Role.DIRECTOR, spk, "ar", false);
        AppUser secretary = user("secretary", "Secretary@12345", "شیلان عومەر", Role.SECRETARY, dsp, "ckb", false);
        secretary.getExtraOffices().add(hrc);
        AppUser secretary2 = user("secretary2", "Secretary@12345", "دیلان ڕەشید", Role.SECRETARY, spk, "ar", false);
        user("secretary.leg", "Secretary@12345", "ئاوات حەسەن", Role.SECRETARY, leg, "ckb", false);
        user("chair.leg", "Chair@12345", "د. نیگار تاهیر", Role.HEAD, leg, "ckb", false);
        user("reception", "Reception@12345", "هێمن عەزیز", Role.RECEPTION, null, "ckb", true);
        user("security", "Security@12345", "ڕێبوار ساڵح", Role.SECURITY, null, "ckb", true);
        user("auditor", "Auditor@12345", "Audit Office", Role.AUDITOR, sgn, "en", true);

        if (demoData) {
            demo(admin, speaker, deputy, director, secretary, secretary2, spk, dsp, leg, hrc, cmp);
        }
    }

    private Office office(String code, OfficeType type, int order, String color, String ckb, String ar, String en,
                          String hostCkb, String hostAr, String hostEn, String location) {
        Office o = new Office();
        o.setCode(code);
        o.setType(type);
        o.setSortOrder(order);
        o.setColor(color);
        o.setNameCkb(ckb);
        o.setNameAr(ar);
        o.setNameEn(en);
        o.setHostTitleCkb(hostCkb);
        o.setHostTitleAr(hostAr);
        o.setHostTitleEn(hostEn);
        o.setDefaultLocation(location);
        return offices.save(o);
    }

    private AppUser user(String username, String pw, String name, Role role, Office office, String lang, boolean all) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(pw));
        u.setFullName(name);
        u.setRole(role);
        u.setOffice(office);
        u.setAllOffices(all);
        u.setPreferredLanguage(lang);
        u.setCalendarToken(CryptoService.randomToken(24));
        u.setEmail(username + "@parliament.krd");
        return users.save(u);
    }

    private void demo(AppUser admin, AppUser speaker, AppUser deputy, AppUser director, AppUser secretary,
                      AppUser secretary2, Office spk, Office dsp, Office leg, Office hrc, Office cmp) {
        Visitor v1 = visitor("ئازاد", "کەمال", "مەحمود", "بەرزنجی", Gender.MALE, "07501234567", "azad.k@example.com",
                "مامۆستا", "بەڕێوەبەرایەتی پەروەردەی هەولێر", OrganizationType.GOVERNMENT, Governorate.ERBIL, "هەولێر", "19800001234");
        Visitor v2 = visitor("نەسرین", "عەلی", "حەسەن", "جاف", Gender.FEMALE, "07701112233", "nasreen@ngo.example",
                "بەڕێوەبەر", "ڕێکخراوی مافەکانی ئافرەتان", OrganizationType.NGO, Governorate.SULAYMANIYAH, "سلێمانی", "A1234567");
        Visitor v3 = visitor("شێرزاد", "عومەر", "ڕەسوڵ", "دۆسکی", Gender.MALE, "07504445566", null,
                "جووتیار", null, OrganizationType.INDIVIDUAL, Governorate.DUHOK, "زاخۆ", "19750009876");
        Visitor v4 = visitor("لانە", "جەمال", "عەزیز", null, Gender.FEMALE, "07709998877", "lana@media.example",
                "ڕۆژنامەنووس", "تۆڕی میدیایی کوردستان", OrganizationType.MEDIA, Governorate.ERBIL, "هەولێر", null);
        Visitor v5 = visitor("Michael", null, null, "Schmidt", Gender.MALE, "+491701234567", "m.schmidt@consulate.example",
                "Consul", "Consulate General", OrganizationType.DIPLOMATIC_MISSION, Governorate.ABROAD, "Erbil", "C0X12345");
        v5.setNationality("German");
        v5.setNameLatin("Michael Schmidt");
        v5.setPreferredLanguage(Language.en);
        v5.setPreferredContactChannel(ContactChannel.EMAIL);
        v5.setVip(true);
        v5.setIdType(IdType.PASSPORT);
        visitors.save(v5);
        Visitor v6 = visitor("هاوڕێ", "سالار", "کەریم", "هەمەوەندی", Gender.MALE, "07511212121", null,
                "خاوەن دووکان", null, OrganizationType.INDIVIDUAL, Governorate.HALABJA, "هەڵەبجە", "19900004444");

        ConstituentCase c = new ConstituentCase();
        c.setCaseNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        c.setTitle("دواکەوتنی مووچەی مامۆستایانی هەولێر");
        c.setVisitor(v1);
        c.setOffice(dsp);
        c.setAssignedTo(secretary);
        c.setRelatedMinistry("وەزارەتی پەروەردە");
        c.setStatus(CaseStatus.IN_PROGRESS);
        c = cases.save(c);
        c.setCaseNumber(String.format("CASE-%d-%04d", LocalDate.now().getYear(), c.getId()));

        LocalDate today = LocalDate.now();
        LocalDate workDay = nextWorkDay(dsp, today);
        req(dsp, v1, "دواکەوتنی مووچەی مامۆستایان", VisitPurpose.CONSTITUENT_COMPLAINT, Priority.NORMAL, VisitStatus.COMPLETED,
                RequestChannel.WALK_IN, secretary, today.minusDays(12).atTime(10, 0), 30, c, director);
        req(dsp, v1, "بەدواداچوونی دۆسیەی مووچەی مامۆستایان", VisitPurpose.CONSTITUENT_COMPLAINT, Priority.NORMAL, VisitStatus.SCHEDULED,
                RequestChannel.PHONE, secretary, workDay.atTime(10, 0), 30, c, director);
        req(dsp, v2, "پرۆژەیاسای ڕووبەڕووبوونەوەی توندوتیژی خێزانی - شاندی ڕێکخراو", VisitPurpose.OFFICIAL_DELEGATION,
                Priority.OFFICIAL, VisitStatus.SCHEDULED, RequestChannel.OFFICIAL_LETTER, secretary, workDay.atTime(11, 0), 45, null, deputy);
        req(dsp, v3, "کێشەی خاوەندارێتی زەوی لە زاخۆ", VisitPurpose.CONSTITUENT_COMPLAINT, Priority.NORMAL, VisitStatus.REQUESTED,
                RequestChannel.WALK_IN, secretary, null, 30, null, null);
        VisitRequest urgent = req(dsp, v6, "چارەسەری پزیشکی لە دەرەوە بۆ منداڵێک", VisitPurpose.CONSTITUENT_COMPLAINT,
                Priority.URGENT_HUMANITARIAN, VisitStatus.UNDER_REVIEW, RequestChannel.PHONE, secretary, null, 15, null, null);
        urgent.setRecommendation(Recommendation.APPROVE);
        urgent.setRecommendedBy(secretary);
        urgent.setRecommendedAt(LocalDateTime.now().minusDays(2));
        urgent.setRecommendationNote("دۆخێکی مرۆیی بەپەلەیە، بەڵگەنامەکان هاوپێچن.");
        req(spk, v4, "داواکاری چاوپێکەوتن دەربارەی دانیشتنی پەرلەمان", VisitPurpose.MEDIA, Priority.NORMAL, VisitStatus.REQUESTED,
                RequestChannel.EMAIL, secretary2, null, 30, null, null);
        req(spk, v5, "سەردانی ڕێزلێنانی کونسوڵی گشتی", VisitPurpose.DIPLOMATIC, Priority.VIP, VisitStatus.APPROVED,
                RequestChannel.OFFICIAL_LETTER, secretary2, null, 45, null, speaker);
        req(spk, v4, "کۆنگرەی ڕۆژنامەوانی دەربارەی بودجە", VisitPurpose.MEDIA, Priority.NORMAL, VisitStatus.REJECTED,
                RequestChannel.ONLINE, secretary2, null, 30, null, speaker);
        req(leg, v3, "ڕاپرسی دەربارەی یاسای زەوی و زار", VisitPurpose.CONSTITUENT_COMPLAINT, Priority.NORMAL, VisitStatus.COMPLETED,
                RequestChannel.WALK_IN, null, today.minusDays(5).atTime(9, 30), 30, null, null);
        req(hrc, v6, "داواکاری هاوکاری بۆ دۆزینەوەی کار", VisitPurpose.PERSONAL, Priority.NORMAL, VisitStatus.NO_SHOW,
                RequestChannel.PHONE, secretary, today.minusDays(3).atTime(12, 0), 15, null, null);
        req(cmp, v2, "سکاڵا دەربارەی دواکەوتنی مامەڵەی فەرمی", VisitPurpose.CONSTITUENT_COMPLAINT, Priority.NORMAL,
                VisitStatus.REQUESTED, RequestChannel.ONLINE, null, null, 30, null, null);
        VisitRequest overdue = req(dsp, v3, "قەرەبووکردنەوەی زیانی لافاو", VisitPurpose.CONSTITUENT_COMPLAINT, Priority.NORMAL,
                VisitStatus.REQUESTED, RequestChannel.WALK_IN, secretary, null, 30, null, null);
        requests.flush();
        jdbc.update("update visit_requests set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(dsp.getOverdueDays() + 3L), overdue.getId());

        block(dsp, "دانیشتنی گشتیی پەرلەمان", BlockType.PARLIAMENT_SESSION, workDay.atTime(13, 0), workDay.atTime(15, 0), director);
        block(spk, "دانیشتنی گشتیی پەرلەمان", BlockType.PARLIAMENT_SESSION, workDay.atTime(13, 0), workDay.atTime(15, 0), admin);
        block(leg, "کۆبوونەوەی لیژنە", BlockType.COMMITTEE_MEETING, workDay.atTime(9, 0), workDay.atTime(11, 0), admin);

        WatchlistEntry w = new WatchlistEntry();
        w.setFullName("Test Blocked Person");
        w.setPhone("+9647500000000");
        w.setLevel(WatchlistLevel.BLOCK);
        w.setReason("تۆماری نموونەیی: ڕەفتاری هەڕەشەئامێز لە سەردانێکی پێشوودا.");
        w.setAddedBy(admin);
        watchlist.save(w);

        FollowUpTask t = new FollowUpTask();
        t.setTitle("پەیوەندیکردن بە وەزارەتی پەروەردەوە دەربارەی مووچەی دواکەوتوو");
        t.setOwner(secretary);
        t.setOffice(dsp);
        t.setDueDate(today.plusDays(3));
        t.setConstituentCase(c);
        t.setCreatedBy(director);
        tasks.save(t);
        log.info("Demo data created");
    }

    private void block(Office o, String title, BlockType type, LocalDateTime s, LocalDateTime e, AppUser by) {
        CalendarBlock b = new CalendarBlock();
        b.setOffice(o);
        b.setTitle(title);
        b.setType(type);
        b.setStartAt(s);
        b.setEndAt(e);
        b.setCreatedBy(by);
        blocks.save(b);
    }

    private LocalDate nextWorkDay(Office o, LocalDate d) {
        Set<java.time.DayOfWeek> days = o.getWorkDaySet();
        LocalDate x = d.plusDays(1);
        while (!days.contains(x.getDayOfWeek())) {
            x = x.plusDays(1);
        }
        return x;
    }

    private Visitor visitor(String first, String father, String grand, String family, Gender g, String phone, String email,
                            String job, String org, OrganizationType type, Governorate gov, String city, String id) {
        Visitor v = new Visitor();
        v.setFirstName(first);
        v.setFatherName(father);
        v.setGrandfatherName(grand);
        v.setFamilyName(family);
        v.setGender(g);
        v.setPhone(Phones.normalize(phone));
        v.setEmail(email);
        v.setJobTitle(job);
        v.setOrganization(org);
        v.setOrganizationType(type);
        v.setGovernorate(gov);
        v.setCity(city);
        v.setNationality("عێراقی");
        v.setDateOfBirth(LocalDate.of(1975 + (int) (Math.random() * 25), 1 + (int) (Math.random() * 12), 10));
        if (id != null) {
            v.setIdType(IdType.NATIONAL_ID);
            v.setIdNumber(id);
            v.setIdNumberHash(crypto.hash(id));
        }
        return visitors.save(v);
    }

    private VisitRequest req(Office office, Visitor v, String subject, VisitPurpose purpose, Priority p, VisitStatus status,
                             RequestChannel ch, AppUser manager, LocalDateTime start, int minutes,
                             ConstituentCase c, AppUser decider) {
        VisitRequest r = new VisitRequest();
        r.setOffice(office);
        r.setVisitor(v);
        r.setSubject(subject);
        r.setDescription(subject + ". وردەکارییەکان لە کاتی تۆمارکردندا لەلایەن سەردانکەرەوە دراون.");
        r.setPurpose(purpose);
        r.setPriority(p);
        r.setStatus(status);
        r.setChannel(ch);
        r.setManagedBy(manager);
        r.setCreatedBy(manager);
        r.setDurationMinutes(minutes);
        r.setLocation(office.getDefaultLocation());
        r.setConstituentCase(c);
        r.setReferenceNumber("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26));
        r.setPublicToken(CryptoService.randomToken(18));
        if (decider != null) {
            r.setDecidedBy(decider);
            r.setDecidedAt(LocalDateTime.now().minusDays(1));
        }
        if (status != VisitStatus.REQUESTED && status != VisitStatus.UNDER_REVIEW && status != VisitStatus.REJECTED) {
            r.setQrToken(CryptoService.randomToken(16));
        }
        if (status == VisitStatus.REJECTED) {
            r.setDecisionReason("خشتەی ئەم مانگە پڕە؛ تکایە پەیوەندی بە نووسینگەی ڕاگەیاندنەوە بکە.");
        }
        if (start != null) {
            r.setScheduledStart(start);
            r.setScheduledEnd(start.plusMinutes(minutes));
            if (status == VisitStatus.COMPLETED) {
                r.setCheckInAt(start.minusMinutes(10));
                r.setMeetingStartAt(start.plusMinutes(5));
                r.setMeetingEndAt(start.plusMinutes(minutes + 8));
                r.setCheckOutAt(start.plusMinutes(minutes + 10));
                r.setQueueDate(start.toLocalDate());
                r.setQueueNumber(1);
                r.setOutcomeSummary("بەڵێن درا بەدواداچوون لەگەڵ وەزارەتی پەیوەندیدار بکرێت.");
            }
        }
        r = requests.save(r);
        r.setReferenceNumber(String.format("%s-%d-%06d", office.getCode(), LocalDate.now().getYear(), r.getId()));
        AuditLog a = new AuditLog();
        a.setEntityType("VisitRequest");
        a.setEntityId(r.getId());
        a.setAction("REQUEST_CREATED");
        a.setToStatus(VisitStatus.REQUESTED);
        a.setActorName(manager == null ? "System" : manager.getFullName());
        a.setUser(manager);
        a.setPublicEvent(true);
        a.setTimestamp(start != null && start.isBefore(LocalDateTime.now()) ? start.minusDays(4) : LocalDateTime.now().minusDays(2));
        audit.save(a);
        if (status != VisitStatus.REQUESTED) {
            AuditLog s = new AuditLog();
            s.setEntityType("VisitRequest");
            s.setEntityId(r.getId());
            s.setAction(status.name());
            s.setFromStatus(VisitStatus.REQUESTED);
            s.setToStatus(status);
            s.setActorName(decider == null ? "System" : decider.getFullName());
            s.setUser(decider);
            s.setPublicEvent(true);
            s.setTimestamp(start != null && start.isBefore(LocalDateTime.now()) ? start : LocalDateTime.now().minusDays(1));
            audit.save(s);
        }
        r = requests.save(r);
        if (start != null && start.isBefore(LocalDateTime.now())) {
            requests.flush();
            jdbc.update("update visit_requests set created_at = ? where id = ?", start.minusDays(4), r.getId());
        }
        return r;
    }
}
