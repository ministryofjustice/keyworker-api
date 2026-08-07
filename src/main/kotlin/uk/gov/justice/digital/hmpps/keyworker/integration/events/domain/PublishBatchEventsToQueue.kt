package uk.gov.justice.digital.hmpps.keyworker.integration.events.domain

import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.BackOffPolicy
import org.springframework.retry.support.RetryTemplate
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MessageAttributes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.Notification
import uk.gov.justice.hmpps.sqs.DEFAULT_BACKOFF_POLICY
import uk.gov.justice.hmpps.sqs.DEFAULT_RETRY_POLICY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import java.util.UUID

fun HmppsQueue.publishBatch(
  jsonMapper: JsonMapper,
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
            Notification(jsonMapper.writeValueAsString(it), attributes = MessageAttributes(it.eventType))
          SendMessageBatchRequestEntry
            .builder()
            .id(UUID.randomUUID().toString())
            .messageBody(jsonMapper.writeValueAsString(notification))
            .build()
        },
      ).build()
  retryTemplate.execute<SendMessageBatchResponse, RuntimeException> {
    sqsClient.sendMessageBatch(publishRequest).get()
  }
}
