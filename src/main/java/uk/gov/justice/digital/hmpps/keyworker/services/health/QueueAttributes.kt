package uk.gov.justice.digital.hmpps.keyworker.services.health

import com.amazonaws.services.sqs.model.QueueAttributeName

enum class QueueAttributes(val awsName: String, val healthName: String) {
  MESSAGES_ON_QUEUE(QueueAttributeName.ApproximateNumberOfMessages.toString(), "MessagesOnQueue"),
  MESSAGES_IN_FLIGHT(QueueAttributeName.ApproximateNumberOfMessagesNotVisible.toString(), "MessagesInFlight"),
  MESSAGES_ON_DLQ(QueueAttributeName.ApproximateNumberOfMessages.toString(), "MessagesOnDLQ")
}
