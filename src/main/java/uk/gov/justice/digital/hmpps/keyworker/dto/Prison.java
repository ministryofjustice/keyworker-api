package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@ApiModel(description = "Prison")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Prison {
    @ApiModelProperty(required = true, value = "Identifies prison.", example = "MDI", position = 0)
    @NotBlank
    private String prisonId;

    @ApiModelProperty(required = true, value = "Indicates that Key working is supported in this prison", example = "true", position = 1)
    @NotBlank
    private boolean supported;

    @ApiModelProperty(required = true, value = "Indicates that Key Worker data has been migrated to the Key Worker Service", example = "true", position = 2)
    @NotBlank
    private boolean migrated;

    @ApiModelProperty(required = true, value = "Indicates that this prison supports auto allocation of prisoner to key workers", example = "true", position = 3)
    @NotBlank
    private boolean autoAllocatedSupported;

    @ApiModelProperty(required = true, value = "Default auto allocation amount for staff in this prison.", example = "6", position = 4)
    @NotBlank
    private int capacityTier1;

    @ApiModelProperty(required = true, value = "Over allocation amount per staff member (max)", example = "9", position = 5)
    @NotBlank
    private int capacityTier2;

    @ApiModelProperty(required = true, value = "Frequency of Key working sessions in this prison", example = "1", position = 6)
    @NotBlank
    private int kwSessionFrequencyInWeeks;

    @ApiModelProperty(required = true, value = "Date and time migration of key workers was done for this prison", example = "2018-10-02T01:12:55.000", position = 7)
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime migratedDateTime;

    public Prison(@NotBlank String prisonId, @NotBlank boolean supported, @NotBlank boolean migrated, @NotBlank boolean autoAllocatedSupported, @NotBlank int capacityTier1, @NotBlank int capacityTier2, @NotBlank int kwSessionFrequencyInWeeks, @NotBlank LocalDateTime migratedDateTime) {
        this.prisonId = prisonId;
        this.supported = supported;
        this.migrated = migrated;
        this.autoAllocatedSupported = autoAllocatedSupported;
        this.capacityTier1 = capacityTier1;
        this.capacityTier2 = capacityTier2;
        this.kwSessionFrequencyInWeeks = kwSessionFrequencyInWeeks;
        this.migratedDateTime = migratedDateTime;
    }

    public Prison() {
    }

    public static PrisonBuilder builder() {
        return new PrisonBuilder();
    }

    public @NotBlank String getPrisonId() {
        return this.prisonId;
    }

    public @NotBlank boolean isSupported() {
        return this.supported;
    }

    public @NotBlank boolean isMigrated() {
        return this.migrated;
    }

    public @NotBlank boolean isAutoAllocatedSupported() {
        return this.autoAllocatedSupported;
    }

    public @NotBlank int getCapacityTier1() {
        return this.capacityTier1;
    }

    public @NotBlank int getCapacityTier2() {
        return this.capacityTier2;
    }

    public @NotBlank int getKwSessionFrequencyInWeeks() {
        return this.kwSessionFrequencyInWeeks;
    }

    public @NotBlank LocalDateTime getMigratedDateTime() {
        return this.migratedDateTime;
    }

    public void setPrisonId(@NotBlank String prisonId) {
        this.prisonId = prisonId;
    }

    public void setSupported(@NotBlank boolean supported) {
        this.supported = supported;
    }

    public void setMigrated(@NotBlank boolean migrated) {
        this.migrated = migrated;
    }

    public void setAutoAllocatedSupported(@NotBlank boolean autoAllocatedSupported) {
        this.autoAllocatedSupported = autoAllocatedSupported;
    }

    public void setCapacityTier1(@NotBlank int capacityTier1) {
        this.capacityTier1 = capacityTier1;
    }

    public void setCapacityTier2(@NotBlank int capacityTier2) {
        this.capacityTier2 = capacityTier2;
    }

    public void setKwSessionFrequencyInWeeks(@NotBlank int kwSessionFrequencyInWeeks) {
        this.kwSessionFrequencyInWeeks = kwSessionFrequencyInWeeks;
    }

    public void setMigratedDateTime(@NotBlank LocalDateTime migratedDateTime) {
        this.migratedDateTime = migratedDateTime;
    }

    public String toString() {
        return "Prison(prisonId=" + this.getPrisonId() + ", supported=" + this.isSupported() + ", migrated=" + this.isMigrated() + ", autoAllocatedSupported=" + this.isAutoAllocatedSupported() + ", capacityTier1=" + this.getCapacityTier1() + ", capacityTier2=" + this.getCapacityTier2() + ", kwSessionFrequencyInWeeks=" + this.getKwSessionFrequencyInWeeks() + ", migratedDateTime=" + this.getMigratedDateTime() + ")";
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Prison)) return false;
        final Prison other = (Prison) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$prisonId = this.getPrisonId();
        final Object other$prisonId = other.getPrisonId();
        if (this$prisonId == null ? other$prisonId != null : !this$prisonId.equals(other$prisonId)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Prison;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $prisonId = this.getPrisonId();
        result = result * PRIME + ($prisonId == null ? 43 : $prisonId.hashCode());
        return result;
    }

    public static class PrisonBuilder {
        private @NotBlank String prisonId;
        private @NotBlank boolean supported;
        private @NotBlank boolean migrated;
        private @NotBlank boolean autoAllocatedSupported;
        private @NotBlank int capacityTier1;
        private @NotBlank int capacityTier2;
        private @NotBlank int kwSessionFrequencyInWeeks;
        private @NotBlank LocalDateTime migratedDateTime;

        PrisonBuilder() {
        }

        public Prison.PrisonBuilder prisonId(@NotBlank String prisonId) {
            this.prisonId = prisonId;
            return this;
        }

        public Prison.PrisonBuilder supported(@NotBlank boolean supported) {
            this.supported = supported;
            return this;
        }

        public Prison.PrisonBuilder migrated(@NotBlank boolean migrated) {
            this.migrated = migrated;
            return this;
        }

        public Prison.PrisonBuilder autoAllocatedSupported(@NotBlank boolean autoAllocatedSupported) {
            this.autoAllocatedSupported = autoAllocatedSupported;
            return this;
        }

        public Prison.PrisonBuilder capacityTier1(@NotBlank int capacityTier1) {
            this.capacityTier1 = capacityTier1;
            return this;
        }

        public Prison.PrisonBuilder capacityTier2(@NotBlank int capacityTier2) {
            this.capacityTier2 = capacityTier2;
            return this;
        }

        public Prison.PrisonBuilder kwSessionFrequencyInWeeks(@NotBlank int kwSessionFrequencyInWeeks) {
            this.kwSessionFrequencyInWeeks = kwSessionFrequencyInWeeks;
            return this;
        }

        public Prison.PrisonBuilder migratedDateTime(@NotBlank LocalDateTime migratedDateTime) {
            this.migratedDateTime = migratedDateTime;
            return this;
        }

        public Prison build() {
            return new Prison(prisonId, supported, migrated, autoAllocatedSupported, capacityTier1, capacityTier2, kwSessionFrequencyInWeeks, migratedDateTime);
        }

        public String toString() {
            return "Prison.PrisonBuilder(prisonId=" + this.prisonId + ", supported=" + this.supported + ", migrated=" + this.migrated + ", autoAllocatedSupported=" + this.autoAllocatedSupported + ", capacityTier1=" + this.capacityTier1 + ", capacityTier2=" + this.capacityTier2 + ", kwSessionFrequencyInWeeks=" + this.kwSessionFrequencyInWeeks + ", migratedDateTime=" + this.migratedDateTime + ")";
        }
    }
}
