package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Blocked time: parliament sessions, committee meetings, travel. No visits can be booked here. */
@Entity
@Table(name = "calendar_blocks")
@Getter
@Setter
public class CalendarBlock extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Office office;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Enums.BlockType type = Enums.BlockType.PARLIAMENT_SESSION;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(nullable = false)
    private LocalDateTime startAt;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(nullable = false)
    private LocalDateTime endAt;

    private boolean allDay;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser createdBy;
}
