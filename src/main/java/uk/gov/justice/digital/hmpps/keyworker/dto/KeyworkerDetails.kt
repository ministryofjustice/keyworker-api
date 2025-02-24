package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class KeyworkerDetails(
  val keyworker: Keyworker,
  val status: CodedDescription,
  val prison: CodedDescription,
  val capacity: Int,
  val allocated: Int,
  val scheduleType: CodedDescription,
  val allocations: List<Allocation>,
)

data class Keyworker(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
)

data class Allocation(
  val prisoner: Prisoner,
  val location: String,
  val releaseDate: LocalDate?,
  val latestSession: LatestKeyworkerSession?,
)

data class Prisoner(
  val prisonNumber: String,
  val firstName: String,
  val lastName: String,
)

data class LatestKeyworkerSession(
  val occurredAt: LocalDateTime,
)
