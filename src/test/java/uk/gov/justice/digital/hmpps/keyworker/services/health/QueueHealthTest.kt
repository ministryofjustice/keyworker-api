package uk.gov.justice.digital.hmpps.keyworker.services.health

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.*
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.springframework.boot.actuate.health.Status

import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.*

class QueueHealthTest {

  private val someQueueName = "some queue name"
  private val someQueueUrl = "some queue url"
  private val someDLQName = "some DLQ name"
  private val someDLQUrl = "some DLQ url"
  private val someMessagesOnQueueCount = 123
  private val someMessagesInFlightCount = 456
  private val someMessagesOnDLQCount = 789
  private val amazonSqs: AmazonSQS = mock()
  private val amazonSqsDLQ: AmazonSQS = mock()
  private val queueHealth: QueueHealth = QueueHealth(amazonSqs, amazonSqsDLQ, someQueueName, someDLQName)

  @Test
  fun `health - queue found - UP`() {
    mockHealthyQueue()

    val health = queueHealth.health()

    assertThat(health.status).isEqualTo(Status.UP)
  }

  @Test
  fun `health - attributes returned - included in health status`() {
    mockHealthyQueue()

    val health = queueHealth.health()

    assertThat(health.details[MESSAGES_ON_QUEUE.healthName]).isEqualTo(someMessagesOnQueueCount)
    assertThat(health.details[MESSAGES_IN_FLIGHT.healthName]).isEqualTo(someMessagesInFlightCount)
  }

  @Test
  fun `health - queue not found - DOWN`() {
    whenever(amazonSqs.getQueueUrl(anyString())).thenThrow(QueueDoesNotExistException::class.java)

    val health = queueHealth.health()

    assertThat(health.status).isEqualTo(Status.DOWN)
  }

  @Test
  fun `health - failed to get main queue attributes - DOWN`() {
    whenever(amazonSqs.getQueueUrl(anyString())).thenReturn(someGetQueueUrlResult())
    whenever(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenThrow(RuntimeException::class.java)

    val health = queueHealth.health()

    assertThat(health.status).isEqualTo(Status.DOWN)
  }

  @Test
  fun `health - DLQ UP - reports DLQ UP`() {
    mockHealthyQueue()

    val health = queueHealth.health()

    assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.UP.description)
  }

  @Test
  fun `health - DLQ attributes returned - included in health status`() {
    mockHealthyQueue()

    val health = queueHealth.health()

    assertThat(health.details[MESSAGES_ON_DLQ.healthName]).isEqualTo(someMessagesOnDLQCount)
  }

  @Test
  fun `health - DLQ down - main queue health also DOWN`() {
    whenever(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithoutDLQ())

    val health = queueHealth.health()

    assertThat(health.status).isEqualTo(Status.DOWN)
    assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `health - no RedrivePolicy attribute on main queue - DLQ NOT ATTACHED`() {
    whenever(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithoutDLQ())

    val health = queueHealth.health()

    assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `health - DLQ not found - DLQ NOT FOUND`() {
    whenever(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
    whenever(amazonSqsDLQ.getQueueUrl(someDLQName)).thenThrow(QueueDoesNotExistException::class.java)

    val health = queueHealth.health()

    assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_FOUND.description)
  }

  @Test
  fun `health - DLQ failed to get attributes - DLQ NOT AVAILABLE`() {
    whenever(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
    whenever(amazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ())
    whenever(amazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenThrow(RuntimeException::class.java)

    val health = queueHealth.health()

    assertThat(health.details["dlqStatus"]).isEqualTo(DlqStatus.NOT_AVAILABLE.description)
  }

  private fun mockHealthyQueue() {
    whenever(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult())
    whenever(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(someGetQueueAttributesResultWithDLQ())
    whenever(amazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ())
    whenever(amazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenReturn(someGetQueueAttributesResultForDLQ())
  }

  private fun someGetQueueAttributesRequest() = GetQueueAttributesRequest(someQueueUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))
  private fun someGetQueueUrlResult(): GetQueueUrlResult = GetQueueUrlResult().withQueueUrl(someQueueUrl)
  private fun someGetQueueAttributesResultWithoutDLQ() = GetQueueAttributesResult().withAttributes(
      mapOf(MESSAGES_ON_QUEUE.awsName to someMessagesOnQueueCount.toString(),
          MESSAGES_IN_FLIGHT.awsName to someMessagesInFlightCount.toString()))

  private fun someGetQueueAttributesResultWithDLQ() = GetQueueAttributesResult().withAttributes(
      mapOf(MESSAGES_ON_QUEUE.awsName to someMessagesOnQueueCount.toString(),
          MESSAGES_IN_FLIGHT.awsName to someMessagesInFlightCount.toString(),
          QueueAttributeName.RedrivePolicy.toString() to "any redrive policy"))

  private fun someGetQueueAttributesRequestForDLQ() = GetQueueAttributesRequest(someDLQUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))
  private fun someGetQueueUrlResultForDLQ(): GetQueueUrlResult = GetQueueUrlResult().withQueueUrl(someDLQUrl)
  private fun someGetQueueAttributesResultForDLQ() = GetQueueAttributesResult().withAttributes(
      mapOf(MESSAGES_ON_QUEUE.awsName to someMessagesOnDLQCount.toString()))

}

