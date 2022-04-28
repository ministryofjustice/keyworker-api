package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@ApiModel(description = "Key worker details")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerDto {

    @Schema(required = true, description = "Unique staff identifier for Key worker.")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Key worker's first name.")
    @NotBlank
    private String firstName;

    @Schema(required = true, description = "Key worker's last name.")
    @NotBlank
    private String lastName;

    @Schema(description = "Key worker's email address.")
    private String email;

    @Schema(description = "Identifier for Key worker image.")
    private Long thumbnailId;

    @Schema(required = true, description = "Key worker's allocation capacity.")
    @NotNull
    private Integer capacity;

    @Schema(required = true, description = "Number of offenders allocated to Key worker.")
    @NotNull
    private Integer numberAllocated;

    @Schema(description = "Key worker's schedule type.")
    private String scheduleType;

    @Schema(description = "Key worker's agency Id.")
    private String agencyId;

    @Schema(description = "Key worker's agency description.")
    private String agencyDescription;

    @Schema(description = "Key worker's status.")
    private KeyworkerStatus status;

    @Schema(description = "Key worker is eligible for auto allocation.")
    private Boolean autoAllocationAllowed;

    @Schema(description = "Date keyworker status should return to active. (returning from annual leave)")
    private LocalDate activeDate;

    @Schema(description = "Number of KW sessions in the time period specified")
    private Integer numKeyWorkerSessions;

    public KeyworkerDto(@NotNull Long staffId, @NotBlank String firstName, @NotBlank String lastName, String email, Long thumbnailId, @NotNull Integer capacity, @NotNull Integer numberAllocated, String scheduleType, String agencyId, String agencyDescription, KeyworkerStatus status, Boolean autoAllocationAllowed, LocalDate activeDate, Integer numKeyWorkerSessions) {
        this.staffId = staffId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.thumbnailId = thumbnailId;
        this.capacity = capacity;
        this.numberAllocated = numberAllocated;
        this.scheduleType = scheduleType;
        this.agencyId = agencyId;
        this.agencyDescription = agencyDescription;
        this.status = status;
        this.autoAllocationAllowed = autoAllocationAllowed;
        this.activeDate = activeDate;
        this.numKeyWorkerSessions = numKeyWorkerSessions;
    }

    public KeyworkerDto() {
    }

    public static KeyworkerDtoBuilder builder() {
        return new KeyworkerDtoBuilder();
    }

    public @NotNull Long getStaffId() {
        return this.staffId;
    }

    public void setStaffId(@NotNull Long staffId) {
        this.staffId = staffId;
    }

    public @NotBlank String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(@NotBlank String firstName) {
        this.firstName = firstName;
    }

    public @NotBlank String getLastName() {
        return this.lastName;
    }

    public void setLastName(@NotBlank String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getThumbnailId() {
        return this.thumbnailId;
    }

    public void setThumbnailId(Long thumbnailId) {
        this.thumbnailId = thumbnailId;
    }

    public @NotNull Integer getCapacity() {
        return this.capacity;
    }

    public void setCapacity(@NotNull Integer capacity) {
        this.capacity = capacity;
    }

    public @NotNull Integer getNumberAllocated() {
        return this.numberAllocated;
    }

    public void setNumberAllocated(@NotNull Integer numberAllocated) {
        this.numberAllocated = numberAllocated;
    }

    public String getScheduleType() {
        return this.scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getAgencyId() {
        return this.agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    public String getAgencyDescription() {
        return this.agencyDescription;
    }

    public void setAgencyDescription(String agencyDescription) {
        this.agencyDescription = agencyDescription;
    }

    public KeyworkerStatus getStatus() {
        return this.status;
    }

    public void setStatus(KeyworkerStatus status) {
        this.status = status;
    }

    public Boolean getAutoAllocationAllowed() {
        return this.autoAllocationAllowed;
    }

    public void setAutoAllocationAllowed(Boolean autoAllocationAllowed) {
        this.autoAllocationAllowed = autoAllocationAllowed;
    }

    public LocalDate getActiveDate() {
        return this.activeDate;
    }

    public void setActiveDate(LocalDate activeDate) {
        this.activeDate = activeDate;
    }

    public Integer getNumKeyWorkerSessions() {
        return this.numKeyWorkerSessions;
    }

    public void setNumKeyWorkerSessions(Integer numKeyWorkerSessions) {
        this.numKeyWorkerSessions = numKeyWorkerSessions;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof KeyworkerDto)) return false;
        final KeyworkerDto other = (KeyworkerDto) o;
        if (!other.canEqual(this)) return false;
        final Object this$staffId = this.getStaffId();
        final Object other$staffId = other.getStaffId();
        if (this$staffId == null ? other$staffId != null : !this$staffId.equals(other$staffId)) return false;
        final Object this$firstName = this.getFirstName();
        final Object other$firstName = other.getFirstName();
        if (this$firstName == null ? other$firstName != null : !this$firstName.equals(other$firstName)) return false;
        final Object this$lastName = this.getLastName();
        final Object other$lastName = other.getLastName();
        if (this$lastName == null ? other$lastName != null : !this$lastName.equals(other$lastName)) return false;
        final Object this$email = this.getEmail();
        final Object other$email = other.getEmail();
        if (this$email == null ? other$email != null : !this$email.equals(other$email)) return false;
        final Object this$thumbnailId = this.getThumbnailId();
        final Object other$thumbnailId = other.getThumbnailId();
        if (this$thumbnailId == null ? other$thumbnailId != null : !this$thumbnailId.equals(other$thumbnailId))
            return false;
        final Object this$capacity = this.getCapacity();
        final Object other$capacity = other.getCapacity();
        if (this$capacity == null ? other$capacity != null : !this$capacity.equals(other$capacity)) return false;
        final Object this$numberAllocated = this.getNumberAllocated();
        final Object other$numberAllocated = other.getNumberAllocated();
        if (this$numberAllocated == null ? other$numberAllocated != null : !this$numberAllocated.equals(other$numberAllocated))
            return false;
        final Object this$scheduleType = this.getScheduleType();
        final Object other$scheduleType = other.getScheduleType();
        if (this$scheduleType == null ? other$scheduleType != null : !this$scheduleType.equals(other$scheduleType))
            return false;
        final Object this$agencyId = this.getAgencyId();
        final Object other$agencyId = other.getAgencyId();
        if (this$agencyId == null ? other$agencyId != null : !this$agencyId.equals(other$agencyId)) return false;
        final Object this$agencyDescription = this.getAgencyDescription();
        final Object other$agencyDescription = other.getAgencyDescription();
        if (this$agencyDescription == null ? other$agencyDescription != null : !this$agencyDescription.equals(other$agencyDescription))
            return false;
        final Object this$status = this.getStatus();
        final Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
        final Object this$autoAllocationAllowed = this.getAutoAllocationAllowed();
        final Object other$autoAllocationAllowed = other.getAutoAllocationAllowed();
        if (this$autoAllocationAllowed == null ? other$autoAllocationAllowed != null : !this$autoAllocationAllowed.equals(other$autoAllocationAllowed))
            return false;
        final Object this$activeDate = this.getActiveDate();
        final Object other$activeDate = other.getActiveDate();
        if (this$activeDate == null ? other$activeDate != null : !this$activeDate.equals(other$activeDate))
            return false;
        final Object this$numKeyWorkerSessions = this.getNumKeyWorkerSessions();
        final Object other$numKeyWorkerSessions = other.getNumKeyWorkerSessions();
        return this$numKeyWorkerSessions == null ? other$numKeyWorkerSessions == null : this$numKeyWorkerSessions.equals(other$numKeyWorkerSessions);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof KeyworkerDto;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $staffId = this.getStaffId();
        result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
        final Object $firstName = this.getFirstName();
        result = result * PRIME + ($firstName == null ? 43 : $firstName.hashCode());
        final Object $lastName = this.getLastName();
        result = result * PRIME + ($lastName == null ? 43 : $lastName.hashCode());
        final Object $email = this.getEmail();
        result = result * PRIME + ($email == null ? 43 : $email.hashCode());
        final Object $thumbnailId = this.getThumbnailId();
        result = result * PRIME + ($thumbnailId == null ? 43 : $thumbnailId.hashCode());
        final Object $capacity = this.getCapacity();
        result = result * PRIME + ($capacity == null ? 43 : $capacity.hashCode());
        final Object $numberAllocated = this.getNumberAllocated();
        result = result * PRIME + ($numberAllocated == null ? 43 : $numberAllocated.hashCode());
        final Object $scheduleType = this.getScheduleType();
        result = result * PRIME + ($scheduleType == null ? 43 : $scheduleType.hashCode());
        final Object $agencyId = this.getAgencyId();
        result = result * PRIME + ($agencyId == null ? 43 : $agencyId.hashCode());
        final Object $agencyDescription = this.getAgencyDescription();
        result = result * PRIME + ($agencyDescription == null ? 43 : $agencyDescription.hashCode());
        final Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        final Object $autoAllocationAllowed = this.getAutoAllocationAllowed();
        result = result * PRIME + ($autoAllocationAllowed == null ? 43 : $autoAllocationAllowed.hashCode());
        final Object $activeDate = this.getActiveDate();
        result = result * PRIME + ($activeDate == null ? 43 : $activeDate.hashCode());
        final Object $numKeyWorkerSessions = this.getNumKeyWorkerSessions();
        result = result * PRIME + ($numKeyWorkerSessions == null ? 43 : $numKeyWorkerSessions.hashCode());
        return result;
    }

    public String toString() {
        return "KeyworkerDto(staffId=" + this.getStaffId() + ", thumbnailId=" + this.getThumbnailId() + ", capacity=" + this.getCapacity() + ", numberAllocated=" + this.getNumberAllocated() + ", scheduleType=" + this.getScheduleType() + ", agencyId=" + this.getAgencyId() + ", agencyDescription=" + this.getAgencyDescription() + ", status=" + this.getStatus() + ", autoAllocationAllowed=" + this.getAutoAllocationAllowed() + ", activeDate=" + this.getActiveDate() + ", numKeyWorkerSessions=" + this.getNumKeyWorkerSessions() + ")";
    }

    public static class KeyworkerDtoBuilder {
        private @NotNull Long staffId;
        private @NotBlank String firstName;
        private @NotBlank String lastName;
        private String email;
        private Long thumbnailId;
        private @NotNull Integer capacity;
        private @NotNull Integer numberAllocated;
        private String scheduleType;
        private String agencyId;
        private String agencyDescription;
        private KeyworkerStatus status;
        private Boolean autoAllocationAllowed;
        private LocalDate activeDate;
        private Integer numKeyWorkerSessions;

        KeyworkerDtoBuilder() {
        }

        public KeyworkerDto.KeyworkerDtoBuilder staffId(@NotNull Long staffId) {
            this.staffId = staffId;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder firstName(@NotBlank String firstName) {
            this.firstName = firstName;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder lastName(@NotBlank String lastName) {
            this.lastName = lastName;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder email(String email) {
            this.email = email;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder thumbnailId(Long thumbnailId) {
            this.thumbnailId = thumbnailId;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder capacity(@NotNull Integer capacity) {
            this.capacity = capacity;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder numberAllocated(@NotNull Integer numberAllocated) {
            this.numberAllocated = numberAllocated;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder scheduleType(String scheduleType) {
            this.scheduleType = scheduleType;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder agencyId(String agencyId) {
            this.agencyId = agencyId;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder agencyDescription(String agencyDescription) {
            this.agencyDescription = agencyDescription;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder status(KeyworkerStatus status) {
            this.status = status;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder autoAllocationAllowed(Boolean autoAllocationAllowed) {
            this.autoAllocationAllowed = autoAllocationAllowed;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder activeDate(LocalDate activeDate) {
            this.activeDate = activeDate;
            return this;
        }

        public KeyworkerDto.KeyworkerDtoBuilder numKeyWorkerSessions(Integer numKeyWorkerSessions) {
            this.numKeyWorkerSessions = numKeyWorkerSessions;
            return this;
        }

        public KeyworkerDto build() {
            return new KeyworkerDto(staffId, firstName, lastName, email, thumbnailId, capacity, numberAllocated, scheduleType, agencyId, agencyDescription, status, autoAllocationAllowed, activeDate, numKeyWorkerSessions);
        }

        public String toString() {
            return "KeyworkerDto.KeyworkerDtoBuilder(staffId=" + this.staffId + ", firstName=" + this.firstName + ", lastName=" + this.lastName + ", email=" + this.email + ", thumbnailId=" + this.thumbnailId + ", capacity=" + this.capacity + ", numberAllocated=" + this.numberAllocated + ", scheduleType=" + this.scheduleType + ", agencyId=" + this.agencyId + ", agencyDescription=" + this.agencyDescription + ", status=" + this.status + ", autoAllocationAllowed=" + this.autoAllocationAllowed + ", activeDate=" + this.activeDate + ", numKeyWorkerSessions=" + this.numKeyWorkerSessions + ")";
        }
    }
}
