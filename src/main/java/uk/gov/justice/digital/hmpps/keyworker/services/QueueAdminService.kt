package uk.gov.justice.digital.hmpps.keyworker.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

@Service
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${sqs.provider}')")
class QueueAdminService(
  @Qualifier("awsSqsClient") private val awsSqsClient: AmazonSQS,
  @Qualifier("awsSqsDlqClient") private val awsSqsDlqClient: AmazonSQS,
  @Value("\${sqs.queue.name}") private val queueName: String,
  @Value("\${sqs.dlq.name}") private val dlqName: String
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val queueUrl: String by lazy { awsSqsClient.getQueueUrl(queueName).queueUrl }
  val dlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(dlqName).queueUrl }

  fun clearAllDlqMessages() {
    awsSqsDlqClient.purgeQueue(PurgeQueueRequest(dlqUrl))
    log.info("Clear all messages on dead letter queue")
  }

  fun transferMessages() =
    repeat(getDlqMessageCount()) {
      awsSqsDlqClient.receiveMessage(ReceiveMessageRequest(dlqUrl).withMaxNumberOfMessages(1)).messages
        .forEach { msg ->
          awsSqsClient.sendMessage(queueUrl, msg.body)
          awsSqsDlqClient.deleteMessage(DeleteMessageRequest(dlqUrl, msg.receiptHandle))
        }
    }

  private fun getDlqMessageCount() =
    awsSqsDlqClient.getQueueAttributes(dlqUrl, listOf("ApproximateNumberOfMessages"))
      .attributes["ApproximateNumberOfMessages"]
      ?.toInt() ?: 0
}
