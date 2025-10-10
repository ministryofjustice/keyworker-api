package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType.ENTRY
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType.SESSION
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import java.time.LocalDateTime

enum class DateType {
  CREATED_AT,
  OCCURRED_AT,
}

data class UsageByPersonIdentifierRequest(
  val personIdentifiers: Set<String>,
  val typeSubTypes: Set<TypeSubTypeRequest>,
  val from: LocalDateTime? = null,
  val to: LocalDateTime? = null,
  val authorIds: Set<String> = setOf(),
  val prisonCode: String? = null,
  val dateType: DateType = DateType.OCCURRED_AT,
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
  val from: LocalDateTime? = null,
  val to: LocalDateTime? = null,
  val prisonCode: String? = null,
  val dateType: DateType = DateType.CREATED_AT,
)

data class UsageByAuthorIdResponse(
  val authorId: String,
  val type: String,
  val subType: String,
  val count: Int,
  val latestNote: LatestNote? = null,
)

data class TypeSubTypeRequest(
  val type: String,
  val subTypes: Set<String> = setOf(),
)

data class LatestNote(
  val occurredAt: LocalDateTime,
)

data class NoteUsageResponse<T>(
  val content: Map<String, List<T>>,
)

fun NoteUsageResponse<UsageByPersonIdentifierResponse>.summary(): CaseNoteFromApiSummary = CaseNoteFromApiSummary(content)

data class CaseNoteFromApiSummary(
  private val data: Map<String, List<UsageByPersonIdentifierResponse>>,
) {
  val keyworkerSessions: Int
  val keyworkerEntries: Int
  val poEntries: Int

  init {
    val grouped = data.values.flatten().groupBy { it.type to it.subType }
    keyworkerSessions = grouped[KW_TYPE to KW_SESSION_SUBTYPE]?.sumOf { it.count } ?: 0
    keyworkerEntries = grouped[KW_TYPE to KW_ENTRY_SUBTYPE]?.sumOf { it.count } ?: 0
    poEntries = grouped[PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE]?.sumOf { it.count } ?: 0
  }

  fun getRecordedFor(
    policy: AllocationPolicy,
    personIdentifier: String,
    prison: Prison,
  ): RecordedFor? {
    val (reType, cnType) =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> SESSION to (KW_TYPE to KW_SESSION_SUBTYPE)
        AllocationPolicy.PERSONAL_OFFICER -> ENTRY to (PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE)
      }
    return findLatestFor(personIdentifier, cnType.first, cnType.second)?.let { RecordedFor(prison, reType, it, policy) }
  }

  private fun findLatestFor(
    personIdentifier: String,
    type: String,
    subType: String,
  ): LocalDateTime? =
    data[personIdentifier]
      ?.find { it.type == type && it.subType == subType }
      ?.latestNote
      ?.occurredAt
}

data class RecordedFor(
  val prison: Prison,
  val type: RecordedEventType,
  val lastOccurredAt: LocalDateTime,
  val policy: AllocationPolicy,
)
