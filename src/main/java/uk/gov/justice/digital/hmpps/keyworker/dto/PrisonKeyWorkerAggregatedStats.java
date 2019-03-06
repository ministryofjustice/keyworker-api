package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@EqualsAndHashCode(of = {"prisonId"})
public class PrisonKeyWorkerAggregatedStats {

    private final String prisonId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Long numberKeyWorkerSessions;
    private final Long numberKeyWorkerEntries;
    private final Double numberOfActiveKeyworkers;
    private final Double numPrisonersAssignedKeyWorker;
    private final Double totalNumPrisoners;
    private final Double avgNumDaysFromReceptionToAllocationDays;
    private final Double avgNumDaysFromReceptionToKeyWorkingSession;

    public PrisonKeyWorkerAggregatedStats(final String prisonId,
                                          final LocalDate startDate,
                                          final LocalDate endDate,
                                          final Long numberKeyWorkerSessions,
                                          final Long numberKeyWorkerEntries,
                                          final Double numberOfActiveKeyworkers,
                                          final Double numPrisonersAssignedKeyWorker,
                                          final Double totalNumPrisoners,
                                          final Double avgNumDaysFromReceptionToAllocationDays,
                                          final Double avgNumDaysFromReceptionToKeyWorkingSession) {
        this.prisonId = prisonId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberKeyWorkerSessions = numberKeyWorkerSessions;
        this.numberKeyWorkerEntries = numberKeyWorkerEntries;
        this.numberOfActiveKeyworkers = numberOfActiveKeyworkers;
        this.numPrisonersAssignedKeyWorker = numPrisonersAssignedKeyWorker;
        this.totalNumPrisoners = totalNumPrisoners;
        this.avgNumDaysFromReceptionToAllocationDays = avgNumDaysFromReceptionToAllocationDays;
        this.avgNumDaysFromReceptionToKeyWorkingSession = avgNumDaysFromReceptionToKeyWorkingSession;
    }
}
