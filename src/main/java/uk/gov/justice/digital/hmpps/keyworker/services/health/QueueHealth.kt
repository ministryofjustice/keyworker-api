package uk.gov.justice.digital.hmpps.keyworker.services.health

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.*
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessagesNotVisible
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Health.Builder
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.*

enum class DlqStatus(val description: String) {
  UP("UP"),
  NOT_ATTACHED("The queue does not have a dead letter queue attached"),
  NOT_FOUND("The queue does not exist"),
  NOT_AVAILABLE("The queue cannot be interrogated")
}

enum class QueueAttributes(val awsName: String, val healthName: String) {
  MESSAGES_ON_QUEUE(ApproximateNumberOfMessages.toString(), "MessagesOnQueue"),
  MESSAGES_IN_FLIGHT(ApproximateNumberOfMessagesNotVisible.toString(), "MessagesInFlight"),
  MESSAGES_ON_DLQ(ApproximateNumberOfMessages.toString(), "MessagesOnDLQ")
}

@Component
class QueueHealth(@Autowired @Qualifier("awsSqsClient") private val awsSqsClient: AmazonSQS,
                  @Autowired @Qualifier("awsSqsDlqClient") private val awsSqsDlqClient: AmazonSQS,
                  @Value("\${sqs.queue.name}") private val queueName: String,
                  @Value("\${sqs.dlq.name}") private val dlqName: String) : HealthIndicator {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun health(): Health {
    val queueAttributes = try {
      val url = awsSqsClient.getQueueUrl(queueName)
      awsSqsClient.getQueueAttributes(getQueueAttributesRequest(url))
    } catch (e: Exception) {
      log.error("Unable to retrieve queue attributes for queue '{}' due to exception:", queueName, e)
      return Builder().down().withException(e).build()
    }
    val details = mutableMapOf<String, Any?>(
        MESSAGES_ON_QUEUE.healthName to queueAttributes.attributes[MESSAGES_ON_QUEUE.awsName]?.toInt(),
        MESSAGES_IN_FLIGHT.healthName to queueAttributes.attributes[MESSAGES_IN_FLIGHT.awsName]?.toInt()
    )

    val health = Builder().up().withDetails(details).addDlqHealth(queueAttributes).build()

    log.info("Found health details for queue '{}': {}", queueName, health)
    return health
  }

  private fun Builder.addDlqHealth(mainQueueAttributes: GetQueueAttributesResult): Builder {
    if (!mainQueueAttributes.attributes.containsKey("RedrivePolicy")) {
      log.error("Queue '{}' is missing a RedrivePolicy attribute indicating it does not have a dead letter queue", queueName)
      return this.down().withDetail("dlqStatus", DlqStatus.NOT_ATTACHED.description)
    }

    val dlqAttributes = try {
      val url = awsSqsDlqClient.getQueueUrl(dlqName)
      awsSqsDlqClient.getQueueAttributes(getQueueAttributesRequest(url))
    } catch (e: QueueDoesNotExistException) {
      log.error("Unable to retrieve dead letter queue URL for queue '{}' due to exception:", queueName, e)
      return this.down(e).withDetail("dlqStatus", DlqStatus.NOT_FOUND.description)
    } catch (e: Exception) {
      log.error("Unable to retrieve dead letter queue attributes for queue '{}' due to exception:", queueName, e)
      return this.down(e).withDetail("dlqStatus", DlqStatus.NOT_AVAILABLE.description)
    }

    return this.withDetail("dlqStatus", DlqStatus.UP.description)
        .withDetail(MESSAGES_ON_DLQ.healthName, dlqAttributes.attributes[MESSAGES_ON_DLQ.awsName]?.toInt())
  }

  private fun getQueueAttributesRequest(url: GetQueueUrlResult) =
      GetQueueAttributesRequest(url.queueUrl).withAttributeNames(QueueAttributeName.All)

}
