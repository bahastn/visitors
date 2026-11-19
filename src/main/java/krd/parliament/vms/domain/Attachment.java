package krd.parliament.vms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Supporting documents and letters attached to a visit request. */
@Entity
@Table(name = "attachments")
@Getter
@Setter
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private VisitRequest request;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    private long sizeBytes;

    @Column(nullable = false, length = 400)
    private String storagePath;

    @Column(length = 300)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser uploadedBy;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}
