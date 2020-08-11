package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static java.time.temporal.ChronoUnit.DAYS;

@Entity
@Table(name = "OFFENDER_KEY_WORKER")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"offenderNo", "staffId", "assignedDateTime"})
@EntityListeners(AuditingEntityListener.class)
@Slf4j
public class OffenderKeyworker {

    @Id()
    @GeneratedValue(strategy=GenerationType.IDENTITY)
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

    @NotNull
    @Column(name = "ALLOC_TYPE", nullable = false)
    @Convert(converter = AllocationTypeConvertor.class)
    private AllocationType allocationType;

    @NotNull
    @Length(max = 32)
    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_ID", nullable = false)
    private String prisonId;

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
     * ------------------------------------------------------------------------------------- */

    @CreatedDate
    @Column(name = "CREATE_DATETIME", nullable = false)
    private LocalDateTime creationDateTime;

    @CreatedBy
    @Column(name = "CREATE_USER_ID", nullable = false)
    private String createUserId;

    @LastModifiedDate
    @Column(name = "MODIFY_DATETIME")
    private LocalDateTime modifyDateTime;

    @LastModifiedBy
    @Column(name = "MODIFY_USER_ID")
    private String modifyUserId;


    public void deallocate(final LocalDateTime expiryDateTime, final DeallocationReason deallocationReason) {
        log.info("De-allocating {} in prison {} reason: {}", offenderNo, prisonId, deallocationReason);
        active = false;
        setExpiryDateTime(expiryDateTime);
        setDeallocationReason(deallocationReason);
    }

    public long getDaysAllocated(final LocalDate fromDate, final LocalDate toDate) {
        final var endTime = expiryDateTime != null ? expiryDateTime.toLocalDate() : toDate;
        final var startTime = assignedDateTime.compareTo(fromDate.atStartOfDay()) > 0 ? assignedDateTime.toLocalDate() : fromDate;
        return DAYS.between(startTime, endTime);
    }
}
