package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.TRANSFER_TYPE
import java.time.LocalDate
import java.time.LocalDateTime

enum class DateType {
  CREATED_AT,
}

data class UsageByPersonIdentifierRequest(
  val personIdentifiers: Set<String>,
  val typeSubTypes: Set<TypeSubTypeRequest>,
  val from: LocalDateTime? = null,
  val to: LocalDateTime? = null,
  val authorIds: Set<String> = setOf(),
  val prisonCode: String? = null,
  val dateType: DateType = DateType.CREATED_AT,
) {
  companion object {
    fun keyworkerTypes(
      prisonCode: String?,
      personIdentifiers: Set<String>,
      from: LocalDate,
      to: LocalDate = from.plusDays(1),
      authorIds: Set<String> = setOf(),
    ): UsageByPersonIdentifierRequest =
      UsageByPersonIdentifierRequest(
        personIdentifiers,
        setOf(TypeSubTypeRequest(KW_TYPE, setOf(KW_ENTRY_SUBTYPE, KW_SESSION_SUBTYPE))),
        from = from.atStartOfDay(),
        to = to.atStartOfDay(),
        authorIds = authorIds,
        prisonCode = prisonCode,
      )

    fun personalOfficerTypes(
      prisonCode: String?,
      personIdentifiers: Set<String>,
      from: LocalDate,
      to: LocalDate = from.plusDays(1),
      authorIds: Set<String> = setOf(),
    ): UsageByPersonIdentifierRequest =
      UsageByPersonIdentifierRequest(
        personIdentifiers,
        setOf(TypeSubTypeRequest(PO_ENTRY_TYPE, setOf(PO_ENTRY_SUBTYPE))),
        from = from.atStartOfDay(),
        to = to.atStartOfDay(),
        authorIds = authorIds,
        prisonCode = prisonCode,
      )

    fun sessionTypes(
      prisonCode: String?,
      personIdentifiers: Set<String>,
      from: LocalDate,
      to: LocalDate = from.plusDays(1),
      staffIds: Set<String> = setOf(),
    ): UsageByPersonIdentifierRequest =
      UsageByPersonIdentifierRequest(
        personIdentifiers,
        setOf(TypeSubTypeRequest(KW_TYPE, setOf(KW_SESSION_SUBTYPE))),
        from = from.atStartOfDay(),
        to = to.atStartOfDay(),
        prisonCode = prisonCode,
        authorIds = staffIds,
      )

    fun transferTypes(
      prisonCode: String?,
      personIdentifiers: Set<String>,
      from: LocalDate,
      to: LocalDate,
    ): UsageByPersonIdentifierRequest =
      UsageByPersonIdentifierRequest(
        personIdentifiers,
        setOf(TypeSubTypeRequest(TRANSFER_TYPE, setOf())),
        from = from.atStartOfDay(),
        to = to.atStartOfDay(),
        prisonCode = prisonCode,
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
  val from: LocalDateTime? = null,
  val to: LocalDateTime? = null,
  val prisonCode: String? = null,
  val dateType: DateType = DateType.CREATED_AT,
) {
  companion object {
    fun lastMonthSessions(authorIds: Set<String>): UsageByAuthorIdRequest =
      UsageByAuthorIdRequest(
        authorIds,
        setOf(TypeSubTypeRequest(KW_TYPE, setOf(KW_SESSION_SUBTYPE))),
        LocalDate.now().atStartOfDay().minusMonths(1),
        LocalDate.now().atStartOfDay(),
      )

    fun lastMonthEntries(authorIds: Set<String>): UsageByAuthorIdRequest {
      val entryConfig = AllocationContext.get().policy.entryConfig
      return UsageByAuthorIdRequest(
        authorIds,
        setOf(TypeSubTypeRequest(entryConfig.type, setOf(entryConfig.subType))),
        LocalDate.now().atStartOfDay().minusMonths(1),
        LocalDate.now().atStartOfDay(),
      )
    }
  }
}

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
  val keyworkerSessions: Int
  val keyworkerEntries: Int
  val poEntries: Int

  init {
    val grouped = data.values.flatten().groupBy { it.type to it.subType }
    keyworkerSessions = grouped[KW_TYPE to KW_SESSION_SUBTYPE]?.sumOf { it.count } ?: 0
    keyworkerEntries = grouped[KW_TYPE to KW_ENTRY_SUBTYPE]?.sumOf { it.count } ?: 0
    poEntries = grouped[PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE]?.sumOf { it.count } ?: 0
  }

  fun totalEntries(policy: AllocationPolicy) =
    when (policy) {
      AllocationPolicy.KEY_WORKER -> keyworkerEntries
      AllocationPolicy.PERSONAL_OFFICER -> poEntries
    }

  fun findSessionDate(personIdentifier: String): LocalDate? =
    data[personIdentifier]
      ?.find { it.subType == KW_SESSION_SUBTYPE }
      ?.latestNote
      ?.occurredAt
      ?.toLocalDate()

  fun findTransferDate(personIdentifier: String): LocalDate? =
    data[personIdentifier]
      ?.find { it.type == TRANSFER_TYPE }
      ?.latestNote
      ?.occurredAt
      ?.toLocalDate()

  fun personIdentifiersWithSessions() =
    data.values
      .flatten()
      .filter { it.subType == KW_SESSION_SUBTYPE }
      .map { it.personIdentifier }
      .toSet()
}
