package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatisticRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.dto.StatSummary
import uk.gov.justice.digital.hmpps.keyworker.services.Statistic.percentage
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class PrisonStatsService(
  private val prisonConfig: PrisonConfigurationRepository,
  private val statistics: PrisonStatisticRepository,
) {
  fun getPrisonStats(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): PrisonStats {
    val dateRange = DAYS.between(from, to.plusDays(1))
    val previousTo = from.minusDays(1)
    val previousFrom = previousTo.minusDays(dateRange)
    val prisonConfig = prisonConfig.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val allStats = statistics.findAllByPrisonCodeAndDateBetween(prisonCode, previousFrom, to)
    val current = allStats.filter { it.date in (from..to) }.asStats(prisonConfig.frequencyInWeeks)
    val previous =
      allStats
        .filter { it.date in (previousFrom..previousTo) }
        .asStats(prisonConfig.frequencyInWeeks)

    return PrisonStats(
      prisonCode,
      current,
      previous,
      prisonConfig.hasPrisonersWithHighComplexityNeeds,
    )
  }
}

private fun List<PrisonStatistic>.asStats(sessionFrequency: Int): StatSummary? {
  if (isEmpty()) return null
  val from = start()
  val to = end()
  val eligible = averageEligiblePrisoners()
  val assignedKeyworker = map { it.assignedKeyworker }.average().toInt()
  val sessions = totalSessions()
  val projectedSessions = projectedSessions(eligible, from, to, sessionFrequency)
  return StatSummary(
    from,
    to,
    map { it.totalPrisoners }.average().toInt(),
    map { it.highComplexityOfNeedPrisoners }.average().toInt(),
    eligible,
    assignedKeyworker,
    map { it.activeKeyworkers }.average().toInt(),
    sessions,
    sumOf { it.keyworkerEntries },
    mapNotNull { it.averageReceptionToAllocationDays }.average().toInt(),
    mapNotNull { it.averageReceptionToSessionDays }.average().toInt(),
    projectedSessions.toInt(),
    percentage(assignedKeyworker, eligible),
    percentage(sessions, projectedSessions) ?: 0.0,
  )
}

private fun List<PrisonStatistic>.start() = minOf { it.date }

private fun List<PrisonStatistic>.end() = maxOf { it.date }

private fun List<PrisonStatistic>.averageEligiblePrisoners() = map { it.eligiblePrisoners }.average().toInt()

private fun List<PrisonStatistic>.totalSessions() = sumOf { it.keyworkerSessions }

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
