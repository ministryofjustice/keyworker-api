package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.SESSION_SUBTYPE
import java.time.LocalDate
import java.time.LocalDateTime

data class UsageByPersonIdentifierRequest(
  val personIdentifiers: Set<String>,
  val typeSubTypes: Set<TypeSubTypeRequest>,
  val occurredFrom: LocalDateTime? = null,
  val occurredTo: LocalDateTime? = null,
  val authorIds: Set<String> = setOf(),
) {
  companion object {
    fun keyworkerTypes(
      personIdentifiers: Set<String>,
      from: LocalDate,
      to: LocalDate = from.plusDays(1),
    ): UsageByPersonIdentifierRequest =
      UsageByPersonIdentifierRequest(
        personIdentifiers,
        setOf(TypeSubTypeRequest(KW_TYPE, setOf(ENTRY_SUBTYPE, SESSION_SUBTYPE))),
        occurredFrom = from.atStartOfDay(),
        occurredTo = to.atStartOfDay(),
      )

    fun sessionTypes(
      personIdentifiers: Set<String>,
      from: LocalDate,
      to: LocalDate = from.plusDays(1),
    ): UsageByPersonIdentifierRequest =
      UsageByPersonIdentifierRequest(
        personIdentifiers,
        setOf(TypeSubTypeRequest(KW_TYPE, setOf(SESSION_SUBTYPE))),
        occurredFrom = from.atStartOfDay(),
        occurredTo = to.atStartOfDay(),
      )
  }
}

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

fun NoteUsageResponse<UsageByPersonIdentifierResponse>.summary(): CaseNoteSummary = CaseNoteSummary(content)

data class CaseNoteSummary(
  private val data: Map<String, List<UsageByPersonIdentifierResponse>>,
) {
  val totalSessions: Int
  val totalEntries: Int

  init {
    val grouped = data.values.flatten().groupBy { it.type to it.subType }
    totalSessions = grouped[KW_TYPE to SESSION_SUBTYPE]?.sumOf { it.count } ?: 0
    totalEntries = grouped[KW_TYPE to ENTRY_SUBTYPE]?.sumOf { it.count } ?: 0
  }

  fun findSessionDate(personIdentifier: String): LocalDate? =
    data[personIdentifier]
      ?.find { it.subType == SESSION_SUBTYPE }
      ?.latestNote
      ?.occurredAt
      ?.toLocalDate()

  fun personIdentifiersWithSessions() =
    data.values
      .flatten()
      .filter { it.subType == SESSION_SUBTYPE }
      .map { it.personIdentifier }
      .toSet()

  companion object {
    const val KW_TYPE = "KA"
    const val SESSION_SUBTYPE = "KS"
    const val ENTRY_SUBTYPE = "KE"
  }
}
