package uk.gov.justice.digital.hmpps.keyworker.services

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.GetQueueUrlResult
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.ReceiveMessageResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class QueueAdminServiceTest {

  private val awsSqsClient = mock<AmazonSQS>()
  private val awsSqsDlqClient = mock<AmazonSQS>()
  private lateinit var queueAdminService: QueueAdminService

  @BeforeEach
  internal fun setUp() {
    whenever(awsSqsClient.getQueueUrl("event-queue")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:event-queue"))
    whenever(awsSqsDlqClient.getQueueUrl("event-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:event-dlq"))
    queueAdminService = QueueAdminService(
      awsSqsClient = awsSqsClient,
      awsSqsDlqClient = awsSqsDlqClient,
      queueName = "event-queue",
      dlqName = "event-dlq"
    )
  }

  @Nested
  inner class ClearAllDlqMessagesForEvent {
    @Test
    internal fun `will purge event dlq of messages`() {
      whenever(awsSqsDlqClient.getQueueUrl("event-dlq")).thenReturn(GetQueueUrlResult().withQueueUrl("arn:eu-west-1:event-dlq"))

      queueAdminService.clearAllDlqMessages()
      verify(awsSqsDlqClient).purgeQueue(
        check {
          assertThat(it.queueUrl).isEqualTo("arn:eu-west-1:event-dlq")
        }
      )
    }
  }

  @Nested
  inner class TransferAllEventDlqMessages {

    private val eventQueueUrl = "arn:eu-west-1:event-queue"
    private val eventDlqUrl = "arn:eu-west-1:event-dlq"

    @Test
    internal fun `will read single message from event dlq`() {
      stubDlqMessageCount(1)
      whenever(awsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234AA"))))

      queueAdminService.transferMessages()

      verify(awsSqsDlqClient).receiveMessage(
        check<ReceiveMessageRequest> {
          assertThat(it.queueUrl).isEqualTo(eventDlqUrl)
        }
      )
    }

    @Test
    internal fun `will read multiple messages from dlq`() {
      stubDlqMessageCount(3)
      whenever(awsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234AA"))))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234BB"))))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234CC"))))

      queueAdminService.transferMessages()

      verify(awsSqsDlqClient, times(3)).receiveMessage(
        check<ReceiveMessageRequest> {
          assertThat(it.queueUrl).isEqualTo(eventDlqUrl)
        }
      )
    }

    @Test
    internal fun `will send single message to the event queue`() {
      stubDlqMessageCount(1)
      whenever(awsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234AA"))))

      queueAdminService.transferMessages()

      verify(awsSqsClient).sendMessage(eventQueueUrl, dataComplianceDeleteOffenderMessage("Z1234AA"))
    }

    @Test
    internal fun `will send multiple messages to the event queue`() {
      stubDlqMessageCount(3)
      whenever(awsSqsDlqClient.receiveMessage(any<ReceiveMessageRequest>()))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234AA"))))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234BB"))))
        .thenReturn(ReceiveMessageResult().withMessages(Message().withBody(dataComplianceDeleteOffenderMessage("Z1234CC"))))

      queueAdminService.transferMessages()

      verify(awsSqsClient).sendMessage(eventQueueUrl, dataComplianceDeleteOffenderMessage("Z1234AA"))
      verify(awsSqsClient).sendMessage(eventQueueUrl, dataComplianceDeleteOffenderMessage("Z1234BB"))
      verify(awsSqsClient).sendMessage(eventQueueUrl, dataComplianceDeleteOffenderMessage("Z1234CC"))
    }

    private fun stubDlqMessageCount(count: Int) =
      whenever(awsSqsDlqClient.getQueueAttributes(eventDlqUrl, listOf("ApproximateNumberOfMessages")))
        .thenReturn(GetQueueAttributesResult().withAttributes(mutableMapOf("ApproximateNumberOfMessages" to count.toString())))
  }
}

fun dataComplianceDeleteOffenderMessage(offenderNumber: String) =
  """
    {
  "Type": "Notification",
  "MessageId": "20e13002-d1be-56e7-be8c-66cdd7e23341",
  "TopicArn": "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
  "Message": "{\"eventType\":\"DATA_COMPLIANCE_DELETE-OFFENDER\",\"eventDatetime\":\"2020-02-25T11:24:32.935401\",\"offenderIdDisplay\":\"$offenderNumber"\",\"nomisEventType\":\"DATA_COMPLIANCE_DELETE-OFFENDER\"}",
  "Timestamp": "2020-02-25T11:25:16.169Z",
  "SignatureVersion": "1",
  "Signature": "h5p3FnnbsSHxj53RFePh8HR40cbVvgEZa6XUVTlYs/yuqfDsi17MPA+bX4ijKmmTT2l6xG2xYhcmRAbJWQ4wrwncTBm2azgiwSO5keRNWYVdiC0rI484KLZboP1SDsE+Y7hOU/R0dz49q7+0yd+QIocPteKB/8xG7/6kjGStAZKf3cEdlxOwLhN+7RU1Yk2ENuwAJjVRtvlAa76yKB3xvL2hId7P7ZLmHGlzZDNZNYxbg9C8HGxteOzZ9ZeeQsWDf9jmZ+5+7dKXQoW9LeqwHxEAq2vuwSZ8uwM5JljXbtS5w1P0psXPYNoin2gU1F5MDK8RPzjUtIvjINx08rmEOA==",
  "SigningCertURL": "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-a86cb10b4e1f29c941702d737128f7b6.pem",
  "UnsubscribeURL": "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:92545cfe-de5d-43e1-8339-c366bf0172aa",
  "MessageAttributes": {
    "eventType": {
      "Type": "String",
      "Value": "DATA_COMPLIANCE_DELETE-OFFENDER"
    },
    "id": {
      "Type": "String",
      "Value": "cb4645f2-d0c1-4677-806a-8036ed54bf69"
    },
    "contentType": {
      "Type": "String",
      "Value": "text/plain;charset=UTF-8"
    },
    "timestamp": {
      "Type": "Number.java.lang.Long",
      "Value": "1582629916147"
    }
  }
}
  """.trimIndent()
