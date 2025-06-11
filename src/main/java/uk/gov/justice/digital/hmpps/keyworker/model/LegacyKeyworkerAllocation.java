package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;
import org.hibernate.validator.constraints.Length;
import org.slf4j.Logger;
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData;
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED;

@Audited
@Entity
@Table(name = "ALLOCATION")
public class LegacyKeyworkerAllocation {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(LegacyKeyworkerAllocation.class);
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Length(max = 10)
    @Column(name = "PERSON_IDENTIFIER", nullable = false)
    private String personIdentifier;

    @NotNull
    @Column(name = "STAFF_ID", nullable = false)
    private Long staffId;

    @NotNull
    @Column(name = "ALLOCATED_AT", nullable = false)
    private LocalDateTime assignedDateTime;

    @Audited(withModifiedFlag = true, modifiedColumnName = "is_active_modified")
    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active;

    @Audited(targetAuditMode = NOT_AUDITED)
    @NotNull
    @ManyToOne
    @JoinColumn(name = "ALLOCATION_REASON_ID")
    private ReferenceData allocationReason;

    @NotNull
    @Column(name = "ALLOCATION_TYPE", nullable = false)
    @Convert(converter = AllocationTypeConvertor.class)
    private AllocationType allocationType;

    @NotNull
    @Length(max = 64)
    @Column(name = "ALLOCATED_BY", nullable = false)
    private String allocatedBy;

    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_CODE", nullable = false)
    private String prisonCode;

    @Audited(withModifiedFlag = true)
    @Column(name = "DEALLOCATED_AT")
    LocalDateTime deallocatedAt;

    @Audited(withModifiedFlag = true, targetAuditMode = NOT_AUDITED)
    @ManyToOne
    @JoinColumn(name = "DEALLOCATION_REASON_ID")
    private ReferenceData deallocationReason;

    @Audited(withModifiedFlag = true)
    @Length(max = 64)
    @Column(name = "DEALLOCATED_BY")
    private String deallocatedBy;

    @TenantId
    @Column(name = "policy_code", nullable = false, updatable = false)
    private final String policy = AllocationContext.Companion.get().getPolicy().name();

    /* --------------------------------------------------------------------------------------
     * Generic fields below here.  Move to super-type?
     *
     * The way these fields behave and are used will probably change when OFFENDER_KEY_WORKER
     * data is no longer imported from prison-api.
     * ------------------------------------------------------------------------------------- */

    public LegacyKeyworkerAllocation(
        UUID id,
        @NotNull @Length(max = 10) String personIdentifier,
        @NotNull Long staffId,
        @NotNull LocalDateTime assignedDateTime,
        boolean active,
        @NotNull ReferenceData allocationReason,
        @NotNull AllocationType allocationType,
        @NotNull @Length(max = 32) String userId,
        @NotNull @Length(max = 6) String prisonCode,
        LocalDateTime deallocatedAt,
        ReferenceData deallocationReason,
        String deallocatedBy
    ) {
        this.id = id;
        this.personIdentifier = personIdentifier;
        this.staffId = staffId;
        this.assignedDateTime = assignedDateTime;
        this.active = active;
        this.allocationReason = allocationReason;
        this.allocationType = allocationType;
        this.allocatedBy = userId;
        this.prisonCode = prisonCode;
        this.deallocatedAt = deallocatedAt;
        this.deallocationReason = deallocationReason;
        this.deallocatedBy = deallocatedBy;
    }

    public LegacyKeyworkerAllocation() {
    }

    public static OffenderKeyworkerBuilder builder() {
        return new OffenderKeyworkerBuilder();
    }


    public void deallocate(final LocalDateTime expiryDateTime, final ReferenceData deallocationReason) {
        log.info("De-allocating {} in prison {} reason: {}", personIdentifier, prisonCode, deallocationReason);
        setActive(false);
        setDeallocatedAt(expiryDateTime);
        setDeallocationReason(deallocationReason);
        setDeallocatedBy(AllocationContext.Companion.get().getUsername());
    }

    public long getDaysAllocated(final LocalDate fromDate, final LocalDate toDate) {
        final var endTime = deallocatedAt != null ? deallocatedAt.toLocalDate() : toDate;
        final var startTime =
            assignedDateTime.compareTo(fromDate.atStartOfDay()) > 0 ? assignedDateTime.toLocalDate() : fromDate;
        return DAYS.between(startTime, endTime);
    }

    public UUID getId() {
        return this.id;
    }

    public @NotNull @Length(max = 10) String getPersonIdentifier() {
        return this.personIdentifier;
    }

    public @NotNull Long getStaffId() {
        return this.staffId;
    }

    public @NotNull LocalDateTime getAssignedDateTime() {
        return this.assignedDateTime;
    }

    public boolean isActive() {
        return this.active;
    }

    public @NotNull ReferenceData getAllocationReason() {
        return this.allocationReason;
    }

    public @NotNull AllocationType getAllocationType() {
        return this.allocationType;
    }

    public @NotNull @Length(max = 32) String getAllocatedBy() {
        return this.allocatedBy;
    }

    public @NotNull @Length(max = 6) String getPrisonCode() {
        return this.prisonCode;
    }

    public LocalDateTime getDeallocatedAt() {
        return this.deallocatedAt;
    }

    public ReferenceData getDeallocationReason() {
        return this.deallocationReason;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setPersonIdentifier(@NotNull @Length(max = 10) String offenderNo) {
        this.personIdentifier = offenderNo;
    }

    public void setStaffId(@NotNull Long staffId) {
        this.staffId = staffId;
    }

    public void setAssignedDateTime(@NotNull LocalDateTime assignedDateTime) {
        this.assignedDateTime = assignedDateTime;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setAllocationReason(@NotNull ReferenceData allocationReason) {
        this.allocationReason = allocationReason;
    }

    public void setAllocationType(@NotNull AllocationType allocationType) {
        this.allocationType = allocationType;
    }

    public void setAllocatedBy(@NotNull @Length(max = 32) String userId) {
        this.allocatedBy = userId;
    }

    public void setPrisonCode(@NotNull @Length(max = 6) String prisonId) {
        this.prisonCode = prisonId;
    }

    public void setDeallocatedAt(LocalDateTime expiryDateTime) {
        this.deallocatedAt = expiryDateTime;
    }

    public void setDeallocationReason(ReferenceData deallocationReason) {
        this.deallocationReason = deallocationReason;
    }

    public void setDeallocatedBy(String username) {
        this.deallocatedBy = username;
    }

    public String getDeallocatedBy() {
        return this.deallocatedBy;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof LegacyKeyworkerAllocation)) return false;
        final LegacyKeyworkerAllocation other = (LegacyKeyworkerAllocation) o;
        if (!other.canEqual(this)) return false;
        final Object this$offenderNo = this.getPersonIdentifier();
        final Object other$offenderNo = other.getPersonIdentifier();
        if (!Objects.equals(this$offenderNo, other$offenderNo)) return false;
        final Object this$staffId = this.getStaffId();
        final Object other$staffId = other.getStaffId();
        if (!Objects.equals(this$staffId, other$staffId)) return false;
        final Object this$assignedDateTime = this.getAssignedDateTime();
        final Object other$assignedDateTime = other.getAssignedDateTime();
        if (!Objects.equals(this$assignedDateTime, other$assignedDateTime))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof LegacyKeyworkerAllocation;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $offenderNo = this.getPersonIdentifier();
        result = result * PRIME + ($offenderNo == null ? 43 : $offenderNo.hashCode());
        final Object $staffId = this.getStaffId();
        result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
        final Object $assignedDateTime = this.getAssignedDateTime();
        result = result * PRIME + ($assignedDateTime == null ? 43 : $assignedDateTime.hashCode());
        return result;
    }

    public OffenderKeyworkerBuilder toBuilder() {
        return new OffenderKeyworkerBuilder().id(this.id).offenderNo(this.personIdentifier).staffId(this.staffId)
            .assignedDateTime(this.assignedDateTime).active(this.active).allocationReason(this.allocationReason)
            .allocationType(this.allocationType).userId(this.allocatedBy).prisonId(this.prisonCode)
            .deallocatedAt(this.deallocatedAt).deallocationReason(this.deallocationReason)
            .deallocatedBy(this.deallocatedBy);
    }

    public static class OffenderKeyworkerBuilder {
        private UUID id = IdGenerator.INSTANCE.newUuid();
        private @NotNull
        @Length(max = 10) String offenderNo;
        private @NotNull Long staffId;
        private @NotNull LocalDateTime assignedDateTime;
        private boolean active;
        private @NotNull ReferenceData allocationReason;
        private @NotNull AllocationType allocationType;
        private @NotNull
        @Length(max = 32) String userId;
        private @NotNull
        @Length(max = 6) String prisonId;
        private LocalDateTime deallocatedAt;
        private ReferenceData deallocationReason;
        private String deallocatedBy;

        OffenderKeyworkerBuilder() {
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder offenderNo(@NotNull @Length(max = 10) String offenderNo) {
            this.offenderNo = offenderNo;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder staffId(@NotNull Long staffId) {
            this.staffId = staffId;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder assignedDateTime(@NotNull LocalDateTime assignedDateTime) {
            this.assignedDateTime = assignedDateTime;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder active(boolean active) {
            this.active = active;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder allocationReason(@NotNull ReferenceData allocationReason) {
            this.allocationReason = allocationReason;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder allocationType(@NotNull AllocationType allocationType) {
            this.allocationType = allocationType;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder userId(@NotNull @Length(max = 32) String userId) {
            this.userId = userId;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder prisonId(@NotNull @Length(max = 6) String prisonId) {
            this.prisonId = prisonId;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder deallocatedAt(LocalDateTime expiryDateTime) {
            this.deallocatedAt = expiryDateTime;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder deallocationReason(ReferenceData deallocationReason) {
            this.deallocationReason = deallocationReason;
            return this;
        }

        public LegacyKeyworkerAllocation.OffenderKeyworkerBuilder deallocatedBy(String deallocatedBy) {
            this.deallocatedBy = deallocatedBy;
            return this;
        }

        public LegacyKeyworkerAllocation build() {
            return new LegacyKeyworkerAllocation(
                id,
                offenderNo,
                staffId,
                assignedDateTime,
                active,
                allocationReason,
                allocationType,
                userId,
                prisonId,
                deallocatedAt,
                deallocationReason,
                deallocatedBy
            );
        }
    }
}
