package uk.gov.justice.digital.hmpps.keyworker.services

import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

@Service
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${complexity-of-need-sqs.provider}')")
class ComplexityOfNeedAdminService(
  @Qualifier("awsSqsClientForComplexityOfNeed") private val awsSqsClient: AmazonSQS,
  @Qualifier("awsSqsDlqClientForComplexityOfNeed") private val awsSqsDlqClient: AmazonSQS,
  @Value("\${complexity-of-need-sqs.queue.name}") private val queueName: String,
  @Value("\${complexity-of-need-sqs.dlq.name}") private val dlqName: String
) : QueueAdminService(awsSqsClient, awsSqsDlqClient, queueName, dlqName)