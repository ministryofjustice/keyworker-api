package uk.gov.justice.digital.hmpps.keyworker.controllers

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(HighComplexityPrisonsController::class)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@WithMockUser
class HighComplexityPrisonsControllerTest {

  @Autowired
  private lateinit var webTestClient: WebTestClient

  @Test
  fun `get high complexity prisons`() {
    webTestClient.get()
      .uri("/high-complexity-prisons/")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("""
        ["LEI", "MDI"]
        """.trimIndent())
  }
}
