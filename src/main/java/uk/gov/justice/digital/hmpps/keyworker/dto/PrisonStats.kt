package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate

data class PrisonStats(
  val prisonCode: String,
  val current: StatSummary?,
  val previous: StatSummary?,
  val hasPrisonersWithHighComplexityOfNeed: Boolean,
)

data class StatSummary(
  val from: LocalDate,
  val to: LocalDate,
  val totalPrisoners: Int,
  val highComplexityOfNeedPrisoners: Int,
  val eligiblePrisoners: Int,
  val prisonersAssignedKeyworker: Int,
  val activeKeyworkers: Int,
  val keyworkerSessions: Int,
  val keyworkerEntries: Int,
  val avgReceptionToAllocationDays: Int?,
  val avgReceptionToSessionDays: Int?,
  val projectedSessions: Int,
  val percentageWithKeyworker: Double?,
  val compliance: Double,
)

data class WeeklyStatInt(
  val date: LocalDate,
  val value: Int,
)

data class WeeklyStatDbl(
  val date: LocalDate,
  val value: Double,
)
