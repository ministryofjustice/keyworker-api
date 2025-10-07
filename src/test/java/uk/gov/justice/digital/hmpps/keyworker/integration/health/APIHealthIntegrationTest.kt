package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest

class APIHealthIntegrationTest : IntegrationTest() {
  @Test
  fun `Health page reports ok`() {
    prisonMockServer.stubHealthOKResponse()
    oAuthMockServer.stubHealthOKResponse()
    complexityOfNeedMockServer.stubHealthOKResponse()

    webTestClient
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .json("{\"status\":\"UP\"}")
  }

  @Test
  fun `Health page dependency timeout`() {
    prisonMockServer.stubHealthDependencyTimeoutResponse()

    webTestClient
      .get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.components.prisonApiHealth.status")
      .isEqualTo("DOWN")
  }
}
