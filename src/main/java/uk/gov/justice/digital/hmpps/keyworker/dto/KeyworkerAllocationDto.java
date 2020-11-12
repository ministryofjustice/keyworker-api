package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@ApiModel(description = "New Key worker allocation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerAllocationDto {

    @ApiModelProperty(required = true, value = "Identifies offender who is subject of allocation.")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(required = true, value = "Identifies Key worker who is subject of allocation.")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Prison where allocation is effective.")
    @NotBlank
    private String prisonId;

    @ApiModelProperty(required = true, value = "Type of allocation - auto or manual.")
    @NotNull
    private AllocationType allocationType;

    @ApiModelProperty(required = true, value = "Reason for allocation.")
    @NotNull
    private AllocationReason allocationReason;

    @ApiModelProperty(value = "Reason for de-allocation.")
    private DeallocationReason deallocationReason;

  public KeyworkerAllocationDto(@NotBlank String offenderNo, @NotNull Long staffId, @NotBlank String prisonId, @NotNull AllocationType allocationType, @NotNull AllocationReason allocationReason, DeallocationReason deallocationReason) {
    this.offenderNo = offenderNo;
    this.staffId = staffId;
    this.prisonId = prisonId;
    this.allocationType = allocationType;
    this.allocationReason = allocationReason;
    this.deallocationReason = deallocationReason;
  }

  public KeyworkerAllocationDto() {
  }

  public static KeyworkerAllocationDtoBuilder builder() {
    return new KeyworkerAllocationDtoBuilder();
  }

  public @NotBlank String getOffenderNo() {
    return this.offenderNo;
  }

  public @NotNull Long getStaffId() {
    return this.staffId;
  }

  public @NotBlank String getPrisonId() {
    return this.prisonId;
  }

  public @NotNull AllocationType getAllocationType() {
    return this.allocationType;
  }

  public @NotNull AllocationReason getAllocationReason() {
    return this.allocationReason;
  }

  public DeallocationReason getDeallocationReason() {
    return this.deallocationReason;
  }

  public void setOffenderNo(@NotBlank String offenderNo) {
    this.offenderNo = offenderNo;
  }

  public void setStaffId(@NotNull Long staffId) {
    this.staffId = staffId;
  }

  public void setPrisonId(@NotBlank String prisonId) {
    this.prisonId = prisonId;
  }

  public void setAllocationType(@NotNull AllocationType allocationType) {
    this.allocationType = allocationType;
  }

  public void setAllocationReason(@NotNull AllocationReason allocationReason) {
    this.allocationReason = allocationReason;
  }

  public void setDeallocationReason(DeallocationReason deallocationReason) {
    this.deallocationReason = deallocationReason;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof KeyworkerAllocationDto)) return false;
    final KeyworkerAllocationDto other = (KeyworkerAllocationDto) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$offenderNo = this.getOffenderNo();
    final Object other$offenderNo = other.getOffenderNo();
    if (this$offenderNo == null ? other$offenderNo != null : !this$offenderNo.equals(other$offenderNo)) return false;
    final Object this$staffId = this.getStaffId();
    final Object other$staffId = other.getStaffId();
    if (this$staffId == null ? other$staffId != null : !this$staffId.equals(other$staffId)) return false;
    final Object this$prisonId = this.getPrisonId();
    final Object other$prisonId = other.getPrisonId();
    if (this$prisonId == null ? other$prisonId != null : !this$prisonId.equals(other$prisonId)) return false;
    final Object this$allocationType = this.getAllocationType();
    final Object other$allocationType = other.getAllocationType();
    if (this$allocationType == null ? other$allocationType != null : !this$allocationType.equals(other$allocationType))
      return false;
    final Object this$allocationReason = this.getAllocationReason();
    final Object other$allocationReason = other.getAllocationReason();
    if (this$allocationReason == null ? other$allocationReason != null : !this$allocationReason.equals(other$allocationReason))
      return false;
    final Object this$deallocationReason = this.getDeallocationReason();
    final Object other$deallocationReason = other.getDeallocationReason();
    if (this$deallocationReason == null ? other$deallocationReason != null : !this$deallocationReason.equals(other$deallocationReason))
      return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof KeyworkerAllocationDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $offenderNo = this.getOffenderNo();
    result = result * PRIME + ($offenderNo == null ? 43 : $offenderNo.hashCode());
    final Object $staffId = this.getStaffId();
    result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
    final Object $prisonId = this.getPrisonId();
    result = result * PRIME + ($prisonId == null ? 43 : $prisonId.hashCode());
    final Object $allocationType = this.getAllocationType();
    result = result * PRIME + ($allocationType == null ? 43 : $allocationType.hashCode());
    final Object $allocationReason = this.getAllocationReason();
    result = result * PRIME + ($allocationReason == null ? 43 : $allocationReason.hashCode());
    final Object $deallocationReason = this.getDeallocationReason();
    result = result * PRIME + ($deallocationReason == null ? 43 : $deallocationReason.hashCode());
    return result;
  }

  public String toString() {
    return "KeyworkerAllocationDto(offenderNo=" + this.getOffenderNo() + ", staffId=" + this.getStaffId() + ", prisonId=" + this.getPrisonId() + ", allocationType=" + this.getAllocationType() + ", allocationReason=" + this.getAllocationReason() + ", deallocationReason=" + this.getDeallocationReason() + ")";
  }

  public static class KeyworkerAllocationDtoBuilder {
    private @NotBlank String offenderNo;
    private @NotNull Long staffId;
    private @NotBlank String prisonId;
    private @NotNull AllocationType allocationType;
    private @NotNull AllocationReason allocationReason;
    private DeallocationReason deallocationReason;

    KeyworkerAllocationDtoBuilder() {
    }

    public KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder offenderNo(@NotBlank String offenderNo) {
      this.offenderNo = offenderNo;
      return this;
    }

    public KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder staffId(@NotNull Long staffId) {
      this.staffId = staffId;
      return this;
    }

    public KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder prisonId(@NotBlank String prisonId) {
      this.prisonId = prisonId;
      return this;
    }

    public KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder allocationType(@NotNull AllocationType allocationType) {
      this.allocationType = allocationType;
      return this;
    }

    public KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder allocationReason(@NotNull AllocationReason allocationReason) {
      this.allocationReason = allocationReason;
      return this;
    }

    public KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder deallocationReason(DeallocationReason deallocationReason) {
      this.deallocationReason = deallocationReason;
      return this;
    }

    public KeyworkerAllocationDto build() {
      return new KeyworkerAllocationDto(offenderNo, staffId, prisonId, allocationType, allocationReason, deallocationReason);
    }

    public String toString() {
      return "KeyworkerAllocationDto.KeyworkerAllocationDtoBuilder(offenderNo=" + this.offenderNo + ", staffId=" + this.staffId + ", prisonId=" + this.prisonId + ", allocationType=" + this.allocationType + ", allocationReason=" + this.allocationReason + ", deallocationReason=" + this.deallocationReason + ")";
    }
  }
}
