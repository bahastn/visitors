package krd.parliament.vms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Internal note on a request. Never shown to the visitor. */
@Entity
@Table(name = "visit_notes")
@Getter
@Setter
public class VisitNote extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private VisitRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser author;

    @Column(nullable = false, length = 3000)
    private String text;
}
