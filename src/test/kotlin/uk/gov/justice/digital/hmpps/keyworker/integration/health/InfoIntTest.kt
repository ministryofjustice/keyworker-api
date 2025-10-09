package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.keyworker.model.prison.ActiveAgenciesResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE
import java.util.function.Consumer

class InfoIntTest : IntegrationTest() {
  @Test
  fun `personal officer enabled prisons are returned from the database`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val poPrisonCode = "POP"
    givenPrisonConfig(prisonConfig(poPrisonCode, enabled = true))

    val response =
      webTestClient
        .get()
        .uri("/personal-officer/info")
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ActiveAgenciesResponse>()
        .returnResult()
        .responseBody!!

    assertThat(response.activeAgencies).contains(poPrisonCode)
  }

  @Test
  fun `Info page contains git information`() {
    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("git.commit.id")
      .isNotEmpty
  }

  @Test
  fun `Info page reports version`() {
    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.version")
      .value(
        Consumer<String> {
          assertThat(it).startsWith(LocalDateTime.now().format(ISO_DATE))
        },
      )
  }
}
