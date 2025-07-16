package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;

import java.time.LocalDate;
import java.util.List;

public interface LegacyPrisonStatisticRepository extends CrudRepository<PrisonKeyWorkerStatistic, Long> {

    List<PrisonKeyWorkerStatistic> findByPrisonIdInAndSnapshotDateBetween(List<String> prisonIds, LocalDate fromDate, LocalDate toDate);

    @Query("select new uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats(s.prisonId, " +
            "min(s.snapshotDate), " +
            "max(s.snapshotDate), " +
            "sum(s.numberKeyWorkerSessions), " +
            "sum(s.numberKeyWorkerEntries), " +
            "avg(s.numberOfActiveKeyworkers), " +
            "avg(s.numPrisonersAssignedKeyWorker), " +
            "avg(s.totalNumPrisoners), " +
            "avg(s.totalNumEligiblePrisoners), " +
            "avg(s.avgNumDaysFromReceptionToAllocationDays), " +
            "avg(s.avgNumDaysFromReceptionToKeyWorkingSession)) " +
            "from PrisonKeyWorkerStatistic s " +
            "where s.prisonId IN (:prisonIds) " +
            "and s.snapshotDate between :fromDate and :toDate "+
            "group by s.prisonId ")
    List<PrisonKeyWorkerAggregatedStats> getAggregatedData(@Param("prisonIds") List<String> prisonIds, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

}
