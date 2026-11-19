package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import krd.parliament.vms.domain.Enums.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.Period;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Full visitor profile: identity, contact, organisation, location and preferences. */
@Entity
@Table(name = "visitors", indexes = {
        @Index(name = "ix_visitor_phone", columnList = "phone"),
        @Index(name = "ix_visitor_idhash", columnList = "idNumberHash")
})
@Getter
@Setter
public class Visitor extends BaseEntity {

    // ---- Identity ----
    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String firstName;

    @Size(max = 80)
    @Column(length = 80)
    private String fatherName;

    @Size(max = 80)
    @Column(length = 80)
    private String grandfatherName;

    @Size(max = 80)
    @Column(length = 80)
    private String familyName;

    /** Name written in Latin letters (for badges, passports, foreign guests). */
    @Size(max = 200)
    @Column(length = 200)
    private String nameLatin;

    @Size(max = 40)
    @Column(length = 40)
    private String title; // Mr., Dr., Sheikh, H.E. ...

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @Size(max = 60)
    @Column(length = 60)
    private String nationality = "Iraqi";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IdType idType;

    /** Stored encrypted (AES-256-GCM). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 200)
    private String idNumber;

    /** Keyed hash of the ID number for searching and watchlist matching. */
    @Column(length = 64)
    private String idNumberHash;

    @Size(max = 80)
    @Column(length = 80)
    private String idIssuePlace;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate idExpiryDate;

    // ---- Contact ----
    @NotBlank
    @Size(max = 30)
    @Column(nullable = false, length = 30)
    private String phone;

    @Size(max = 30)
    @Column(length = 30)
    private String phoneSecondary;

    @Size(max = 30)
    @Column(length = 30)
    private String whatsappNumber;

    @Email
    @Size(max = 150)
    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContactChannel preferredContactChannel = ContactChannel.SMS;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private Language preferredLanguage = Language.ckb;

    /** Visitor agreed to receive SMS / WhatsApp / email messages. */
    private boolean consentToMessages = true;

    // ---- Work / organisation ----
    @Size(max = 120)
    @Column(length = 120)
    private String jobTitle;

    @Size(max = 200)
    @Column(length = 200)
    private String organization;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrganizationType organizationType = OrganizationType.INDIVIDUAL;

    // ---- Location ----
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Governorate governorate;

    @Size(max = 80)
    @Column(length = 80)
    private String city;

    @Size(max = 80)
    @Column(length = 80)
    private String district;

    @Size(max = 300)
    @Column(length = 300)
    private String address;

    // ---- Emergency contact ----
    @Size(max = 120)
    @Column(length = 120)
    private String emergencyContactName;

    @Size(max = 30)
    @Column(length = 30)
    private String emergencyContactPhone;

    // ---- Office use ----
    private boolean vip;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    /** Photo stored in the attachments directory (relative path). */
    @Column(length = 300)
    private String photoPath;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;

    public String getFullName() {
        return Stream.of(title, firstName, fatherName, grandfatherName, familyName)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    public String getShortName() {
        return Stream.of(firstName, fatherName, familyName)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    public Integer getAge() {
        return dateOfBirth == null ? null : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public String getMaskedIdNumber() {
        if (idNumber == null || idNumber.length() < 4) {
            return idNumber;
        }
        return "•".repeat(Math.max(0, idNumber.length() - 4)) + idNumber.substring(idNumber.length() - 4);
    }
}
