package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest

class ComplexityOfNeedQueueHealthTest : IntegrationTest() {
  @BeforeEach
  fun setup() {
    subPing(200)
  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    getForEntity("/health")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
      .jsonPath("$.components.domaineventsqueue-health.status").isEqualTo("UP")
      .jsonPath("$.components.domaineventsqueue-health.details.dlqStatus").isEqualTo("UP")
  }

  @Test
  fun `Health ping page is accessible`() {
    getForEntity("/health/ping")
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.status").isEqualTo("UP")
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
}
