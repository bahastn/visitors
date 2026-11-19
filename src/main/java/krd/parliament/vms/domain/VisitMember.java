package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A member of a delegation attending with the main visitor. */
@Entity
@Table(name = "visit_members")
@Getter
@Setter
public class VisitMember extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private VisitRequest request;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(length = 120)
    private String jobTitle;

    @Column(length = 200)
    private String organization;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Enums.IdType idType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 200)
    private String idNumber;

    @Column(length = 64)
    private String idNumberHash;

    private LocalDateTime checkInAt;

    private boolean watchlistFlag;
}
