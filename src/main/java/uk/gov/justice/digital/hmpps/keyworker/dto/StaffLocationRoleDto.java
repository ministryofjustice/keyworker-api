package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StaffLocationRoleDto {
    private Long staffId;
    private String firstName;
    private String lastName;
    private String email;
    private Long thumbnailId;
    private String agencyId;
    private String agencyDescription;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String position;
    private String positionDescription;
    private String role;
    private String roleDescription;
    private String scheduleType;
    private String scheduleTypeDescription;
    private BigDecimal hoursPerWeek;

  public StaffLocationRoleDto(Long staffId, String firstName, String lastName, String email, Long thumbnailId, String agencyId, String agencyDescription, LocalDate fromDate, LocalDate toDate, String position, String positionDescription, String role, String roleDescription, String scheduleType, String scheduleTypeDescription, BigDecimal hoursPerWeek) {
    this.staffId = staffId;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.thumbnailId = thumbnailId;
    this.agencyId = agencyId;
    this.agencyDescription = agencyDescription;
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.position = position;
    this.positionDescription = positionDescription;
    this.role = role;
    this.roleDescription = roleDescription;
    this.scheduleType = scheduleType;
    this.scheduleTypeDescription = scheduleTypeDescription;
    this.hoursPerWeek = hoursPerWeek;
  }

  public StaffLocationRoleDto() {
  }

  public static StaffLocationRoleDtoBuilder builder() {
    return new StaffLocationRoleDtoBuilder();
  }

  public Long getStaffId() {
    return this.staffId;
  }

  public String getFirstName() {
    return this.firstName;
  }

  public String getLastName() {
    return this.lastName;
  }

  public String getEmail() {
    return this.email;
  }

  public Long getThumbnailId() {
    return this.thumbnailId;
  }

  public String getAgencyId() {
    return this.agencyId;
  }

  public String getAgencyDescription() {
    return this.agencyDescription;
  }

  public LocalDate getFromDate() {
    return this.fromDate;
  }

  public LocalDate getToDate() {
    return this.toDate;
  }

  public String getPosition() {
    return this.position;
  }

  public String getPositionDescription() {
    return this.positionDescription;
  }

  public String getRole() {
    return this.role;
  }

  public String getRoleDescription() {
    return this.roleDescription;
  }

  public String getScheduleType() {
    return this.scheduleType;
  }

  public String getScheduleTypeDescription() {
    return this.scheduleTypeDescription;
  }

  public BigDecimal getHoursPerWeek() {
    return this.hoursPerWeek;
  }

  public void setStaffId(Long staffId) {
    this.staffId = staffId;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setThumbnailId(Long thumbnailId) {
    this.thumbnailId = thumbnailId;
  }

  public void setAgencyId(String agencyId) {
    this.agencyId = agencyId;
  }

  public void setAgencyDescription(String agencyDescription) {
    this.agencyDescription = agencyDescription;
  }

  public void setFromDate(LocalDate fromDate) {
    this.fromDate = fromDate;
  }

  public void setToDate(LocalDate toDate) {
    this.toDate = toDate;
  }

  public void setPosition(String position) {
    this.position = position;
  }

  public void setPositionDescription(String positionDescription) {
    this.positionDescription = positionDescription;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public void setRoleDescription(String roleDescription) {
    this.roleDescription = roleDescription;
  }

  public void setScheduleType(String scheduleType) {
    this.scheduleType = scheduleType;
  }

  public void setScheduleTypeDescription(String scheduleTypeDescription) {
    this.scheduleTypeDescription = scheduleTypeDescription;
  }

  public void setHoursPerWeek(BigDecimal hoursPerWeek) {
    this.hoursPerWeek = hoursPerWeek;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof StaffLocationRoleDto)) return false;
    final StaffLocationRoleDto other = (StaffLocationRoleDto) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$staffId = this.getStaffId();
    final Object other$staffId = other.getStaffId();
    if (this$staffId == null ? other$staffId != null : !this$staffId.equals(other$staffId)) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof StaffLocationRoleDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $staffId = this.getStaffId();
    result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
    return result;
  }

  public String toString() {
    return "StaffLocationRoleDto(staffId=" + this.getStaffId() + ", thumbnailId=" + this.getThumbnailId() + ", agencyId=" + this.getAgencyId() + ", agencyDescription=" + this.getAgencyDescription() + ", fromDate=" + this.getFromDate() + ", toDate=" + this.getToDate() + ", position=" + this.getPosition() + ", positionDescription=" + this.getPositionDescription() + ", role=" + this.getRole() + ", roleDescription=" + this.getRoleDescription() + ", scheduleType=" + this.getScheduleType() + ", scheduleTypeDescription=" + this.getScheduleTypeDescription() + ", hoursPerWeek=" + this.getHoursPerWeek() + ")";
  }

  public static class StaffLocationRoleDtoBuilder {
    private Long staffId;
    private String firstName;
    private String lastName;
    private String email;
    private Long thumbnailId;
    private String agencyId;
    private String agencyDescription;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String position;
    private String positionDescription;
    private String role;
    private String roleDescription;
    private String scheduleType;
    private String scheduleTypeDescription;
    private BigDecimal hoursPerWeek;

    StaffLocationRoleDtoBuilder() {
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder staffId(Long staffId) {
      this.staffId = staffId;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder firstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder lastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder email(String email) {
      this.email = email;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder thumbnailId(Long thumbnailId) {
      this.thumbnailId = thumbnailId;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder agencyId(String agencyId) {
      this.agencyId = agencyId;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder agencyDescription(String agencyDescription) {
      this.agencyDescription = agencyDescription;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder fromDate(LocalDate fromDate) {
      this.fromDate = fromDate;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder toDate(LocalDate toDate) {
      this.toDate = toDate;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder position(String position) {
      this.position = position;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder positionDescription(String positionDescription) {
      this.positionDescription = positionDescription;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder role(String role) {
      this.role = role;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder roleDescription(String roleDescription) {
      this.roleDescription = roleDescription;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder scheduleType(String scheduleType) {
      this.scheduleType = scheduleType;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder scheduleTypeDescription(String scheduleTypeDescription) {
      this.scheduleTypeDescription = scheduleTypeDescription;
      return this;
    }

    public StaffLocationRoleDto.StaffLocationRoleDtoBuilder hoursPerWeek(BigDecimal hoursPerWeek) {
      this.hoursPerWeek = hoursPerWeek;
      return this;
    }

    public StaffLocationRoleDto build() {
      return new StaffLocationRoleDto(staffId, firstName, lastName, email, thumbnailId, agencyId, agencyDescription, fromDate, toDate, position, positionDescription, role, roleDescription, scheduleType, scheduleTypeDescription, hoursPerWeek);
    }

    public String toString() {
      return "StaffLocationRoleDto.StaffLocationRoleDtoBuilder(staffId=" + this.staffId + ", firstName=" + this.firstName + ", lastName=" + this.lastName + ", email=" + this.email + ", thumbnailId=" + this.thumbnailId + ", agencyId=" + this.agencyId + ", agencyDescription=" + this.agencyDescription + ", fromDate=" + this.fromDate + ", toDate=" + this.toDate + ", position=" + this.position + ", positionDescription=" + this.positionDescription + ", role=" + this.role + ", roleDescription=" + this.roleDescription + ", scheduleType=" + this.scheduleType + ", scheduleTypeDescription=" + this.scheduleTypeDescription + ", hoursPerWeek=" + this.hoursPerWeek + ")";
    }
  }
}
