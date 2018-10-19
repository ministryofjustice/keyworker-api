package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(of = {"prisonId"})
public class PrisonKeyWorkerAgregatedStats {

    private final String prisonId;
    private final Long numberKeyWorkeringSessions;
    private final Long numberKeyWorkerEntries;
    private final Double numberOfActiveKeyworkers;
    private final Double numPrisonersAssignedKeyWorker;
    private final Double totalNumPrisoners;
    private final Double avgNumDaysFromReceptionToAlliocationDays;
    private final Double avgNumDaysFromReceptionToKeyWorkingSession;

    public PrisonKeyWorkerAgregatedStats(String prisonId,
                                         Long numberKeyWorkeringSessions,
                                         Long numberKeyWorkerEntries,
                                         Double numberOfActiveKeyworkers,
                                         Double numPrisonersAssignedKeyWorker,
                                         Double totalNumPrisoners,
                                         Double avgNumDaysFromReceptionToAlliocationDays,
                                         Double avgNumDaysFromReceptionToKeyWorkingSession) {
        this.prisonId = prisonId;
        this.numberKeyWorkeringSessions = numberKeyWorkeringSessions;
        this.numberKeyWorkerEntries = numberKeyWorkerEntries;
        this.numberOfActiveKeyworkers = numberOfActiveKeyworkers;
        this.numPrisonersAssignedKeyWorker = numPrisonersAssignedKeyWorker;
        this.totalNumPrisoners = totalNumPrisoners;
        this.avgNumDaysFromReceptionToAlliocationDays = avgNumDaysFromReceptionToAlliocationDays;
        this.avgNumDaysFromReceptionToKeyWorkingSession = avgNumDaysFromReceptionToKeyWorkingSession;
    }
}
