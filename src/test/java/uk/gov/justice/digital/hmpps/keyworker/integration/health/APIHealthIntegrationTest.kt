package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest

class APIHealthIntegrationTest : IntegrationTest() {
  @Test
  fun `Health page reports ok`() {
    eliteMockServer.stubHealthOKResponse()
    oAuthMockServer.stubHealthOKResponse()
    complexityOfNeedMockServer.stubHealthOKResponse()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus().is2xxSuccessful
      .expectBody().json("{\"status\":\"UP\"}")
  }

  @Test
  fun `Health page dependency timeout`() {
    eliteMockServer.stubHealthDependencyTimeoutResponse()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
      .expectBody()
      .jsonPath("$.components.elite2ApiHealth.status").isEqualTo("DOWN")
      .jsonPath("$.components.elite2ApiHealth.details.error").value<String> { assertThat(it).contains("Timeout") }
  }
}
