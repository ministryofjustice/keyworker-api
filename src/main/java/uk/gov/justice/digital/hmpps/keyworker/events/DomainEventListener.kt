package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CalculatePrisonStats
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.ComplexityOfNeedChanged
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.Other
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.PrisonMerged
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.Notification
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.services.MergePrisonNumbers
import uk.gov.justice.digital.hmpps.keyworker.statistics.PrisonStatisticCalculator

@Service
class DomainEventListener(
  private val complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor,
  private val mergePrisonNumbers: MergePrisonNumbers,
  private val prisonStats: PrisonStatisticCalculator,
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

      is Other -> telemetryClient.trackEvent("UnrecognisedEvent", mapOf("name" to eventType.name), null)
    }
  }
}
