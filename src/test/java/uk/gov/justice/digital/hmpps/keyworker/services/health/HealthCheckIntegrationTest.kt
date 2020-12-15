package uk.gov.justice.digital.hmpps.keyworker.services.health

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.MESSAGES_IN_FLIGHT
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.MESSAGES_ON_DLQ
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.MESSAGES_ON_QUEUE

class HealthCheckIntegrationTest : IntegrationTest() {

  @Autowired
  private lateinit var queueHealth: QueueHealth

  @Autowired
  @Value("\${sqs.queue.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var queueName: String

  @Autowired
  @Value("\${sqs.dlq.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var dlqName: String

  @AfterEach
  fun tearDown() {
    ReflectionTestUtils.setField(queueHealth, "queueName", queueName)
    ReflectionTestUtils.setField(queueHealth, "dlqName", dlqName)
  }

  @Test
  fun `Health page reports ok`() {
    subPing(200)

    getForEntity("/health")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
      .jsonPath("$.components.elite2ApiHealth.details.HttpStatus").isEqualTo("OK")
  }

  @Test
  fun `Health ping page is accessible`() {
    subPing(200)

    getForEntity("/health/ping")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down new`() {
    subPing(404)

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.elite2ApiHealth.details.error")
      .value<String> { error -> assertThat(error).contains("404") }
      .jsonPath("$.components.elite2ApiHealth.details.body")
      .value<String> { body -> assertThat(body).contains("some error") }
  }

  @Test
  fun `Health page reports a teapot`() {
    subPing(418)

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.elite2ApiHealth.details.error")
      .value<String> { error -> assertThat(error).contains("418") }
      .jsonPath("$.components.elite2ApiHealth.details.body")
      .value<String> { body -> assertThat(body).contains("some error") }
  }

  @Test
  fun `Queue Health page reports ok`() {
    subPing(200)

    getForEntity("/health")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
      .jsonPath("$.components.queueHealth.status").isEqualTo("UP")
  }

  @Test
  fun `Queue Health page reports interesting attributes`() {
    subPing(200)

    getForEntity("/health")
      .expectBody()
      .jsonPath("$.components.queueHealth.details.${MESSAGES_ON_QUEUE.healthName}").isEqualTo(0)
      .jsonPath("$.components.queueHealth.details.${MESSAGES_IN_FLIGHT.healthName}").isEqualTo(0)
  }

  @Test
  fun `Queue does not exist reports down`() {
    ReflectionTestUtils.setField(queueHealth, "queueName", "missing_queue")
    subPing(200)

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.queueHealth.status").isEqualTo("DOWN")
  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    subPing(200)

    getForEntity("/health")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
      .jsonPath("$.components.queueHealth.status").isEqualTo("UP")
      .jsonPath("$.components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.UP.description)
  }

  @Test
  fun `Dlq health reports interesting attributes`() {
    subPing(200)

    getForEntity("/health")
      .expectBody()
      .jsonPath("components.queueHealth.details.${MESSAGES_ON_DLQ.healthName}").isEqualTo(0)
  }

  @Test
  fun `Dlq down brings main health and queue health down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.queueHealth.status").isEqualTo("DOWN")
      .jsonPath("$.components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Main queue has no redrive policy reports dlq down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Dlq not found reports dlq down`() {
    subPing(200)
    ReflectionTestUtils.setField(queueHealth, "dlqName", "missing_queue")

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)
  }

  @Test
  fun `Health liveness page is accessible`() {
    getForEntity("/health/liveness")
      .expectStatus().isEqualTo(200)
      .expectBody().jsonPath("$.stats", "UP")
  }

  @Test
  fun `Health readiness page is accessible`() {
    getForEntity("/health/readiness")
      .expectStatus().isEqualTo(200)
      .expectBody().jsonPath("$.stats", "UP")
  }

  private fun subPing(status: Int) {
    eliteMockServer.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "{\"status\":\"UP\"}" else "some error")
          .withStatus(status)
      )
    )
  }

  private fun mockQueueWithoutRedrivePolicyAttributes() {
    val queueName = ReflectionTestUtils.getField(queueHealth, "queueName") as String
    val queueUrl = awsSqsClient.getQueueUrl(queueName)
    whenever(
      awsSqsClient.getQueueAttributes(
        GetQueueAttributesRequest(queueUrl.queueUrl).withAttributeNames(
          listOf(
            QueueAttributeName.All.toString()
          )
        )
      )
    )
      .thenReturn(GetQueueAttributesResult())
  }
}
