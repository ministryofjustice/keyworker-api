package uk.gov.justice.digital.hmpps.keyworker.services.recordedevents

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class RecordedEventRetriever(
  private val referenceDataRepository: ReferenceDataRepository,
  private val rer: RecordedEventRepository,
) {
  fun findCaseNoteTotals(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): RecordedEventTotals {
    val recordedEventTypes =
      referenceDataRepository
        .findByKeyDomainOrderBySequenceNumber(ReferenceDataDomain.RECORDED_EVENT_TYPE)
        .associateBy { RecordedEventType.valueOf(it.code) }

    return rer
      .findByPrisonCodeAndOccurredAtBetween(
        prisonCode,
        from.atStartOfDay(),
        to.plusDays(1).atStartOfDay(),
      ).let { list ->
        RecordedEventTotals(
          recordedEventTypes[RecordedEventType.SESSION]?.let { type -> list.count { it.type.code == type.code } },
          recordedEventTypes[RecordedEventType.ENTRY]?.let { type -> list.count { it.type.code == type.code } },
        )
      }
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
          e.value
            .groupBy { it.personIdentifier }
            .map {
              RecordedEventSummary(it.key, it.value)
            }.grouped()
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
      ?.groupBy { it.personIdentifier }
      ?.map {
        RecordedEventSummary(it.key, it.value)
      }?.grouped() ?: RecordedEventSummaries.empty()

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
      ).associate { it.personIdentifier to LatestNote(it.date) }
}

class RecordedEventTotals(
  val sessionCount: Int?,
  val entryCount: Int?,
) {
  companion object {
    fun empty() = RecordedEventTotals(null, null)
  }
}

data class LatestNote(
  val occurredAt: LocalDateTime,
)
