package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate

data class KeyworkerDetails(
  val keyworker: KeyworkerWithSchedule,
  val status: CodedDescription,
  val prison: CodedDescription,
  val capacity: Int,
  val allocated: Int,
  val allocations: List<KeyworkerPrisoner>,
  val stats: KeyworkerStats,
  val allowAutoAllocation: Boolean,
  val reactivateOn: LocalDate?,
)

data class KeyworkerWithSchedule(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val scheduleType: CodedDescription,
)

data class KeyworkerPrisoner(
  val prisoner: Prisoner,
  val latestSession: LatestKeyworkerSession?,
)

data class LatestKeyworkerSession(
  val occurredAt: LocalDate,
)

data class KeyworkerStats(
  val current: KeyworkerSessionStats,
  val previous: KeyworkerSessionStats,
)

data class KeyworkerSessionStats(
  val from: LocalDate,
  val to: LocalDate,
  val projectedSessions: Int,
  val recordedSessions: Int,
  val recordedEntries: Int,
  val complianceRate: Double,
)
