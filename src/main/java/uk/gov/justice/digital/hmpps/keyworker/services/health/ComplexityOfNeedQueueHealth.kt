package uk.gov.justice.digital.hmpps.keyworker.services.health

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component

@Component
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${complexity-of-need-sqs.provider}')")
open class ComplexityOfNeedQueueHealth(
  @Autowired @Qualifier("awsSqsClientForComplexityOfNeed") private val awsSqsClient: AmazonSQS,
  @Autowired @Qualifier("awsSqsDlqClientForComplexityOfNeed") private val awsSqsDlqClient: AmazonSQS,
  @Value("\${complexity-of-need-sqs.queue.name}") private val queueName: String,
  @Value("\${complexity-of-need-sqs.dlq.name}") private val dlqName: String
) : HealthIndicator {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun health(): Health {
    val queueAttributes = try {
      val url = awsSqsClient.getQueueUrl(queueName)
      awsSqsClient.getQueueAttributes(getQueueAttributesRequest(url))
    } catch (e: Exception) {
      log.error("Unable to retrieve queue attributes for queue '{}' due to exception:", queueName, e)
      return Health.Builder().down().withException(e).build()
    }
    val details = mutableMapOf<String, Any?>(
      QueueAttributes.MESSAGES_ON_QUEUE.healthName to queueAttributes.attributes[QueueAttributes.MESSAGES_ON_QUEUE.awsName]?.toInt(),
      QueueAttributes.MESSAGES_IN_FLIGHT.healthName to queueAttributes.attributes[QueueAttributes.MESSAGES_IN_FLIGHT.awsName]?.toInt()
    )

    return Health.Builder().up().withDetails(details).addDlqHealth(queueAttributes).build()
  }

  private fun Health.Builder.addDlqHealth(mainQueueAttributes: GetQueueAttributesResult): Health.Builder {
    if (!mainQueueAttributes.attributes.containsKey("RedrivePolicy")) {
      log.error(
        "Queue '{}' is missing a RedrivePolicy attribute indicating it does not have a dead letter queue",
        queueName
      )
      return down().withDetail("dlqStatus", DlqStatus.NOT_ATTACHED.description)
    }

    val dlqAttributes = try {
      val url = awsSqsDlqClient.getQueueUrl(dlqName)
      awsSqsDlqClient.getQueueAttributes(getQueueAttributesRequest(url))
    } catch (e: QueueDoesNotExistException) {
      log.error("Unable to retrieve dead letter queue URL for queue '{}' due to exception:", queueName, e)
      return down(e).withDetail("dlqStatus", DlqStatus.NOT_FOUND.description)
    } catch (e: Exception) {
      log.error("Unable to retrieve dead letter queue attributes for queue '{}' due to exception:", queueName, e)
      return down(e).withDetail("dlqStatus", DlqStatus.NOT_AVAILABLE.description)
    }

    return withDetail("dlqStatus", DlqStatus.UP.description)
      .withDetail(QueueAttributes.MESSAGES_ON_DLQ.healthName, dlqAttributes.attributes[QueueAttributes.MESSAGES_ON_DLQ.awsName]?.toInt())
  }

  private fun getQueueAttributesRequest(url: GetQueueUrlResult) =
    GetQueueAttributesRequest(url.queueUrl).withAttributeNames(QueueAttributeName.All)
}
