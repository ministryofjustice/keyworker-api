package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OffenderKeyworkerDto {

    @Schema(required = true, description = "Id of offender allocation.")
    @NotNull
    private Long offenderKeyworkerId;

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

    public OffenderKeyworkerDto(@NotNull Long offenderKeyworkerId, @NotBlank String offenderNo, @NotNull Long staffId, @NotBlank String agencyId, @NotNull LocalDateTime assigned, LocalDateTime expired, @NotBlank String userId, @NotBlank String active) {
        this.offenderKeyworkerId = offenderKeyworkerId;
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

    public @NotNull Long getOffenderKeyworkerId() {
        return this.offenderKeyworkerId;
    }

    public @NotBlank String getOffenderNo() {
        return this.offenderNo;
    }

    public @NotNull Long getStaffId() {
        return this.staffId;
    }

    public @NotBlank String getAgencyId() {
        return this.agencyId;
    }

    public @NotNull LocalDateTime getAssigned() {
        return this.assigned;
    }

    public LocalDateTime getExpired() {
        return this.expired;
    }

    public @NotBlank String getUserId() {
        return this.userId;
    }

    public @NotBlank String getActive() {
        return this.active;
    }

    public void setOffenderKeyworkerId(@NotNull Long offenderKeyworkerId) {
        this.offenderKeyworkerId = offenderKeyworkerId;
    }

    public void setOffenderNo(@NotBlank String offenderNo) {
        this.offenderNo = offenderNo;
    }

    public void setStaffId(@NotNull Long staffId) {
        this.staffId = staffId;
    }

    public void setAgencyId(@NotBlank String agencyId) {
        this.agencyId = agencyId;
    }

    public void setAssigned(@NotNull LocalDateTime assigned) {
        this.assigned = assigned;
    }

    public void setExpired(LocalDateTime expired) {
        this.expired = expired;
    }

    public void setUserId(@NotBlank String userId) {
        this.userId = userId;
    }

    public void setActive(@NotBlank String active) {
        this.active = active;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof OffenderKeyworkerDto)) return false;
        final OffenderKeyworkerDto other = (OffenderKeyworkerDto) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$offenderKeyworkerId = this.getOffenderKeyworkerId();
        final Object other$offenderKeyworkerId = other.getOffenderKeyworkerId();
        if (this$offenderKeyworkerId == null ? other$offenderKeyworkerId != null : !this$offenderKeyworkerId.equals(other$offenderKeyworkerId))
            return false;
        final Object this$offenderNo = this.getOffenderNo();
        final Object other$offenderNo = other.getOffenderNo();
        if (this$offenderNo == null ? other$offenderNo != null : !this$offenderNo.equals(other$offenderNo))
            return false;
        final Object this$staffId = this.getStaffId();
        final Object other$staffId = other.getStaffId();
        if (this$staffId == null ? other$staffId != null : !this$staffId.equals(other$staffId)) return false;
        final Object this$agencyId = this.getAgencyId();
        final Object other$agencyId = other.getAgencyId();
        if (this$agencyId == null ? other$agencyId != null : !this$agencyId.equals(other$agencyId)) return false;
        final Object this$assigned = this.getAssigned();
        final Object other$assigned = other.getAssigned();
        if (this$assigned == null ? other$assigned != null : !this$assigned.equals(other$assigned)) return false;
        final Object this$expired = this.getExpired();
        final Object other$expired = other.getExpired();
        if (this$expired == null ? other$expired != null : !this$expired.equals(other$expired)) return false;
        final Object this$userId = this.getUserId();
        final Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
        final Object this$active = this.getActive();
        final Object other$active = other.getActive();
        if (this$active == null ? other$active != null : !this$active.equals(other$active)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof OffenderKeyworkerDto;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $offenderKeyworkerId = this.getOffenderKeyworkerId();
        result = result * PRIME + ($offenderKeyworkerId == null ? 43 : $offenderKeyworkerId.hashCode());
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

    public String toString() {
        return "OffenderKeyworkerDto(offenderKeyworkerId=" + this.getOffenderKeyworkerId() + ", offenderNo=" + this.getOffenderNo() + ", staffId=" + this.getStaffId() + ", agencyId=" + this.getAgencyId() + ", assigned=" + this.getAssigned() + ", expired=" + this.getExpired() + ", userId=" + this.getUserId() + ", active=" + this.getActive() + ")";
    }

    public static class OffenderKeyworkerDtoBuilder {
        private @NotNull Long offenderKeyworkerId;
        private @NotBlank String offenderNo;
        private @NotNull Long staffId;
        private @NotBlank String agencyId;
        private @NotNull LocalDateTime assigned;
        private LocalDateTime expired;
        private @NotBlank String userId;
        private @NotBlank String active;

        OffenderKeyworkerDtoBuilder() {
        }

        public OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder offenderKeyworkerId(@NotNull Long offenderKeyworkerId) {
            this.offenderKeyworkerId = offenderKeyworkerId;
            return this;
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
            return new OffenderKeyworkerDto(offenderKeyworkerId, offenderNo, staffId, agencyId, assigned, expired, userId, active);
        }

        public String toString() {
            return "OffenderKeyworkerDto.OffenderKeyworkerDtoBuilder(offenderKeyworkerId=" + this.offenderKeyworkerId + ", offenderNo=" + this.offenderNo + ", staffId=" + this.staffId + ", agencyId=" + this.agencyId + ", assigned=" + this.assigned + ", expired=" + this.expired + ", userId=" + this.userId + ", active=" + this.active + ")";
        }
    }
}
