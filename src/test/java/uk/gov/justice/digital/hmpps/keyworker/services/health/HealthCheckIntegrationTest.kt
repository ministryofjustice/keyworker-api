package uk.gov.justice.digital.hmpps.keyworker.services.health

import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.amazonaws.services.sqs.model.QueueAttributeName
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.nhaarman.mockito_kotlin.whenever
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.keyworker.services.health.QueueAttributes.*

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

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.elite2ApiHealth.details.HttpStatus").isEqualTo("OK")
    assertThatJson(response.body).node("status").isEqualTo("UP")
    assertThat(response.statusCodeValue).isEqualTo(200)
  }

  @Test
  fun `Health ping page is accessible`() {
    subPing(200)

    val response = restTemplate.getForEntity("/health/ping", String::class.java)

    assertThatJson(response.body).node("status").isEqualTo("UP")
    assertThat(response.statusCodeValue).isEqualTo(200)
  }

  @Test
  fun `Health page reports down`() {
    subPing(404)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.elite2ApiHealth.details.error").isEqualTo("org.springframework.web.client.HttpClientErrorException\$NotFound: 404 Not Found: [some error]")
    assertThatJson(response.body).node("status").isEqualTo("DOWN")
    assertThat(response.statusCodeValue).isEqualTo(503)
  }

  @Test
  fun `Health page reports a teapot`() {
    subPing(418)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.elite2ApiHealth.details.error").isEqualTo("org.springframework.web.client.HttpClientErrorException: 418 418: [some error]")
    assertThatJson(response.body).node("status").isEqualTo("DOWN")
    assertThat(response.statusCodeValue).isEqualTo(503)
  }

  @Test
  fun `Queue Health page reports ok`() {
    subPing(200)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.queueHealth.status").isEqualTo("UP")
    assertThatJson(response.body).node("status").isEqualTo("UP")
    assertThat(response.statusCodeValue).isEqualTo(200)
  }

  @Test
  fun `Queue Health page reports interesting attributes`() {
    subPing(200)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.queueHealth.details.${MESSAGES_ON_QUEUE.healthName}").isEqualTo(0)
    assertThatJson(response.body).node("components.queueHealth.details.${MESSAGES_IN_FLIGHT.healthName}").isEqualTo(0)
  }

  @Test
  fun `Queue does not exist reports down`() {
    ReflectionTestUtils.setField(queueHealth, "queueName", "missing_queue")
    subPing(200)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("status").isEqualTo("DOWN")
    assertThatJson(response.body).node("components.queueHealth.status").isEqualTo("DOWN")
    assertThat(response.statusCodeValue).isEqualTo(503)
  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    subPing(200)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("status").isEqualTo("UP")
    assertThatJson(response.body).node("components.queueHealth.status").isEqualTo("UP")
    assertThatJson(response.body).node("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.UP.description)
    assertThat(response.statusCodeValue).isEqualTo(200)
  }

  @Test
  fun `Dlq health reports interesting attributes`() {
    subPing(200)

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.queueHealth.details.${MESSAGES_ON_DLQ.healthName}").isEqualTo(0)
  }

  @Test
  fun `Dlq down brings main health and queue health down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("status").isEqualTo("DOWN")
    assertThatJson(response.body).node("components.queueHealth.status").isEqualTo("DOWN")
    assertThatJson(response.body).node("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
    assertThat(response.statusCodeValue).isEqualTo(503)
  }

  @Test
  fun `Main queue has no redrive policy reports dlq down`() {
    subPing(200)
    mockQueueWithoutRedrivePolicyAttributes()

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_ATTACHED.description)
    assertThat(response.statusCodeValue).isEqualTo(503)
  }

  @Test
  fun `Dlq not found reports dlq down`() {
    subPing(200)
    ReflectionTestUtils.setField(queueHealth, "dlqName", "missing_queue")

    val response = restTemplate.getForEntity("/health", String::class.java)

    assertThatJson(response.body).node("components.queueHealth.details.dlqStatus").isEqualTo(DlqStatus.NOT_FOUND.description)
    assertThat(response.statusCodeValue).isEqualTo(503)
  }

  private fun subPing(status: Int) {
    eliteMockServer.stubFor(get("/ping").willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(if (status == 200) "pong" else "some error")
        .withStatus(status)))
  }

  private fun mockQueueWithoutRedrivePolicyAttributes() {
    val queueName = ReflectionTestUtils.getField(queueHealth, "queueName") as String
    val queueUrl = awsSqsClient.getQueueUrl(queueName)
    whenever(awsSqsClient.getQueueAttributes(GetQueueAttributesRequest(queueUrl.queueUrl).withAttributeNames(listOf(QueueAttributeName.All.toString()))))
        .thenReturn(GetQueueAttributesResult())
  }

}
