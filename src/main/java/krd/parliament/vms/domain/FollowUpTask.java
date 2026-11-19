package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A promise made during a visit ("contact the ministry") tracked with an owner and deadline. */
@Entity
@Table(name = "follow_up_tasks")
@Getter
@Setter
public class FollowUpTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private VisitRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    private ConstituentCase constituentCase;

    @ManyToOne(fetch = FetchType.EAGER)
    private Office office;

    @NotBlank
    @Column(nullable = false, length = 250)
    private String title;

    @Column(length = 3000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser owner;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Enums.TaskStatus status = Enums.TaskStatus.OPEN;

    @Column(length = 2000)
    private String resultNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;

    private LocalDateTime completedAt;

    public boolean isOverdue() {
        return dueDate != null && dueDate.isBefore(LocalDate.now())
                && (status == Enums.TaskStatus.OPEN || status == Enums.TaskStatus.IN_PROGRESS);
    }
}
