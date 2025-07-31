package uk.gov.justice.digital.hmpps.keyworker.services.casenotes

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationCaseNote
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationCaseNoteRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventCount
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import java.time.LocalDate

@Service
class CaseNoteRetriever(
  private val acr: AllocationCaseNoteRepository,
) {
  fun findCaseNoteTotals(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): CaseNoteTotals =
    caseNoteTypes[AllocationContext.get().policy]
      ?.takeIf { it.isNotEmpty() }
      ?.let {
        acr.findByPrisonCodeAndCaseNoteTypeInAndOccurredAtBetween(
          prisonCode,
          it,
          from.atStartOfDay(),
          to.plusDays(1).atStartOfDay(),
        )
      }?.let {
        CaseNoteTotals.relatingTo(AllocationContext.get().policy)(it)
      } ?: CaseNoteTotals.empty()

  fun findCaseNoteSummaries(
    staffId: Set<Long>,
    from: LocalDate,
    to: LocalDate,
  ): Map<Long, CaseNoteSummaries> =
    caseNoteTypes[AllocationContext.get().policy]
      ?.let {
        acr.findByStaffIdInAndCaseNoteTypeInAndOccurredAtBetween(
          staffId,
          it,
          from.atStartOfDay(),
          to.plusDays(1).atStartOfDay(),
        )
      }?.takeIf { it.isNotEmpty() }
      ?.groupBy { it.staffId }
      ?.map { e ->
        e.key to
          CaseNoteSummaries(
            e.value.groupBy { it.personIdentifier }.map {
              CaseNoteSummary.relatingTo(AllocationContext.get().policy)(it.toPair())
            },
          )
      }?.toMap() ?: emptyMap()

  fun findCaseNoteSummaries(
    personIdentifiers: Set<String>,
    from: LocalDate,
    to: LocalDate,
  ): CaseNoteSummaries =
    caseNoteTypes[AllocationContext.get().policy]
      ?.let {
        acr.findByPersonIdentifierInAndCaseNoteTypeInAndCreatedAtBetween(
          personIdentifiers,
          it,
          from.atStartOfDay(),
          to.plusDays(1).atStartOfDay(),
        )
      }?.takeIf { it.isNotEmpty() }
      ?.let { list ->
        CaseNoteSummaries(
          list.groupBy { it.personIdentifier }.map {
            CaseNoteSummary.relatingTo(AllocationContext.get().policy)(it.toPair())
          },
        )
      } ?: CaseNoteSummaries.empty()

  fun findMostRecentCaseNoteBefore(
    prisonCode: String,
    personIdentifiers: Set<String>,
    date: LocalDate,
  ): Map<String, LatestNote> =
    acr
      .findLatestCaseNotesBefore(
        prisonCode,
        personIdentifiers,
        requireNotNull(recordedEventTypes[AllocationContext.get().policy]),
        date.atStartOfDay(),
      ).associate { it.personIdentifier to LatestNote(it.occurredAt) }

  companion object {
    private val caseNoteTypes =
      mapOf<AllocationPolicy, Set<CaseNoteTypeKey>>(
        AllocationPolicy.KEY_WORKER to
          setOf(
            CaseNoteTypeKey(KW_TYPE, KW_SESSION_SUBTYPE),
            CaseNoteTypeKey(KW_TYPE, KW_ENTRY_SUBTYPE),
          ),
        AllocationPolicy.PERSONAL_OFFICER to setOf(CaseNoteTypeKey(PO_ENTRY_TYPE, PO_ENTRY_SUBTYPE)),
      )

    private val recordedEventTypes =
      mapOf<AllocationPolicy, Set<CaseNoteTypeKey>>(
        AllocationPolicy.KEY_WORKER to setOf(CaseNoteTypeKey(KW_TYPE, KW_SESSION_SUBTYPE)),
        AllocationPolicy.PERSONAL_OFFICER to setOf(CaseNoteTypeKey(PO_ENTRY_TYPE, PO_ENTRY_SUBTYPE)),
      )
  }
}

class CaseNoteTotals(
  val sessionCount: Int?,
  val entryCount: Int?,
) {
  companion object {
    fun empty() = CaseNoteTotals(null, null)

    fun relatingTo(policy: AllocationPolicy): (List<AllocationCaseNote>) -> CaseNoteTotals =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> { list ->
          CaseNoteTotals(
            list.count { it.type == KW_TYPE && it.subType == KW_SESSION_SUBTYPE },
            list.count { it.type == KW_TYPE && it.subType == KW_ENTRY_SUBTYPE },
          )
        }

        AllocationPolicy.PERSONAL_OFFICER -> { list ->
          CaseNoteTotals(
            null,
            list.count { it.type == PO_ENTRY_TYPE && it.subType == PO_ENTRY_SUBTYPE },
          )
        }
      }
  }
}

class CaseNoteSummaries(
  summaries: List<CaseNoteSummary>,
) {
  private val data = summaries.associateBy { it.personIdentifier }

  val sessionCount = data.values.sumOf { it.sessionCount ?: 0 }
  val entryCount = data.values.sumOf { it.entryCount ?: 0 }
  val complianceCount = data.values.sumOf { it.complianceCount }

  fun recordedEventCount(): List<RecordedEventCount> =
    data.values
      .flatMap { cns -> cns.recordedEventCount }
      .groupBy { it.type }
      .map { e -> RecordedEventCount(e.key, e.value.sumOf { it.count }) }

  fun findByPersonIdentifier(personIdentifier: String): CaseNoteSummaries = CaseNoteSummaries(listOfNotNull(data[personIdentifier]))

  fun findLatestCaseNote(personIdentifier: String): AllocationCaseNote? = data[personIdentifier]?.latestOccurrence

  fun personIdentifiers(): Set<String> = data.keys

  companion object {
    fun empty() = CaseNoteSummaries(listOf(CaseNoteSummary.relatingTo(AllocationContext.get().policy)("" to emptyList())))
  }
}

interface CaseNoteSummary {
  val personIdentifier: String
  val caseNotes: List<AllocationCaseNote>
  val sessionCount: Int?
  val entryCount: Int?
  val complianceCount: Int
  val latestOccurrence: AllocationCaseNote?
  val recordedEventCount: List<RecordedEventCount>

  companion object {
    fun relatingTo(policy: AllocationPolicy): (Pair<String, List<AllocationCaseNote>>) -> CaseNoteSummary =
      {
        when (policy) {
          AllocationPolicy.KEY_WORKER -> KeyworkerCaseNoteSummary(it.first, it.second)
          AllocationPolicy.PERSONAL_OFFICER -> PersonOfficerCaseNoteSummary(it.first, it.second)
        }
      }
  }
}

class KeyworkerCaseNoteSummary(
  override val personIdentifier: String,
  override val caseNotes: List<AllocationCaseNote>,
) : CaseNoteSummary {
  override val sessionCount: Int = caseNotes.count { it.type == KW_TYPE && it.subType == KW_SESSION_SUBTYPE }
  override val entryCount: Int = caseNotes.count { it.type == KW_TYPE && it.subType == KW_ENTRY_SUBTYPE }
  override val complianceCount: Int = sessionCount
  override val latestOccurrence: AllocationCaseNote? =
    caseNotes.filter { it.type == KW_TYPE && it.subType == KW_SESSION_SUBTYPE }.maxByOrNull { it.occurredAt }
  override val recordedEventCount: List<RecordedEventCount> =
    listOf(
      RecordedEventCount(RecordedEventType.SESSION, sessionCount),
      RecordedEventCount(RecordedEventType.ENTRY, entryCount),
    )
}

class PersonOfficerCaseNoteSummary(
  override val personIdentifier: String,
  override val caseNotes: List<AllocationCaseNote>,
) : CaseNoteSummary {
  override val sessionCount: Int? = null
  override val entryCount: Int = caseNotes.count { it.type == PO_ENTRY_TYPE && it.subType == PO_ENTRY_SUBTYPE }
  override val complianceCount: Int = entryCount
  override val latestOccurrence: AllocationCaseNote? =
    caseNotes.filter { it.type == PO_ENTRY_TYPE && it.subType == PO_ENTRY_SUBTYPE }.maxByOrNull { it.occurredAt }
  override val recordedEventCount: List<RecordedEventCount> =
    listOf(
      RecordedEventCount(RecordedEventType.ENTRY, entryCount),
    )
}
