package uk.gov.justice.digital.hmpps.keyworker.services

import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

@Service
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${offender-events-sqs.provider}')")
class OffenderEventsAdminService(
  @Qualifier("awsSqsClientForOffenderEvents") private val awsSqsClient: AmazonSQS,
  @Qualifier("awsSqsDlqClientForOffenderEvents") private val awsSqsDlqClient: AmazonSQS,
  @Value("\${offender-events-sqs.queue.name}") private val queueName: String,
  @Value("\${offender-events-sqs.dlq.name}") private val dlqName: String
) : QueueAdminService(awsSqsClient, awsSqsDlqClient, queueName, dlqName)

