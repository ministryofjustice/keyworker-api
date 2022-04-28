package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;

import java.time.LocalDate;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocationsFilterDto {
    private String prisonId;
    private Optional<AllocationType> allocationType;
    private Optional<LocalDate> fromDate;
    private LocalDate toDate;

    public AllocationsFilterDto(String prisonId, Optional<AllocationType> allocationType, Optional<LocalDate> fromDate, LocalDate toDate) {
        this.prisonId = prisonId;
        this.allocationType = allocationType;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public AllocationsFilterDto() {
    }

    public static AllocationsFilterDtoBuilder builder() {
        return new AllocationsFilterDtoBuilder();
    }

    public String getPrisonId() {
        return this.prisonId;
    }

    public void setPrisonId(String prisonId) {
        this.prisonId = prisonId;
    }

    public Optional<AllocationType> getAllocationType() {
        return this.allocationType;
    }

    public void setAllocationType(Optional<AllocationType> allocationType) {
        this.allocationType = allocationType;
    }

    public Optional<LocalDate> getFromDate() {
        return this.fromDate;
    }

    public void setFromDate(Optional<LocalDate> fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return this.toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof AllocationsFilterDto)) return false;
        final AllocationsFilterDto other = (AllocationsFilterDto) o;
        if (!other.canEqual(this)) return false;
        final Object this$prisonId = this.getPrisonId();
        final Object other$prisonId = other.getPrisonId();
        if (this$prisonId == null ? other$prisonId != null : !this$prisonId.equals(other$prisonId)) return false;
        final Object this$allocationType = this.getAllocationType();
        final Object other$allocationType = other.getAllocationType();
        if (this$allocationType == null ? other$allocationType != null : !this$allocationType.equals(other$allocationType))
            return false;
        final Object this$fromDate = this.getFromDate();
        final Object other$fromDate = other.getFromDate();
        if (this$fromDate == null ? other$fromDate != null : !this$fromDate.equals(other$fromDate)) return false;
        final Object this$toDate = this.getToDate();
        final Object other$toDate = other.getToDate();
        return this$toDate == null ? other$toDate == null : this$toDate.equals(other$toDate);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof AllocationsFilterDto;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $prisonId = this.getPrisonId();
        result = result * PRIME + ($prisonId == null ? 43 : $prisonId.hashCode());
        final Object $allocationType = this.getAllocationType();
        result = result * PRIME + ($allocationType == null ? 43 : $allocationType.hashCode());
        final Object $fromDate = this.getFromDate();
        result = result * PRIME + ($fromDate == null ? 43 : $fromDate.hashCode());
        final Object $toDate = this.getToDate();
        result = result * PRIME + ($toDate == null ? 43 : $toDate.hashCode());
        return result;
    }

    public String toString() {
        return "AllocationsFilterDto(prisonId=" + this.getPrisonId() + ", allocationType=" + this.getAllocationType() + ", fromDate=" + this.getFromDate() + ", toDate=" + this.getToDate() + ")";
    }

    public static class AllocationsFilterDtoBuilder {
        private String prisonId;
        private Optional<AllocationType> allocationType;
        private Optional<LocalDate> fromDate;
        private LocalDate toDate;

        AllocationsFilterDtoBuilder() {
        }

        public AllocationsFilterDto.AllocationsFilterDtoBuilder prisonId(String prisonId) {
            this.prisonId = prisonId;
            return this;
        }

        public AllocationsFilterDto.AllocationsFilterDtoBuilder allocationType(Optional<AllocationType> allocationType) {
            this.allocationType = allocationType;
            return this;
        }

        public AllocationsFilterDto.AllocationsFilterDtoBuilder fromDate(Optional<LocalDate> fromDate) {
            this.fromDate = fromDate;
            return this;
        }

        public AllocationsFilterDto.AllocationsFilterDtoBuilder toDate(LocalDate toDate) {
            this.toDate = toDate;
            return this;
        }

        public AllocationsFilterDto build() {
            return new AllocationsFilterDto(prisonId, allocationType, fromDate, toDate);
        }

        public String toString() {
            return "AllocationsFilterDto.AllocationsFilterDtoBuilder(prisonId=" + this.prisonId + ", allocationType=" + this.allocationType + ", fromDate=" + this.fromDate + ", toDate=" + this.toDate + ")";
        }
    }
}
