package krd.parliament.vms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Person flagged by security. ALERT warns staff; BLOCK prevents check-in. */
@Entity
@Table(name = "watchlist")
@Getter
@Setter
public class WatchlistEntry extends BaseEntity {

    @NotBlank
    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(length = 64)
    private String idNumberHash;

    /** Last 4 characters of the ID, for display only. */
    @Column(length = 10)
    private String idHint;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.WatchlistLevel level = Enums.WatchlistLevel.ALERT;

    @NotBlank
    @Column(nullable = false, length = 1000)
    private String reason;

    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser addedBy;
}
