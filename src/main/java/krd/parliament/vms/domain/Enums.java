package krd.parliament.vms.domain;

/**
 * Small value lists used by the visitor profile and visit requests.
 * Each is translated in the UI with the message key {@code enum.<Type>.<VALUE>}.
 */
public final class Enums {

    private Enums() {
    }

    public enum RequestChannel { WALK_IN, PHONE, ONLINE, EMAIL, OFFICIAL_LETTER, OTHER_OFFICE }

    public enum VisitPurpose {
        CONSTITUENT_COMPLAINT, OFFICIAL_DELEGATION, MEDIA, POLITICAL_PARTY, NGO, PERSONAL,
        BUSINESS, DIPLOMATIC, ACADEMIC, RELIGIOUS_TRIBAL, OTHER
    }

    public enum OrganizationType {
        INDIVIDUAL, GOVERNMENT, PARLIAMENT, POLITICAL_PARTY, NGO, MEDIA, PRIVATE_SECTOR,
        INTERNATIONAL, DIPLOMATIC_MISSION, ACADEMIC, RELIGIOUS, TRIBAL, OTHER
    }

    public enum Gender { MALE, FEMALE }

    public enum IdType { NATIONAL_ID, PASSPORT, RESIDENCY_CARD, DRIVING_LICENSE, OTHER }

    public enum Governorate {
        ERBIL, SULAYMANIYAH, DUHOK, HALABJA, KIRKUK, NINEVEH, DIYALA, SALAHADDIN, BAGHDAD, OTHER_IRAQ, ABROAD
    }

    public enum ContactChannel { SMS, WHATSAPP, EMAIL, PHONE_CALL }

    public enum Language { ckb, ar, en, kmr }

    public enum Recommendation { APPROVE, REJECT, POSTPONE, REFER }

    public enum BlockType { PARLIAMENT_SESSION, COMMITTEE_MEETING, TRAVEL, OFFICIAL_EVENT, PERSONAL, OTHER }

    public enum WatchlistLevel { ALERT, BLOCK }

    public enum NotificationChannel { SMS, WHATSAPP, EMAIL, IN_APP }

    public enum NotificationStatus { PENDING, SENT, LOGGED, FAILED }

    public enum TaskStatus { OPEN, IN_PROGRESS, DONE, CANCELLED }

    public enum CaseStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }

    public enum OfficeType {
        PRESIDENCY, SECRETARIAT_GENERAL, COMMITTEE, PARLIAMENTARY_GROUP, MP_OFFICE, DIRECTORATE, DEPARTMENT, EXTERNAL
    }
}
