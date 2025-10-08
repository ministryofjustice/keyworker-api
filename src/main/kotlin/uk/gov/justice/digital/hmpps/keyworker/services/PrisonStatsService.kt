package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatisticRepository
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonStatSummary
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventCount
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.services.Statistic.percentage
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventRetriever
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class PrisonStatsService(
  private val prisonConfig: PrisonConfigurationRepository,
  private val statistics: PrisonStatisticRepository,
  private val recordedEventRetriever: RecordedEventRetriever,
) {
  fun getPrisonStats(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
    comparisonFrom: LocalDate,
    comparisonTo: LocalDate,
  ): PrisonStats {
    val prisonConfig = prisonConfig.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val allStats = statistics.findAllByPrisonCodeAndDateBetween(prisonCode, comparisonFrom, to)
    val current = allStats.filter { it.date in (from..to) }.asStats(prisonConfig, from, to)
    val previous =
      allStats
        .filter { it.date in (comparisonFrom..comparisonTo) }
        .asStats(prisonConfig, comparisonFrom, comparisonTo)

    return PrisonStats(
      prisonCode,
      current,
      previous,
      prisonConfig.hasPrisonersWithHighComplexityNeeds,
    )
  }

  private fun List<PrisonStatistic>.asStats(
    config: PrisonConfiguration,
    from: LocalDate,
    to: LocalDate,
  ): PrisonStatSummary {
    val eligiblePrisoners = averageEligiblePrisoners()
    val prisonersAssigned = map { it.prisonersAssignedCount }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val projectedSessions = eligiblePrisoners?.let { projectedSessions(it, from, to, config.frequencyInWeeks) }
    val cnTotals = recordedEventRetriever.findCaseNoteTotals(config.code, from, to)
    return PrisonStatSummary(
      from,
      to,
      map { it.prisonerCount }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
      map { it.highComplexityOfNeedPrisonerCount }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
      eligiblePrisoners,
      prisonersAssigned,
      map { it.eligibleStaffCount }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
      listOfNotNull(
        cnTotals.sessionCount?.let { RecordedEventCount(RecordedEventType.SESSION, it) },
        cnTotals.entryCount?.let { RecordedEventCount(RecordedEventType.ENTRY, it) },
      ),
      mapNotNull { it.receptionToAllocationDays }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
      mapNotNull { it.receptionToRecordedEventDays }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
      projectedSessions,
      prisonersAssigned?.let { percentage(it, eligiblePrisoners) },
      cnTotals.sessionCount?.let { percentage(it, projectedSessions) },
    )
  }

  private fun List<PrisonStatistic>.averageEligiblePrisoners() =
    map { it.eligiblePrisonerCount }.takeIf { it.isNotEmpty() }?.average()?.toInt()

  private fun List<PrisonStatistic>.projectedSessions(
    eligible: Int,
    from: LocalDate,
    to: LocalDate,
    sessionFrequencyInWeeks: Int,
  ): Int =
    if (eligible == 0) {
      0
    } else {
      (
        eligible * (DAYS.between(from, to.plusDays(1)) / (sessionFrequencyInWeeks * 7.0))
      ).toInt()
    }
}
