package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OffenderKeyworkerDto {

    @Schema(required = true, description = "The offender's unique offender number (aka NOMS Number in the UK).")
    @NotBlank
    private String offenderNo;

    @Schema(required = true, description = "The offender's Key worker.")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Prison Id where allocation is effective.")
    @NotBlank
    private String agencyId;

    @Schema(required = true, description = "The date and time of the allocation.")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @Schema(required = false, description = "The date and time of deallocation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expired;

    @Schema(required = true, description = "The user who created the allocation.")
    @NotBlank
    private String userId;

    @Schema(required = true, description = "Whether allocation is active.")
    @NotBlank
    private String active;

    public OffenderKeyworkerDto(@NotBlank String offenderNo, @NotNull Long staffId, @NotBlank String agencyId, @NotNull LocalDateTime assigned, LocalDateTime expired, @NotBlank String userId, @NotBlank String active) {
        this.offenderNo = offenderNo;
        this.staffId = staffId;
        this.agencyId = agencyId;
        this.assigned = assigned;
        this.expired = expired;
        this.userId = userId;
        this.active = active;
    }

    public OffenderKeyworkerDto() {
    }

    public static OffenderKeyworkerDtoBuilder builder() {
        return new OffenderKeyworkerDtoBuilder();
    }

    public @NotBlank String getOffenderNo() {
        return this.offenderNo;
    }

    public void setOffenderNo(@NotBlank String offenderNo) {
        this.offenderNo = offenderNo;
    }

    public @NotNull Long getStaffId() {
        return this.staffId;
    }

    public void setStaffId(@NotNull Long staffId) {
        this.staffId = staffId;
    }

    public @NotBlank String getAgencyId() {
        return this.agencyId;
    }

    public void setAgencyId(@NotBlank String agencyId) {
        this.agencyId = agencyId;
    }

    public @NotNull LocalDateTime getAssigned() {
        return this.assigned;
    }

    public void setAssigned(@NotNull LocalDateTime assigned) {
        this.assigned = assigned;
    }

    public LocalDateTime getExpired() {
        return this.expired;
    }

    public void setExpired(LocalDateTime expired) {
        this.expired = expired;
    }

    public @NotBlank String getUserId() {
        return this.userId;
    }

    public void setUserId(@NotBlank String userId) {
        this.userId = userId;
    }

    public @NotBlank String getActive() {
        return this.active;
    }

    public void setActive(@NotBlank String active) {
        this.active = active;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof OffenderKeyworkerDto)) return false;
        final OffenderKeyworkerDto other = (OffenderKeyworkerDto) o;
        if (!other.canEqual(this)) return false;
        final Object this$offenderNo = this.getOffenderNo();
        final Object other$offenderNo = other.getOffenderNo();
        if (!Objects.equals(this$offenderNo, other$offenderNo))
            return false;
        final Object this$staffId = this.getStaffId();
        final Object other$staffId = other.getStaffId();
        if (!Objects.equals(this$staffId, other$staffId)) return false;
        final Object this$agencyId = this.getAgencyId();
        final Object other$agencyId = other.getAgencyId();
        if (!Objects.equals(this$agencyId, other$agencyId)) return false;
        final Object this$assigned = this.getAssigned();
        final Object other$assigned = other.getAssigned();
        if (!Objects.equals(this$assigned, other$assigned)) return false;
        final Object this$expired = this.getExpired();
        final Object other$expired = other.getExpired();
        if (!Objects.equals(this$expired, other$expired)) return false;
        final Object this$userId = this.getUserId();
        final Object other$userId = other.getUserId();
        if (!Objects.equals(this$userId, other$userId)) return false;
        final Object this$active = this.getActive();
        final Object other$active = other.getActive();
        return Objects.equals(this$active, other$active);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof OffenderKeyworkerDto;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $offenderNo = this.getOffenderNo();
        result = result * PRIME + ($offenderNo == null ? 43 : $offenderNo.hashCode());
        final Object $staffId = this.getStaffId();
        result = result * PRIME + ($staffId == null ? 43 : $staffId.hashCode());
        final Object $agencyId = this.getAgencyId();
        result = result * PRIME + ($agencyId == null ? 43 : $agencyId.hashCode());
        final Object $assigned = this.getAssigned();
        result = result * PRIME + ($assigned == null ? 43 : $assigned.hashCode());
        final Object $expired = this.getExpired();
        result = result * PRIME + ($expired == null ? 43 : $expired.hashCode());
        final Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        final Object $active = this.getActive();
        result = result * PRIME + ($active == null ? 43 : $active.hashCode());
        return result;
    }

    public static class OffenderKeyworkerDtoBuilder {
        private @NotBlank String offenderNo;
        private @NotNull Long staffId;
        private @NotBlank String agencyId;
        private @NotNull LocalDateTime assigned;
        private LocalDateTime expired;
        private @NotBlank String userId;
        private @NotBlank String active;

        OffenderKeyworkerDtoBuilder() {
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder offenderNo(@NotBlank String offenderNo) {
            this.offenderNo = offenderNo;
            return this;
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder staffId(@NotNull Long staffId) {
            this.staffId = staffId;
            return this;
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder agencyId(@NotBlank String agencyId) {
            this.agencyId = agencyId;
            return this;
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder assigned(@NotNull LocalDateTime assigned) {
            this.assigned = assigned;
            return this;
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder expired(LocalDateTime expired) {
            this.expired = expired;
            return this;
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder userId(@NotBlank String userId) {
            this.userId = userId;
            return this;
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder active(@NotBlank String active) {
            this.active = active;
            return this;
        }

        public OffenderKeyworkerDto build() {
            return new OffenderKeyworkerDto(offenderNo, staffId, agencyId, assigned, expired, userId, active);
        }
    }
}
