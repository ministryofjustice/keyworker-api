package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration.Companion.NOT_CONFIGURED
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.dto.StatSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.WeeklyStatDbl
import uk.gov.justice.digital.hmpps.keyworker.dto.WeeklyStatInt
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfig
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonStatisticRepository
import java.math.BigDecimal
import java.math.RoundingMode.HALF_EVEN
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters.previousOrSame
import kotlin.math.roundToInt

@Service
class PrisonService(
  private val prisonConfig: PrisonConfigRepository,
  private val statistics: PrisonStatisticRepository,
) {
  fun getPrisonKeyworkerStatus(prisonCode: String) = prisonConfig.findByIdOrNull(prisonCode).keyworkerStatus()

  fun getPrisonStats(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): PrisonStats {
    val prisonConfig = prisonConfig.findByIdOrNull(prisonCode) ?: PrisonConfig.default(prisonCode)
    val lastYear = statistics.findAllByPrisonCodeAndDateBetween(prisonCode, to.minusYears(1), to)
    val current = lastYear.filter { it.date in (from..to) }.asStats(prisonConfig.kwSessionFrequencyInWeeks)
    val previousFrom = from.minusDays(DAYS.between(from, to))
    val previousTo = from.minusDays(1)
    val previous =
      lastYear
        .filter { it.date in (previousFrom..previousTo) }
        .asStats(prisonConfig.kwSessionFrequencyInWeeks)

    val sessionTimeline =
      lastYear
        .groupBy { it.date.with(previousOrSame(DayOfWeek.SUNDAY)) }
        .map { WeeklyStatInt(it.key, it.value.sumOf { it.keyworkerSessions }) }
        .sortedBy { it.date }

    val averageSessions = sessionTimeline.map { it.value }.average().toInt()

    val complianceTimeline =
      lastYear
        .groupBy { it.date.with(previousOrSame(DayOfWeek.SUNDAY)) }
        .map {
          WeeklyStatDbl(
            it.key,
            with(it.value) {
              percentage(
                totalSessions(),
                projectedSessions(averageEligiblePrisoners(), start(), end(), prisonConfig.kwSessionFrequencyInWeeks) ?: 0,
              )
            },
          )
        }.sortedBy { it.date }

    val averageCompliance =
      complianceTimeline
        .mapNotNull { it.value }
        .average()
        .toBigDecimal()
        .setScale(2, HALF_EVEN)
        .toDouble()

    return PrisonStats(
      prisonCode,
      current,
      previous,
      sessionTimeline,
      averageSessions,
      complianceTimeline,
      averageCompliance,
    )
  }
}

private fun PrisonConfig?.keyworkerStatus(): PrisonKeyworkerConfiguration =
  this?.let {
    PrisonKeyworkerConfiguration(
      it.migrated,
      it.hasPrisonersWithHighComplexityNeeds,
      it.autoAllocate,
      it.capacityTier1,
      it.capacityTier2,
      it.kwSessionFrequencyInWeeks,
    )
  } ?: NOT_CONFIGURED

private fun List<PrisonStatistic>.asStats(sessionFrequency: Int): StatSummary =
  let {
    val from = start()
    val to = end()
    val eligible = averageEligiblePrisoners()
    val assignedKeyworker = map { it.assignedKeyworker }.average().toInt()
    val sessions = totalSessions()
    val projectedSessions = projectedSessions(eligible, from, to, sessionFrequency)
    StatSummary(
      from,
      to,
      map { it.totalPrisoners }.average().toInt(),
      eligible,
      assignedKeyworker,
      map { it.activeKeyworkers }.average().toInt(),
      sessions,
      sumOf { it.keyworkerEntries },
      mapNotNull { it.averageReceptionToAllocationDays }.average().toInt(),
      mapNotNull { it.averageReceptionToSessionDays }.average().toInt(),
      projectedSessions,
      percentage(assignedKeyworker, eligible),
      percentage(sessions, projectedSessions ?: 0),
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
  sessionFrequency: Int,
) = if (eligible == 0) null else (eligible * (DAYS.between(from, to) + 1.0 / (sessionFrequency * 7.0))).roundToInt()

private fun percentage(
  numerator: Int,
  denominator: Int,
): Double? =
  if (numerator == 0 || denominator == 0) {
    null
  } else {
    BigDecimal(numerator.toDouble() / denominator.toDouble() * 100).setScale(2, HALF_EVEN).toDouble()
  }
