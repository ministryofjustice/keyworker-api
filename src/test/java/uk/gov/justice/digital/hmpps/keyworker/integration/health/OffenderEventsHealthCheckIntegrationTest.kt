package uk.gov.justice.digital.hmpps.keyworker.integration.health

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.keyworker.services.health.DlqStatus
import uk.gov.justice.digital.hmpps.keyworker.services.health.OffenderEventsQueueHealth
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.MESSAGES_IN_FLIGHT
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.MESSAGES_ON_DLQ
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.MESSAGES_ON_QUEUE

class OffenderEventsHealthCheckIntegrationTest : IntegrationTest() {

  @Autowired
  private lateinit var queueHealth: OffenderEventsQueueHealth

  @Autowired
  @Value("\${offender-events-sqs.queue.name}")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  private lateinit var queueName: String

  @Autowired
  @Value("\${offender-events-sqs.dlq.name}")
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
      .jsonPath("$.components.prisonApiHealth.details.HttpStatus").isEqualTo("OK")
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
      .jsonPath("$.components.prisonApiHealth.details.error")
      .value<String> { error -> assertThat(error).contains("404") }
      .jsonPath("$.components.prisonApiHealth.details.body")
      .value<String> { body -> assertThat(body).contains("some error") }
  }

  @Test
  fun `Health page reports a teapot`() {
    subPing(418)

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.prisonApiHealth.details.error")
      .value<String> { error -> assertThat(error).contains("418") }
      .jsonPath("$.components.prisonApiHealth.details.body")
      .value<String> { body -> assertThat(body).contains("some error") }
  }

  @Test
  fun `Queue Health page reports ok`() {
    subPing(200)

    getForEntity("/health")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
      .jsonPath("$.components.offenderEventsQueueHealth.status").isEqualTo("UP")
  }

  @Test
  fun `Queue Health page reports interesting attributes`() {
    subPing(200)

    getForEntity("/health")
      .expectBody()
      .jsonPath("$.components.offenderEventsQueueHealth.details.${MESSAGES_ON_QUEUE.healthName}").isEqualTo(0)
      .jsonPath("$.components.offenderEventsQueueHealth.details.${MESSAGES_IN_FLIGHT.healthName}").isEqualTo(0)
  }

  @Test
  fun `Queue does not exist reports down`() {
    ReflectionTestUtils.setField(queueHealth, "queueName", "missing_queue")

    subPing(200)

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.offenderEventsQueueHealth.status").isEqualTo("DOWN")
  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    subPing(200)

    getForEntity("/health")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
      .jsonPath("$.components.offenderEventsQueueHealth.status").isEqualTo("UP")
      .jsonPath("$.components.offenderEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.UP.description)
  }

  @Test
  fun `Dlq health reports interesting attributes`() {
    subPing(200)

    getForEntity("/health")
      .expectBody()
      .jsonPath("components.offenderEventsQueueHealth.details.${MESSAGES_ON_DLQ.healthName}").isEqualTo(0)
  }

  @Test
  fun `Dlq down brings main health and queue health down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.offenderEventsQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("$.components.offenderEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Main queue has no redrive policy reports dlq down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.offenderEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
  }

  @Test
  fun `Dlq not found reports dlq down`() {
    subPing(200)
    ReflectionTestUtils.setField(queueHealth, "dlqName", "missing_queue")

    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.components.offenderEventsQueueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)
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
    addConditionalPingStub(prisonMockServer, status)
    addConditionalPingStub(oAuthMockServer, status)
    addConditionalPingStub(complexityOfNeedMockServer, status, "/ping")
  }

  private fun addConditionalPingStub(mock: WireMockServer, status: Int, url: String? = "/health/ping") {
    mock.stubFor(
      WireMock.get(url).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "{\"status\":\"UP\"}" else "some error")
          .withStatus(status)
      )
    )
  }

  private fun mockQueueWithoutRedrivePolicyAttributes() {
    val queueName = ReflectionTestUtils.getField(queueHealth, "queueName") as String
    val queueUrl = awsSqsClientForOffenderEvents.getQueueUrl(queueName)
    whenever(
      awsSqsClientForOffenderEvents.getQueueAttributes(
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
