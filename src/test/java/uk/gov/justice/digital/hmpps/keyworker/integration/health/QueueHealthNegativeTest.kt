package uk.gov.justice.digital.hmpps.keyworker.integration.health

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueHealth
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties

class QueueHealthNegativeTest : IntegrationTest() {

  @TestConfiguration
  class TestConfig {
    @Bean
    fun badQueueHealth(hmppsConfigProperties: HmppsSqsProperties): HmppsQueueHealth {
      val sqsClient = AmazonSQSClientBuilder.standard()
        .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(hmppsConfigProperties.localstackUrl, hmppsConfigProperties.region))
        .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
        .build()
      return HmppsQueueHealth(HmppsQueue("missingQueueId", sqsClient, "missingQueue", sqsClient, "missingDlq"))
    }
  }

  @Test
  fun `Health page reports down`() {
    getForEntity("/health")
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.status").isEqualTo("DOWN")
      .jsonPath("$.components.badQueueHealth.status").isEqualTo("DOWN")
      .jsonPath("$.components.badQueueHealth.details.dlqStatus").isEqualTo("DOWN")
  }
}
