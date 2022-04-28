package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@ApiModel(description = "Key worker allocation details")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerAllocationDetailsDto {

    @Schema(required = true, description = "Offender Booking Id")
    @NotNull
    private Long bookingId;

    @Schema(required = true, description = "Offender Unique Reference")
    @NotBlank
    private String offenderNo;

    @Schema(required = true, description = "First Name")
    @NotBlank
    private String firstName;

    @Schema(description = "Middle Name(s)")
    private String middleNames;

    @Schema(required = true, description = "Last Name")
    @NotBlank
    private String lastName;

    @Schema(required = true, description = "The key worker's Staff Id")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Agency Id - will be removed - use prisonId")
    @NotBlank
    @Deprecated
    private String agencyId;

    @Schema(required = true, description = "Prison Id")
    @NotBlank
    private String prisonId;

    @Schema(required = true, description = "Date and time of the allocation")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @Schema(required = true, description = "A")
    @NotNull
    private AllocationType allocationType;

    @Schema(required = true, description = "Description of the location within the prison")
    @NotBlank
    private String internalLocationDesc;

    @Schema(required = true, description = "Prison different to current - deallocation only allowed")
    private boolean deallocOnly;

  public KeyworkerAllocationDetailsDto(@NotNull Long bookingId, @NotBlank String offenderNo, @NotBlank String firstName, String middleNames, @NotBlank String lastName, @NotNull Long staffId, @NotBlank String agencyId, @NotBlank String prisonId, @NotNull LocalDateTime assigned, @NotNull AllocationType allocationType, @NotBlank String internalLocationDesc, boolean deallocOnly) {
    this.bookingId = bookingId;
    this.offenderNo = offenderNo;
    this.firstName = firstName;
    this.middleNames = middleNames;
    this.lastName = lastName;
    this.staffId = staffId;
    this.agencyId = agencyId;
    this.prisonId = prisonId;
    this.assigned = assigned;
    this.allocationType = allocationType;
    this.internalLocationDesc = internalLocationDesc;
    this.deallocOnly = deallocOnly;
  }

  public KeyworkerAllocationDetailsDto() {
  }

  public static KeyworkerAllocationDetailsDtoBuilder builder() {
    return new KeyworkerAllocationDetailsDtoBuilder();
  }

  public @NotNull Long getBookingId() {
    return this.bookingId;
  }

  public @NotBlank String getOffenderNo() {
    return this.offenderNo;
  }

  public @NotBlank String getFirstName() {
    return this.firstName;
  }

  public String getMiddleNames() {
    return this.middleNames;
  }

  public @NotBlank String getLastName() {
    return this.lastName;
  }

  public @NotNull Long getStaffId() {
    return this.staffId;
  }

  @Deprecated
  public @NotBlank String getAgencyId() {
    return this.agencyId;
  }

  public @NotBlank String getPrisonId() {
    return this.prisonId;
  }

  public @NotNull LocalDateTime getAssigned() {
    return this.assigned;
  }

  public @NotNull AllocationType getAllocationType() {
    return this.allocationType;
  }

  public @NotBlank String getInternalLocationDesc() {
    return this.internalLocationDesc;
  }

  public boolean isDeallocOnly() {
    return this.deallocOnly;
  }

  public void setBookingId(@NotNull Long bookingId) {
    this.bookingId = bookingId;
  }

  public void setOffenderNo(@NotBlank String offenderNo) {
    this.offenderNo = offenderNo;
  }

  public void setFirstName(@NotBlank String firstName) {
    this.firstName = firstName;
  }

  public void setMiddleNames(String middleNames) {
    this.middleNames = middleNames;
  }

  public void setLastName(@NotBlank String lastName) {
    this.lastName = lastName;
  }

  public void setStaffId(@NotNull Long staffId) {
    this.staffId = staffId;
  }

  @Deprecated
  public void setAgencyId(@NotBlank String agencyId) {
    this.agencyId = agencyId;
  }

  public void setPrisonId(@NotBlank String prisonId) {
    this.prisonId = prisonId;
  }

  public void setAssigned(@NotNull LocalDateTime assigned) {
    this.assigned = assigned;
  }

  public void setAllocationType(@NotNull AllocationType allocationType) {
    this.allocationType = allocationType;
  }

  public void setInternalLocationDesc(@NotBlank String internalLocationDesc) {
    this.internalLocationDesc = internalLocationDesc;
  }

  public void setDeallocOnly(boolean deallocOnly) {
    this.deallocOnly = deallocOnly;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof KeyworkerAllocationDetailsDto)) return false;
    final KeyworkerAllocationDetailsDto other = (KeyworkerAllocationDetailsDto) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$bookingId = this.getBookingId();
    final Object other$bookingId = other.getBookingId();
    if (this$bookingId == null ? other$bookingId != null : !this$bookingId.equals(other$bookingId)) return false;
    final Object this$offenderNo = this.getOffenderNo();
    final Object other$offenderNo = other.getOffenderNo();
    if (this$offenderNo == null ? other$offenderNo != null : !this$offenderNo.equals(other$offenderNo)) return false;
    final Object this$firstName = this.getFirstName();
    final Object other$firstName = other.getFirstName();
    if (this$firstName == null ? other$firstName != null : !this$firstName.equals(other$firstName)) return false;
    final Object this$middleNames = this.getMiddleNames();
    final Object other$middleNames = other.getMiddleNames();
    if (this$middleNames == null ? other$middleNames != null : !this$middleNames.equals(other$middleNames))
      return false;
    final Object this$lastName = this.getLastName();
    final Object other$lastName = other.getLastName();
    if (this$lastName == null ? other$lastName != null : !this$lastName.equals(other$lastName)) return false;
    final Object this$staffId = this.getStaffId();
    final Object other$staffId = other.getStaffId();
    if (this$staffId == null ? other$staffId != null : !this$staffId.equals(other$staffId)) return false;
    final Object this$agencyId = this.getAgencyId();
    final Object other$agencyId = other.getAgencyId();
    if (this$agencyId == null ? other$agencyId != null : !this$agencyId.equals(other$agencyId)) return false;
    final Object this$prisonId = this.getPrisonId();
    final Object other$prisonId = other.getPrisonId();
    if (this$prisonId == null ? other$prisonId != null : !this$prisonId.equals(other$prisonId)) return false;
    final Object this$assigned = this.getAssigned();
    final Object other$assigned = other.getAssigned();
    if (this$assigned == null ? other$assigned != null : !this$assigned.equals(other$assigned)) return false;
    final Object this$allocationType = this.getAllocationType();
    final Object other$allocationType = other.getAllocationType();
    if (this$allocationType == null ? other$allocationType != null : !this$allocationType.equals(other$allocationType))
      return false;
    final Object this$internalLocationDesc = this.getInternalLocationDesc();
    final Object other$internalLocationDesc = other.getInternalLocationDesc();
    if (this$internalLocationDesc == null ? other$internalLocationDesc != null : !this$internalLocationDesc.equals(other$internalLocationDesc))
      return false;
    if (this.isDeallocOnly() != other.isDeallocOnly()) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof KeyworkerAllocationDetailsDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $bookingId = this.getBookingId();
    result = result * PRIME + ($bookingId == null ? 43 : $bookingId.hashCode());
    final Object $offenderNo = this.getOffenderNo();
    result = result * PRIME + ($offenderNo == null ? 43 : $offenderNo.hashCode());
    final Object $firstName = this.getFirstName();
    result = result * PRIME + ($firstName == null ? 43 : $firstName.hashCode());
    final Object $middleNames = this.getMiddleNames();
    result = result * PRIME + ($middleNames == null ? 43 : $middleNames.hashCode());
    final Object $lastName = this.getLastName();
    result = result * PRIME + ($lastName == null ? 43 : $lastName.hashCode());
    final Object $staffId = this.getStaffId();
    result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
    final Object $agencyId = this.getAgencyId();
    result = result * PRIME + ($agencyId == null ? 43 : $agencyId.hashCode());
    final Object $prisonId = this.getPrisonId();
    result = result * PRIME + ($prisonId == null ? 43 : $prisonId.hashCode());
    final Object $assigned = this.getAssigned();
    result = result * PRIME + ($assigned == null ? 43 : $assigned.hashCode());
    final Object $allocationType = this.getAllocationType();
    result = result * PRIME + ($allocationType == null ? 43 : $allocationType.hashCode());
    final Object $internalLocationDesc = this.getInternalLocationDesc();
    result = result * PRIME + ($internalLocationDesc == null ? 43 : $internalLocationDesc.hashCode());
    result = result * PRIME + (this.isDeallocOnly() ? 79 : 97);
    return result;
  }

  public String toString() {
    return "KeyworkerAllocationDetailsDto(bookingId=" + this.getBookingId() + ", offenderNo=" + this.getOffenderNo() + ", staffId=" + this.getStaffId() + ", agencyId=" + this.getAgencyId() + ", prisonId=" + this.getPrisonId() + ", assigned=" + this.getAssigned() + ", allocationType=" + this.getAllocationType() + ", internalLocationDesc=" + this.getInternalLocationDesc() + ", deallocOnly=" + this.isDeallocOnly() + ")";
  }

  public static class KeyworkerAllocationDetailsDtoBuilder {
    private @NotNull Long bookingId;
    private @NotBlank String offenderNo;
    private @NotBlank String firstName;
    private String middleNames;
    private @NotBlank String lastName;
    private @NotNull Long staffId;
    private @NotBlank String agencyId;
    private @NotBlank String prisonId;
    private @NotNull LocalDateTime assigned;
    private @NotNull AllocationType allocationType;
    private @NotBlank String internalLocationDesc;
    private boolean deallocOnly;

    KeyworkerAllocationDetailsDtoBuilder() {
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder bookingId(@NotNull Long bookingId) {
      this.bookingId = bookingId;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder offenderNo(@NotBlank String offenderNo) {
      this.offenderNo = offenderNo;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder firstName(@NotBlank String firstName) {
      this.firstName = firstName;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder middleNames(String middleNames) {
      this.middleNames = middleNames;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder lastName(@NotBlank String lastName) {
      this.lastName = lastName;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder staffId(@NotNull Long staffId) {
      this.staffId = staffId;
      return this;
    }

    @Deprecated
    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder agencyId(@NotBlank String agencyId) {
      this.agencyId = agencyId;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder prisonId(@NotBlank String prisonId) {
      this.prisonId = prisonId;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder assigned(@NotNull LocalDateTime assigned) {
      this.assigned = assigned;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder allocationType(@NotNull AllocationType allocationType) {
      this.allocationType = allocationType;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder internalLocationDesc(@NotBlank String internalLocationDesc) {
      this.internalLocationDesc = internalLocationDesc;
      return this;
    }

    public KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder deallocOnly(boolean deallocOnly) {
      this.deallocOnly = deallocOnly;
      return this;
    }

    public KeyworkerAllocationDetailsDto build() {
      return new KeyworkerAllocationDetailsDto(bookingId, offenderNo, firstName, middleNames, lastName, staffId, agencyId, prisonId, assigned, allocationType, internalLocationDesc, deallocOnly);
    }

    public String toString() {
      return "KeyworkerAllocationDetailsDto.KeyworkerAllocationDetailsDtoBuilder(bookingId=" + this.bookingId + ", offenderNo=" + this.offenderNo + ", firstName=" + this.firstName + ", middleNames=" + this.middleNames + ", lastName=" + this.lastName + ", staffId=" + this.staffId + ", agencyId=" + this.agencyId + ", prisonId=" + this.prisonId + ", assigned=" + this.assigned + ", allocationType=" + this.allocationType + ", internalLocationDesc=" + this.internalLocationDesc + ", deallocOnly=" + this.deallocOnly + ")";
    }
  }
}
