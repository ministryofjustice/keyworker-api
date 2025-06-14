package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;
import org.hibernate.envers.Audited;
import org.hibernate.validator.constraints.Length;
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy;
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator;

import java.util.UUID;

@Audited(withModifiedFlag = true)
@Entity
@Table(name = "PRISON_CONFIGURATION")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LegacyPrisonConfiguration {

    @Audited(withModifiedFlag = false)
    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_CODE", nullable = false)
    private String prisonCode;

    @Audited(withModifiedFlag = true, modifiedColumnName = "is_enabled_modified")
    @Column(name = "IS_ENABLED", nullable = false)
    private boolean enabled;

    @Column(name = "ALLOW_AUTO_ALLOCATION", nullable = false)
    private boolean allowAutoAllocation;

    @Column(name = "CAPACITY", nullable = false)
    private int capacity;

    @Column(name = "MAXIMUM_CAPACITY")
    private int maximumCapacity;

    @Column(name = "FREQUENCY_IN_WEEKS", nullable = false)
    private int frequencyInWeeks;

    @Column(name = "HAS_PRISONERS_WITH_HIGH_COMPLEXITY_NEEDS", nullable = false)
    private boolean hasPrisonersWithHighComplexityNeeds;

    @TenantId
    @Audited(withModifiedFlag = false)
    @Column(name = "POLICY_CODE", updatable = false)
    private final String policy = AllocationPolicy.KEY_WORKER.name();

    @Id
    @Audited(withModifiedFlag = false)
    private final UUID id = IdGenerator.INSTANCE.newUuid();

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasPrisonersWithHighComplexityNeeds() {
        return hasPrisonersWithHighComplexityNeeds;
    }

    public boolean isAllowAutoAllocation() {
        return allowAutoAllocation;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getMaximumCapacity() {
        return maximumCapacity;
    }

    public int getFrequencyInWeeks() {
        return frequencyInWeeks;
    }

    public String getPolicy() { return policy; }
}
