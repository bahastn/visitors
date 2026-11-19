package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Groups repeat visits about the same issue into one case. */
@Entity
@Table(name = "constituent_cases")
@Getter
@Setter
public class ConstituentCase extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String caseNumber;

    @NotBlank
    @Column(nullable = false, length = 250)
    private String title;

    @Column(length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    private Visitor visitor;

    @ManyToOne(fetch = FetchType.EAGER)
    private Office office;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Enums.CaseStatus status = Enums.CaseStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser assignedTo;

    @Column(length = 200)
    private String relatedMinistry;

    @Column(length = 3000)
    private String resolution;

    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "constituentCase")
    @OrderBy("createdAt desc")
    private List<VisitRequest> visits = new ArrayList<>();
}
