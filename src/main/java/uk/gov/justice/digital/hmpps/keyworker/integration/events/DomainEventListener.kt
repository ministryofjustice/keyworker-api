package uk.gov.justice.digital.hmpps.keyworker.integration.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedEventProcessor
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CalculatePrisonStats
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteCreated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteDeleted
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteMoved
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteUpdated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.ComplexityOfNeedChanged
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.MigrateCaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.Other
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.PrisonMerged
import uk.gov.justice.digital.hmpps.keyworker.services.MergePrisonNumbers
import uk.gov.justice.digital.hmpps.keyworker.services.MigrateSessionsAndEntries
import uk.gov.justice.digital.hmpps.keyworker.services.PersonInformation
import uk.gov.justice.digital.hmpps.keyworker.services.SessionAndEntryService
import uk.gov.justice.digital.hmpps.keyworker.statistics.PrisonStatisticCalculator

@Service
class DomainEventListener(
  private val complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor,
  private val mergePrisonNumbers: MergePrisonNumbers,
  private val prisonStats: PrisonStatisticCalculator,
  private val sessionEntries: SessionAndEntryService,
  private val migrate: MigrateSessionsAndEntries,
  private val objectMapper: ObjectMapper,
  private val telemetryClient: TelemetryClient,
) {
  @SqsListener("domaineventsqueue", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "keyworker-api-complexity-event-queue", kind = SpanKind.SERVER)
  fun eventListener(notification: Notification<String>) {
    val eventType = EventType.from(notification.eventType)

    when (eventType) {
      ComplexityOfNeedChanged -> complexityOfNeedEventProcessor.onComplexityChange(notification.message)
      PrisonMerged -> {
        val domainEvent = objectMapper.readValue<HmppsDomainEvent<MergeInformation>>(notification.message)
        mergePrisonNumbers.merge(domainEvent.additionalInformation)
      }

      CalculatePrisonStats -> {
        val prisonStatsInfo = objectMapper.readValue<HmppsDomainEvent<PrisonStatisticsInfo>>(notification.message)
        prisonStats.calculate(prisonStatsInfo)
      }

      CaseNoteCreated if (notification.isKeyworkerRelated()) -> sessionEntries.new(information(notification))
      CaseNoteMoved if (notification.isKeyworkerRelated()) -> sessionEntries.move(information(notification))
      CaseNoteDeleted if (notification.isKeyworkerRelated()) -> sessionEntries.delete(information(notification))
      CaseNoteUpdated -> sessionEntries.update(information(notification))

      CaseNoteCreated, CaseNoteMoved, CaseNoteDeleted ->
        telemetryClient.trackEvent(
          "CaseNoteNotOfInterest",
          mapOf(
            "name" to eventType.name,
            "type" to notification.caseNoteType,
            "subType" to notification.caseNoteSubType,
          ),
          null,
        )

      MigrateCaseNotes ->
        migrate.handle(
          objectMapper.readValue<HmppsDomainEvent<CaseNoteMigrationInformation>>(notification.message),
        )

      is Other -> telemetryClient.trackEvent("UnrecognisedEvent", mapOf("name" to eventType.name), null)
    }
  }

  private fun information(notification: Notification<String>): PersonInformation {
    val message = objectMapper.readValue<HmppsDomainEvent<CaseNoteInformation>>(notification.message)
    val personIdentifier = checkNotNull(message.personReference.nomsNumber())
    return PersonInformation(personIdentifier, message.additionalInformation)
  }

  private fun Notification<*>.isKeyworkerRelated(): Boolean = caseNoteType == KW_TYPE

  private val Notification<*>.caseNoteType get(): String = attributes["type"]?.value ?: ""
  private val Notification<*>.caseNoteSubType get(): String = attributes["subType"]?.value ?: ""
}
