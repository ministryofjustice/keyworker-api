package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.validator.constraints.Length;
import org.slf4j.Logger;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import static java.time.temporal.ChronoUnit.DAYS;

@Entity
@Table(name = "OFFENDER_KEY_WORKER")
@EntityListeners(AuditingEntityListener.class)
public class OffenderKeyworker {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(OffenderKeyworker.class);
  @Id()
    @GeneratedValue(strategy= GenerationType.IDENTITY)
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
     * data is no longer imported from prison-api.
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

  public OffenderKeyworker(Long offenderKeyworkerId, @NotNull @Length(max = 10) String offenderNo, @NotNull Long staffId, @NotNull LocalDateTime assignedDateTime, boolean active, @NotNull AllocationReason allocationReason, @NotNull AllocationType allocationType, @NotNull @Length(max = 32) String userId, @NotNull @Length(max = 6) String prisonId, LocalDateTime expiryDateTime, DeallocationReason deallocationReason, LocalDateTime creationDateTime, String createUserId, LocalDateTime modifyDateTime, String modifyUserId) {
    this.offenderKeyworkerId = offenderKeyworkerId;
    this.offenderNo = offenderNo;
    this.staffId = staffId;
    this.assignedDateTime = assignedDateTime;
    this.active = active;
    this.allocationReason = allocationReason;
    this.allocationType = allocationType;
    this.userId = userId;
    this.prisonId = prisonId;
    this.expiryDateTime = expiryDateTime;
    this.deallocationReason = deallocationReason;
    this.creationDateTime = creationDateTime;
    this.createUserId = createUserId;
    this.modifyDateTime = modifyDateTime;
    this.modifyUserId = modifyUserId;
  }

  public OffenderKeyworker() {
  }

  public static OffenderKeyworkerBuilder builder() {
    return new OffenderKeyworkerBuilder();
  }


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

  public Long getOffenderKeyworkerId() {
    return this.offenderKeyworkerId;
  }

  public @NotNull @Length(max = 10) String getOffenderNo() {
    return this.offenderNo;
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

  public @NotNull AllocationReason getAllocationReason() {
    return this.allocationReason;
  }

  public @NotNull AllocationType getAllocationType() {
    return this.allocationType;
  }

  public @NotNull @Length(max = 32) String getUserId() {
    return this.userId;
  }

  public @NotNull @Length(max = 6) String getPrisonId() {
    return this.prisonId;
  }

  public LocalDateTime getExpiryDateTime() {
    return this.expiryDateTime;
  }

  public DeallocationReason getDeallocationReason() {
    return this.deallocationReason;
  }

  public LocalDateTime getCreationDateTime() {
    return this.creationDateTime;
  }

  public String getCreateUserId() {
    return this.createUserId;
  }

  public LocalDateTime getModifyDateTime() {
    return this.modifyDateTime;
  }

  public String getModifyUserId() {
    return this.modifyUserId;
  }

  public void setOffenderKeyworkerId(Long offenderKeyworkerId) {
    this.offenderKeyworkerId = offenderKeyworkerId;
  }

  public void setOffenderNo(@NotNull @Length(max = 10) String offenderNo) {
    this.offenderNo = offenderNo;
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

  public void setAllocationReason(@NotNull AllocationReason allocationReason) {
    this.allocationReason = allocationReason;
  }

  public void setAllocationType(@NotNull AllocationType allocationType) {
    this.allocationType = allocationType;
  }

  public void setUserId(@NotNull @Length(max = 32) String userId) {
    this.userId = userId;
  }

  public void setPrisonId(@NotNull @Length(max = 6) String prisonId) {
    this.prisonId = prisonId;
  }

  public void setExpiryDateTime(LocalDateTime expiryDateTime) {
    this.expiryDateTime = expiryDateTime;
  }

  public void setDeallocationReason(DeallocationReason deallocationReason) {
    this.deallocationReason = deallocationReason;
  }

  public void setCreationDateTime(LocalDateTime creationDateTime) {
    this.creationDateTime = creationDateTime;
  }

  public void setCreateUserId(String createUserId) {
    this.createUserId = createUserId;
  }

  public void setModifyDateTime(LocalDateTime modifyDateTime) {
    this.modifyDateTime = modifyDateTime;
  }

  public void setModifyUserId(String modifyUserId) {
    this.modifyUserId = modifyUserId;
  }

  public String toString() {
    return "OffenderKeyworker(offenderKeyworkerId=" + this.getOffenderKeyworkerId() + ", offenderNo=" + this.getOffenderNo() + ", staffId=" + this.getStaffId() + ", assignedDateTime=" + this.getAssignedDateTime() + ", active=" + this.isActive() + ", allocationReason=" + this.getAllocationReason() + ", allocationType=" + this.getAllocationType() + ", userId=" + this.getUserId() + ", prisonId=" + this.getPrisonId() + ", expiryDateTime=" + this.getExpiryDateTime() + ", deallocationReason=" + this.getDeallocationReason() + ", creationDateTime=" + this.getCreationDateTime() + ", createUserId=" + this.getCreateUserId() + ", modifyDateTime=" + this.getModifyDateTime() + ", modifyUserId=" + this.getModifyUserId() + ")";
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof OffenderKeyworker)) return false;
    final OffenderKeyworker other = (OffenderKeyworker) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$offenderNo = this.getOffenderNo();
    final Object other$offenderNo = other.getOffenderNo();
    if (this$offenderNo == null ? other$offenderNo != null : !this$offenderNo.equals(other$offenderNo)) return false;
    final Object this$staffId = this.getStaffId();
    final Object other$staffId = other.getStaffId();
    if (this$staffId == null ? other$staffId != null : !this$staffId.equals(other$staffId)) return false;
    final Object this$assignedDateTime = this.getAssignedDateTime();
    final Object other$assignedDateTime = other.getAssignedDateTime();
    if (this$assignedDateTime == null ? other$assignedDateTime != null : !this$assignedDateTime.equals(other$assignedDateTime))
      return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OffenderKeyworker;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $offenderNo = this.getOffenderNo();
    result = result * PRIME + ($offenderNo == null ? 43 : $offenderNo.hashCode());
    final Object $staffId = this.getStaffId();
    result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
    final Object $assignedDateTime = this.getAssignedDateTime();
    result = result * PRIME + ($assignedDateTime == null ? 43 : $assignedDateTime.hashCode());
    return result;
  }

  public OffenderKeyworkerBuilder toBuilder() {
    return new OffenderKeyworkerBuilder().offenderKeyworkerId(this.offenderKeyworkerId).offenderNo(this.offenderNo).staffId(this.staffId).assignedDateTime(this.assignedDateTime).active(this.active).allocationReason(this.allocationReason).allocationType(this.allocationType).userId(this.userId).prisonId(this.prisonId).expiryDateTime(this.expiryDateTime).deallocationReason(this.deallocationReason).creationDateTime(this.creationDateTime).createUserId(this.createUserId).modifyDateTime(this.modifyDateTime).modifyUserId(this.modifyUserId);
  }

  public static class OffenderKeyworkerBuilder {
    private Long offenderKeyworkerId;
    private @NotNull @Length(max = 10) String offenderNo;
    private @NotNull Long staffId;
    private @NotNull LocalDateTime assignedDateTime;
    private boolean active;
    private @NotNull AllocationReason allocationReason;
    private @NotNull AllocationType allocationType;
    private @NotNull @Length(max = 32) String userId;
    private @NotNull @Length(max = 6) String prisonId;
    private LocalDateTime expiryDateTime;
    private DeallocationReason deallocationReason;
    private LocalDateTime creationDateTime;
    private String createUserId;
    private LocalDateTime modifyDateTime;
    private String modifyUserId;

    OffenderKeyworkerBuilder() {
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder offenderKeyworkerId(Long offenderKeyworkerId) {
      this.offenderKeyworkerId = offenderKeyworkerId;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder offenderNo(@NotNull @Length(max = 10) String offenderNo) {
      this.offenderNo = offenderNo;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder staffId(@NotNull Long staffId) {
      this.staffId = staffId;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder assignedDateTime(@NotNull LocalDateTime assignedDateTime) {
      this.assignedDateTime = assignedDateTime;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder active(boolean active) {
      this.active = active;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder allocationReason(@NotNull AllocationReason allocationReason) {
      this.allocationReason = allocationReason;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder allocationType(@NotNull AllocationType allocationType) {
      this.allocationType = allocationType;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder userId(@NotNull @Length(max = 32) String userId) {
      this.userId = userId;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder prisonId(@NotNull @Length(max = 6) String prisonId) {
      this.prisonId = prisonId;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder expiryDateTime(LocalDateTime expiryDateTime) {
      this.expiryDateTime = expiryDateTime;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder deallocationReason(DeallocationReason deallocationReason) {
      this.deallocationReason = deallocationReason;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder creationDateTime(LocalDateTime creationDateTime) {
      this.creationDateTime = creationDateTime;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder createUserId(String createUserId) {
      this.createUserId = createUserId;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder modifyDateTime(LocalDateTime modifyDateTime) {
      this.modifyDateTime = modifyDateTime;
      return this;
    }

    public OffenderKeyworker.OffenderKeyworkerBuilder modifyUserId(String modifyUserId) {
      this.modifyUserId = modifyUserId;
      return this;
    }

    public OffenderKeyworker build() {
      return new OffenderKeyworker(offenderKeyworkerId, offenderNo, staffId, assignedDateTime, active, allocationReason, allocationType, userId, prisonId, expiryDateTime, deallocationReason, creationDateTime, createUserId, modifyDateTime, modifyUserId);
    }

    public String toString() {
      return "OffenderKeyworker.OffenderKeyworkerBuilder(offenderKeyworkerId=" + this.offenderKeyworkerId + ", offenderNo=" + this.offenderNo + ", staffId=" + this.staffId + ", assignedDateTime=" + this.assignedDateTime + ", active=" + this.active + ", allocationReason=" + this.allocationReason + ", allocationType=" + this.allocationType + ", userId=" + this.userId + ", prisonId=" + this.prisonId + ", expiryDateTime=" + this.expiryDateTime + ", deallocationReason=" + this.deallocationReason + ", creationDateTime=" + this.creationDateTime + ", createUserId=" + this.createUserId + ", modifyDateTime=" + this.modifyDateTime + ", modifyUserId=" + this.modifyUserId + ")";
    }
  }
}
