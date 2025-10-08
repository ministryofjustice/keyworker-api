package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest

class OffenderEventsHealthCheckTest : IntegrationTest() {
  @BeforeEach
  fun setup() {
    subPing(200)
  }

  @Test
  fun `Queue health ok and dlq health ok, reports everything up`() {
    getForEntity("/health")
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
      .jsonPath("$.components.offenderevents-health.status")
      .isEqualTo("UP")
      .jsonPath("$.components.offenderevents-health.details.dlqStatus")
      .isEqualTo("UP")
  }

  @Test
  fun `Health page reports ok`() {
    getForEntity("/health")
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
      .jsonPath("$.components.prisonApiHealth.details.HttpStatus")
      .isEqualTo("OK")
  }

  @Test
  fun `Health ping page is accessible`() {
    getForEntity("/health/ping")
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
  }

  @Test
  fun `Health liveness page is accessible`() {
    getForEntity("/health/liveness")
      .expectStatus()
      .isEqualTo(200)
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
  }

  @Test
  fun `Health readiness page is accessible`() {
    getForEntity("/health/readiness")
      .expectStatus()
      .isEqualTo(200)
      .expectBody()
      .jsonPath("$.status")
      .isEqualTo("UP")
  }
}
