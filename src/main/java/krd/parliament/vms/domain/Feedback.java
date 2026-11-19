package krd.parliament.vms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Short survey answered by the visitor after the visit. */
@Entity
@Table(name = "feedback")
@Getter
@Setter
public class Feedback extends BaseEntity {

    @OneToOne(optional = false)
    private VisitRequest request;

    /** 1 (very poor) - 5 (excellent). */
    private int rating;

    /** Was the reception and waiting experience good? 1-5 */
    private Integer receptionRating;

    private Boolean issueAddressed;

    @Column(length = 2000)
    private String comment;
}
