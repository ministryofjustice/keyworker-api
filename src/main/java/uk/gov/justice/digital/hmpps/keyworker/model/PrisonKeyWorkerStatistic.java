package uk.gov.justice.digital.hmpps.keyworker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.val;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.hibernate.annotations.TenantId;
import org.hibernate.validator.constraints.Length;
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy;

@Entity
@Table(name = "PRISON_STATISTIC")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"prisonId", "snapshotDate"})
public class PrisonKeyWorkerStatistic implements Comparable<PrisonKeyWorkerStatistic> {

    @Id()
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_CODE", nullable = false)
    private String prisonId;

    @NotNull
    @Column(name = "STATISTIC_DATE", nullable = false)
    private LocalDate snapshotDate;

    @NotNull
    @Column(name = "PRISONERS_ASSIGNED_COUNT", nullable = false)
    private Integer numPrisonersAssignedKeyWorker;

    @NotNull
    @Column(name = "PRISONER_COUNT", nullable = false)
    private Integer totalNumPrisoners;

    @NotNull
    @Column(name = "ELIGIBLE_PRISONER_COUNT", nullable = false)
    private Integer totalNumEligiblePrisoners;

    @Column(name = "RECORDED_SESSION_COUNT")
    private Integer numberKeyWorkerSessions;

    @Column(name = "RECORDED_ENTRY_COUNT")
    private Integer numberKeyWorkerEntries;

    @Column(name = "ELIGIBLE_STAFF_COUNT")
    private Integer numberOfActiveKeyworkers;

    @Column(name = "RECEPTION_TO_ALLOCATION_DAYS")
    private Integer avgNumDaysFromReceptionToAllocationDays;

    @Column(name = "RECEPTION_TO_RECORDED_EVENT_DAYS")
    private Integer avgNumDaysFromReceptionToKeyWorkingSession;

    @TenantId
    @Column(name = "policy_code", updatable = false)
    private String policy;

    @Override
    public int compareTo(final PrisonKeyWorkerStatistic stat) {
        return new CompareToBuilder()
                        .append(prisonId, stat.prisonId)
                        .append(snapshotDate, stat.snapshotDate)
                        .toComparison();
    }
}
