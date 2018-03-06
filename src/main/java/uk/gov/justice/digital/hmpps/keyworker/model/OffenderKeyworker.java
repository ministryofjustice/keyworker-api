package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.validator.constraints.Length;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "OFFENDER_KEY_WORKER")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"offenderNo", "staffId", "assignedDateTime"})
public class OffenderKeyworker {

    @Id()
    @GeneratedValue
    @Column(name = "offender_keyworker_id", nullable = false)
    private Long offenderKeyworkerId;

    @NotNull
    @Length(max = 10)
    @Column(name = "OFFENDER_NO", nullable = false)
    private String offenderNo;

    @NotNull
    @Column(name = "STAFF_ID", nullable = false)
    private Long staffId;

    @NotNull
    @Column(name = "ASSIGNED_DATE_TIME", nullable = false)
    private LocalDateTime assignedDateTime;

    @Type(type = "yes_no")
    @Column(name = "ACTIVE_FLAG", nullable = false)
    private boolean active;

    @NotNull
    @Column(name = "ALLOC_REASON", nullable = false)
    @Convert(converter = AllocationReasonConvertor.class)
    private AllocationReason allocationReason;

    @Column(name = "ALLOC_TYPE", nullable = false)
    @Convert(converter = AllocationTypeConvertor.class)
    private AllocationType allocationType;

    @NotNull
    @Length(max = 32)
    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @NotNull
    @Length(max = 6)
    @Column(name = "AGY_LOC_ID", nullable = false)
    private String agencyId;

    @Column(name = "EXPIRY_DATE_TIME")
    LocalDateTime expiryDateTime;

    @Column(name = "DEALLOC_REASON")
    @Convert(converter = DeallocationReasonConvertor.class)
    private DeallocationReason deallocationReason;

    /* --------------------------------------------------------------------------------------
     * Generic fields below here.  Move to super-type?
     *
     * The way these fields behave and are used will probably change when OFFENDER_KEY_WORKER
     * data is no longer imported from elite2-api.
     * For now fields like creationDateTime must always be set before persisting.
     * Later, annotations such as @CreationTimestamp or @Generated might be useful.
     * ------------------------------------------------------------------------------------- */
    @NotNull
    private CreateUpdate createUpdate;
}
