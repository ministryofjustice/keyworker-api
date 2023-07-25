package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
class QueueHealthNegativeTest : IntegrationTest() {

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
