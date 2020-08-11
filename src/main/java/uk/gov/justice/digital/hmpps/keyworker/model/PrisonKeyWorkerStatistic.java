package uk.gov.justice.digital.hmpps.keyworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.hibernate.validator.constraints.Length;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
@Table(name = "KEYWORKER_STATS")
@Data()
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = {"prisonId", "snapshotDate"})
public class PrisonKeyWorkerStatistic implements Comparable<PrisonKeyWorkerStatistic> {

    @Id()
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name = "KEYWORKER_STATS_ID", nullable = false)
    private Long id;

    @NotNull
    @Length(max = 6)
    @Column(name = "PRISON_ID", nullable = false)
    private String prisonId;

    @NotNull
    @Column(name = "SNAPSHOT_DATE", nullable = false)
    private LocalDate snapshotDate;

    @NotNull
    @Column(name = "NUM_PRISONERS_ASSIGNED_KW", nullable = false)
    private Integer numPrisonersAssignedKeyWorker;

    @NotNull
    @Column(name = "TOTAL_NUM_PRISONERS", nullable = false)
    private Integer totalNumPrisoners;

    @Column(name = "NUM_KW_SESSIONS")
    private Integer numberKeyWorkerSessions;

    @Column(name = "NUM_KW_ENTRIES")
    private Integer numberKeyWorkerEntries;

    @Column(name = "NUM_ACTIVE_KEYWORKERS")
    private Integer numberOfActiveKeyworkers;

    @Column(name = "RECPT_TO_ALLOC_DAYS")
    private Integer avgNumDaysFromReceptionToAllocationDays;

    @Column(name = "RECPT_TO_KW_SESSION_DAYS")
    private Integer avgNumDaysFromReceptionToKeyWorkingSession;

    @Override
    public int compareTo(final PrisonKeyWorkerStatistic stat) {
        return new CompareToBuilder()
                        .append(prisonId, stat.prisonId)
                        .append(snapshotDate, stat.snapshotDate)
                        .toComparison();
    }
}
