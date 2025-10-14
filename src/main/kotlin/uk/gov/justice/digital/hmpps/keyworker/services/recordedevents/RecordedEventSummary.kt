package uk.gov.justice.digital.hmpps.keyworker.services.recordedevents

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventCount
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType

interface RecordedEventSummary {
  val personIdentifier: String
  val recordedEvents: List<RecordedEvent>
  val sessionCount: Int?
  val entryCount: Int?
  val complianceCount: Int
  val latestOccurrence: RecordedEvent?
  val recordedEventCount: List<RecordedEventCount>

  companion object {
    operator fun invoke(
      personIdentifier: String,
      recordedEvents: List<RecordedEvent>,
    ) = when (AllocationContext.get().requiredPolicy()) {
      AllocationPolicy.KEY_WORKER -> KeyworkerRecordedEventSummary(personIdentifier, recordedEvents)
      AllocationPolicy.PERSONAL_OFFICER -> PersonOfficerRecordedEventSummary(personIdentifier, recordedEvents)
    }
  }
}

class KeyworkerRecordedEventSummary(
  override val personIdentifier: String,
  override val recordedEvents: List<RecordedEvent>,
) : RecordedEventSummary {
  override val sessionCount: Int = recordedEvents.count { it.type.code == RecordedEventType.SESSION.name }
  override val entryCount: Int = recordedEvents.count { it.type.code == RecordedEventType.ENTRY.name }
  override val complianceCount: Int = sessionCount
  override val latestOccurrence: RecordedEvent? =
    recordedEvents.filter { it.type.code == RecordedEventType.SESSION.name }.maxByOrNull { it.occurredAt }
  override val recordedEventCount: List<RecordedEventCount> =
    listOf(
      RecordedEventCount(RecordedEventType.SESSION, sessionCount),
      RecordedEventCount(RecordedEventType.ENTRY, entryCount),
    )
}

class PersonOfficerRecordedEventSummary(
  override val personIdentifier: String,
  override val recordedEvents: List<RecordedEvent>,
) : RecordedEventSummary {
  override val sessionCount: Int? = null
  override val entryCount: Int = recordedEvents.count { it.type.code == RecordedEventType.ENTRY.name }
  override val complianceCount: Int = entryCount
  override val latestOccurrence: RecordedEvent? =
    recordedEvents.filter { it.type.code == RecordedEventType.ENTRY.name }.maxByOrNull { it.occurredAt }
  override val recordedEventCount: List<RecordedEventCount> =
    listOf(RecordedEventCount(RecordedEventType.ENTRY, entryCount))
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

  fun findLatestRecordedEvent(personIdentifier: String): RecordedEvent? = data[personIdentifier]?.latestOccurrence

  fun personIdentifiers(): Set<String> = data.keys

  companion object {
    fun empty() =
      RecordedEventSummaries(
        listOf(
          RecordedEventSummary("", emptyList()),
        ),
      )
  }
}

fun List<RecordedEventSummary>.grouped() = RecordedEventSummaries(this)
