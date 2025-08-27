package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDateTime

data class RecordedEventResponse(
  val recordedEvents: List<StaffRecordedEvent>,
)

data class StaffRecordedEvent(
  val prisoner: RecordedEventPrisoner,
  val type: CodedDescription,
  val occurredAt: LocalDateTime,
  val text: String,
  val amendments: List<RecordedEventAmendment>,
)

data class RecordedEventAmendment(
  val createdAt: LocalDateTime,
  val authorDisplayName: String,
  val text: String,
)

data class RecordedEventPrisoner(
  val prisonerNumber: String,
  val firstName: String,
  val lastName: String,
)
