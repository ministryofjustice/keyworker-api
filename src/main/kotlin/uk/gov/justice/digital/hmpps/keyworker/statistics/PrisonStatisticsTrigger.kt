package uk.gov.justice.digital.hmpps.keyworker.statistics

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.BackOffPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MessageAttributes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.Notification
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.PrisonStatisticsInfo
import uk.gov.justice.hmpps.sqs.DEFAULT_BACKOFF_POLICY
import uk.gov.justice.hmpps.sqs.DEFAULT_RETRY_POLICY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDate
import java.util.UUID

@Service
class PrisonStatisticsTrigger(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val queueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val eventQueue: HmppsQueue by lazy {
    queueService.findByQueueId("domaineventsqueue") ?: throw IllegalStateException("Queue not available")
  }

  fun runFor(date: LocalDate) {
    AllocationPolicy.entries.forEach { policy ->
      prisonConfigRepository
        .findEnabledPrisonsForPolicyCode(policy.name)
        .asSequence()
        .map { it.toDomainEvent(date) }
        .chunked(10)
        .forEach { eventQueue.publishBatch(it) }
    }
  }

  private fun PrisonConfiguration.toDomainEvent(date: LocalDate): HmppsDomainEvent<PrisonStatisticsInfo> =
    HmppsDomainEvent(
      EventType.CalculatePrisonStats.name,
      PrisonStatisticsInfo(code, date, AllocationPolicy.of(policy)!!),
    )

  private fun HmppsQueue.publishBatch(
    events: Collection<HmppsDomainEvent<*>>,
    retryPolicy: RetryPolicy = DEFAULT_RETRY_POLICY,
    backOffPolicy: BackOffPolicy = DEFAULT_BACKOFF_POLICY,
  ) {
    val retryTemplate =
      RetryTemplate().apply {
        setRetryPolicy(retryPolicy)
        setBackOffPolicy(backOffPolicy)
      }
    val publishRequest =
      SendMessageBatchRequest
        .builder()
        .queueUrl(queueUrl)
        .entries(
          events.map {
            val notification =
              Notification(objectMapper.writeValueAsString(it), attributes = MessageAttributes(it.eventType))
            SendMessageBatchRequestEntry
              .builder()
              .id(UUID.randomUUID().toString())
              .messageBody(objectMapper.writeValueAsString(notification))
              .build()
          },
        ).build()
    retryTemplate.execute<SendMessageBatchResponse, RuntimeException> {
      sqsClient.sendMessageBatch(publishRequest).get()
    }
  }
}
