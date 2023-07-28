package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Entity
@Table(name = "PRISON_SUPPORTED")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"prisonId"})
public class PrisonSupported {

    @Id()
    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_ID", nullable = false)
    private String prisonId;

    @Column(name = "MIGRATED", nullable = false)
    private boolean migrated;

    @Column(name = "AUTO_ALLOCATE", nullable = false)
    private boolean autoAllocate;

    @Column(name = "MIGRATED_DATE_TIME")
    private LocalDateTime migratedDateTime;

    @Column(name = "CAPACITY_TIER_1", nullable = false)
    private int capacityTier1;

    @Column(name = "CAPACITY_TIER_2")
    private int capacityTier2;

    @Column(name = "KW_SESSION_FREQ_WEEKS", nullable = false)
    private int kwSessionFrequencyInWeeks;
}
