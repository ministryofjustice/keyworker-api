package uk.gov.justice.digital.hmpps.keyworker.integration.events.domain

import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.integration.events.Notification
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.CalculatePrisonStats
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.CaseNoteCreated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.CaseNoteDeleted
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.CaseNoteMoved
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.CaseNoteUpdated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.ComplexityOfNeedChanged
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.MigrateCaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.Other
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType.PrisonMerged
import uk.gov.justice.digital.hmpps.keyworker.integration.events.offender.ComplexityOfNeedEventProcessor
import uk.gov.justice.digital.hmpps.keyworker.services.MergePrisonNumbers
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.MigrateRecordedEvents
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.PersonInformation
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventService
import uk.gov.justice.digital.hmpps.keyworker.statistics.PrisonStatisticCalculator

@Service
class DomainEventListener(
  private val complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor,
  private val mergePrisonNumbers: MergePrisonNumbers,
  private val prisonStats: PrisonStatisticCalculator,
  private val recordedEvent: RecordedEventService,
  private val migrateRecordedEvents: MigrateRecordedEvents,
  private val jsonMapper: JsonMapper,
  private val telemetryClient: TelemetryClient,
) {
  @SqsListener("domaineventsqueue", factory = "hmppsQueueContainerFactoryProxy")
  fun eventListener(notification: Notification<String>) =
    try {
      when (val eventType = EventType.from(notification.eventType)) {
        ComplexityOfNeedChanged -> complexityOfNeedEventProcessor.onComplexityChange(notification.message)
        PrisonMerged -> {
          val domainEvent = jsonMapper.readValue<HmppsDomainEvent<MergeInformation>>(notification.message)
          AllocationPolicy.entries.forEach { policy ->
            AllocationContext.get().copy(policy = policy).set()
            mergePrisonNumbers.merge(domainEvent.additionalInformation)
          }
        }

        CalculatePrisonStats -> {
          val prisonStatsInfo = jsonMapper.readValue<HmppsDomainEvent<PrisonStatisticsInfo>>(notification.message)
          prisonStats.calculate(prisonStatsInfo)
        }

        CaseNoteCreated if (notification.isOfInterest()) -> recordedEvent.new(information(notification))
        CaseNoteMoved if (notification.isOfInterest()) -> recordedEvent.update(information(notification))
        CaseNoteUpdated -> recordedEvent.update(information(notification))
        CaseNoteDeleted -> recordedEvent.delete(information(notification))

        CaseNoteCreated, CaseNoteMoved -> { /* no-op as not of interest */ }

        MigrateCaseNotes ->
          migrateRecordedEvents.handle(
            jsonMapper.readValue<HmppsDomainEvent<CaseNoteMigrationInformation>>(notification.message),
          )

        is Other -> telemetryClient.trackEvent("UnrecognisedEvent", mapOf("name" to eventType.name), null)
      }
    } finally {
      AllocationContext.clear()
    }

  private fun information(notification: Notification<String>): PersonInformation {
    val message = jsonMapper.readValue<HmppsDomainEvent<CaseNoteInformation>>(notification.message)
    val personIdentifier = checkNotNull(message.personReference.nomsNumber())
    return PersonInformation(personIdentifier, message.additionalInformation)
  }

  private fun Notification<*>.isOfInterest(): Boolean = recordedEvent.isOfInterest(caseNoteType, caseNoteSubType)

  private val Notification<*>.caseNoteType get(): String = attributes["type"]?.value ?: ""
  private val Notification<*>.caseNoteSubType get(): String = attributes["subType"]?.value ?: ""
}
