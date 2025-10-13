package uk.gov.justice.digital.hmpps.keyworker.services.recordedevents

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventCount
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import java.time.LocalDate
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEvent as RecordedEventEntity

@Service
class RecordedEventRetriever(
  private val rer: RecordedEventRepository,
) {
  fun findCaseNoteTotals(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): RecordedEventTotals =
    rer
      .findByPrisonCodeAndOccurredAtBetween(
        prisonCode,
        from.atStartOfDay(),
        to.plusDays(1).atStartOfDay(),
      ).let {
        RecordedEventTotals.relatingTo(AllocationContext.get().requiredPolicy())(it)
      }

  fun findRecordedEventSummaries(
    prisonCode: String,
    staffIds: Set<Long>,
    from: LocalDate,
    to: LocalDate,
  ): Map<Long, RecordedEventSummaries> =
    rer
      .findByStaffIdInAndOccurredAtBetween(
        prisonCode,
        staffIds,
        from.atStartOfDay(),
        to.plusDays(1).atStartOfDay(),
      ).takeIf { it.isNotEmpty() }
      ?.groupBy { it.staffId }
      ?.map { e ->
        e.key to
          RecordedEventSummaries(
            e.value.groupBy { it.personIdentifier }.map {
              RecordedEventSummary.relatingTo(AllocationContext.get().requiredPolicy())(it.toPair())
            },
          )
      }?.toMap() ?: emptyMap()

  fun findRecordedEventSummaries(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): RecordedEventSummaries =
    rer
      .findByPrisonCodeAndCreatedAtBetween(
        prisonCode,
        from.atStartOfDay(),
        to.plusDays(1).atStartOfDay(),
      ).takeIf { it.isNotEmpty() }
      ?.let { list ->
        RecordedEventSummaries(
          list.groupBy { it.personIdentifier }.map {
            RecordedEventSummary.relatingTo(AllocationContext.get().requiredPolicy())(it.toPair())
          },
        )
      } ?: RecordedEventSummaries.empty()

  fun findMostRecentEventBefore(
    prisonCode: String,
    personIdentifiers: Set<String>,
    date: LocalDate,
    type: RecordedEventType,
  ): Map<String, LatestNote> =
    rer
      .findLatestRecordedEventBefore(
        prisonCode,
        personIdentifiers,
        ReferenceDataDomain.RECORDED_EVENT_TYPE of type.name,
        date.atStartOfDay(),
      ).associate { it.personIdentifier to LatestNote(it.occurredAt) }
}

class RecordedEventTotals(
  val sessionCount: Int?,
  val entryCount: Int?,
) {
  companion object {
    fun empty() = RecordedEventTotals(null, null)

    fun relatingTo(policy: AllocationPolicy): (List<RecordedEventEntity>) -> RecordedEventTotals =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> { list ->
          RecordedEventTotals(
            list.count { it.type.code == RecordedEventType.SESSION.name },
            list.count { it.type.code == RecordedEventType.ENTRY.name },
          )
        }

        AllocationPolicy.PERSONAL_OFFICER -> { list ->
          RecordedEventTotals(
            null,
            list.count { it.type.code == RecordedEventType.ENTRY.name },
          )
        }
      }
  }
}

class RecordedEventSummaries(
  summaries: List<RecordedEventSummary>,
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

  fun findByPersonIdentifier(personIdentifier: String): RecordedEventSummaries =
    RecordedEventSummaries(listOfNotNull(data[personIdentifier]))

  fun findLatestRecordedEvent(personIdentifier: String): RecordedEventEntity? = data[personIdentifier]?.latestOccurrence

  fun personIdentifiers(): Set<String> = data.keys

  companion object {
    fun empty() =
      RecordedEventSummaries(listOf(RecordedEventSummary.relatingTo(AllocationContext.get().requiredPolicy())("" to emptyList())))
  }
}

interface RecordedEventSummary {
  val personIdentifier: String
  val caseNotes: List<RecordedEventEntity>
  val sessionCount: Int?
  val entryCount: Int?
  val complianceCount: Int
  val latestOccurrence: RecordedEventEntity?
  val recordedEventCount: List<RecordedEventCount>

  companion object {
    fun relatingTo(policy: AllocationPolicy): (Pair<String, List<RecordedEventEntity>>) -> RecordedEventSummary =
      {
        when (policy) {
          AllocationPolicy.KEY_WORKER -> KeyworkerRecordedEventSummary(it.first, it.second)
          AllocationPolicy.PERSONAL_OFFICER -> PersonOfficerRecordedEventSummary(it.first, it.second)
        }
      }
  }
}

class KeyworkerRecordedEventSummary(
  override val personIdentifier: String,
  override val caseNotes: List<RecordedEventEntity>,
) : RecordedEventSummary {
  override val sessionCount: Int = caseNotes.count { it.type.code == RecordedEventType.SESSION.name }
  override val entryCount: Int = caseNotes.count { it.type.code == RecordedEventType.ENTRY.name }
  override val complianceCount: Int = sessionCount
  override val latestOccurrence: RecordedEventEntity? =
    caseNotes.filter { it.type.code == RecordedEventType.SESSION.name }.maxByOrNull { it.occurredAt }
  override val recordedEventCount: List<RecordedEventCount> =
    listOf(
      RecordedEventCount(RecordedEventType.SESSION, sessionCount),
      RecordedEventCount(RecordedEventType.ENTRY, entryCount),
    )
}

class PersonOfficerRecordedEventSummary(
  override val personIdentifier: String,
  override val caseNotes: List<RecordedEventEntity>,
) : RecordedEventSummary {
  override val sessionCount: Int? = null
  override val entryCount: Int = caseNotes.count { it.type.code == RecordedEventType.ENTRY.name }
  override val complianceCount: Int = entryCount
  override val latestOccurrence: RecordedEventEntity? =
    caseNotes.filter { it.type.code == RecordedEventType.ENTRY.name }.maxByOrNull { it.occurredAt }
  override val recordedEventCount: List<RecordedEventCount> =
    listOf(
      RecordedEventCount(RecordedEventType.ENTRY, entryCount),
    )
}
