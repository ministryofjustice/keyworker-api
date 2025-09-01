package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate

data class PrisonStats(
  val prisonCode: String,
  val current: PrisonStatSummary?,
  val previous: PrisonStatSummary?,
  val hasPrisonersWithHighComplexityOfNeed: Boolean,
)

data class PrisonStatSummary(
  val from: LocalDate,
  val to: LocalDate,
  val totalPrisoners: Int?,
  val highComplexityOfNeedPrisoners: Int?,
  val eligiblePrisoners: Int?,
  val prisonersAssigned: Int?,
  val eligibleStaff: Int?,
  val recordedEvents: List<RecordedEventCount>,
  val avgReceptionToAllocationDays: Int?,
  val avgReceptionToRecordedEventDays: Int?,
  val projectedRecordedEvents: Int?,
  val percentageAssigned: Double?,
  val recordedEventComplianceRate: Double?,
)
