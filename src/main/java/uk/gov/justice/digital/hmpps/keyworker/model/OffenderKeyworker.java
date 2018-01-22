package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "offender_key_worker")
@Data
public class OffenderKeyworker {

    @Id
    @Column(name = "offender_keywork_id", nullable = false)
    private String offenderKeyworkerId;

    @Column(name = "offender_book_id", nullable = false)
    private Long offenderBookingId;

    @Column(name = "officer_id", nullable = false)
    private Long officerId;

    @Column(name = "assigned_datetime", nullable = false)
    private LocalDateTime assignedDateTime;
}
