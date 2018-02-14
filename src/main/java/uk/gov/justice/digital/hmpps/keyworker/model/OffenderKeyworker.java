package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "offender_key_worker")
@Data
public class OffenderKeyworker {

    @Id()
    @GeneratedValue(strategy=GenerationType.AUTO)
    @Column(name = "offender_keyworker_id", nullable = false)
    private Long offenderKeyworkerId;

    @Column(name = "offender_book_id", nullable = false)
    private Long offenderBookingId;

    @Column(name = "staff_username", nullable = false)
    private String staffUsername;

    @Column(name = "assigned_datetime", nullable = false)
    private Date assignedDateTime;
}
