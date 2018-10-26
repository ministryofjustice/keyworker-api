package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;

import java.time.LocalDate;
import java.util.List;

public interface PrisonKeyWorkerStatisticRepository extends CrudRepository<PrisonKeyWorkerStatistic, Long> {

    List<PrisonKeyWorkerStatistic> findByPrisonIdAndSnapshotDateBetween(String prisonId, LocalDate fromDate, LocalDate toDate);

    @Query("select new uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats(s.prisonId, " +
            "min(s.snapshotDate), " +
            "max(s.snapshotDate), " +
            "sum(s.numberKeyWorkeringSessions), " +
            "sum(s.numberKeyWorkerEntries), " +
            "avg(s.numberOfActiveKeyworkers), " +
            "avg(s.numPrisonersAssignedKeyWorker), " +
            "avg(s.totalNumPrisoners), " +
            "avg(s.avgNumDaysFromReceptionToAlliocationDays), " +
            "avg(s.avgNumDaysFromReceptionToKeyWorkingSession)) " +
            "from PrisonKeyWorkerStatistic s " +
            "where s.prisonId = :prisonId " +
            "and s.snapshotDate between :fromDate and :toDate "+
            "group by s.prisonId ")
    List<PrisonKeyWorkerAggregatedStats> getAggregatedData(@Param("prisonId") String prisonId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
