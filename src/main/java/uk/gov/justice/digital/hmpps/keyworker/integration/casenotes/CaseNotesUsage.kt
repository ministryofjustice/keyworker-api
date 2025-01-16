package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import java.time.LocalDateTime

data class UsageByPersonIdentifierRequest(
  val personIdentifiers: Set<String>,
  val typeSubTypes: Set<TypeSubTypeRequest>,
  val occurredFrom: LocalDateTime? = null,
  val occurredTo: LocalDateTime? = null,
  val authorIds: Set<String> = setOf(),
)

data class UsageByPersonIdentifierResponse(
  val personIdentifier: String,
  val type: String,
  val subType: String,
  val count: Int,
  val latestNote: LatestNote? = null,
)

data class UsageByAuthorIdRequest(
  val authorIds: Set<String>,
  val typeSubTypes: Set<TypeSubTypeRequest>,
  val occurredFrom: LocalDateTime? = null,
  val occurredTo: LocalDateTime? = null,
)

data class UsageByAuthorIdResponse(
  val authorId: String,
  val type: String,
  val subType: String,
  val count: Int,
  val latestNote: LatestNote? = null,
)

data class TypeSubTypeRequest(val type: String, val subTypes: Set<String> = setOf())

data class LatestNote(val occurredAt: LocalDateTime)

data class NoteUsageResponse<T>(val content: Map<String, List<T>>)
