package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription

class ReferenceDataIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(REFERENCE_DATA_URL, "any-domain")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getReferenceDataSpec("any-domain", "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok - can retrieve staff status`() {
    val rd =
      getReferenceDataSpec("staff-status")
        .expectStatus()
        .isOk
        .expectBodyList(CodedDescription::class.java)
        .returnResult()
        .responseBody

    assertThat(rd).containsExactly(
      CodedDescription("ACTIVE", "Active"),
      CodedDescription("UNAVAILABLE_ANNUAL_LEAVE", "Unavailable - annual leave"),
      CodedDescription("UNAVAILABLE_LONG_TERM_ABSENCE", "Unavailable - long term absence"),
      CodedDescription("UNAVAILABLE_NO_PRISONER_CONTACT", "Unavailable - no prisoner contact"),
      CodedDescription("INACTIVE", "Inactive"),
    )
  }

  @ParameterizedTest
  @MethodSource("referenceDataDomains")
  fun `200 ok - can retrieve reference data domains with correct role`(domain: String) {
    val rd =
      getReferenceDataSpec(domain)
        .expectStatus()
        .isOk
        .expectBodyList(CodedDescription::class.java)
        .returnResult()
        .responseBody

    assertThat(rd).isNotEmpty
  }

  private fun getReferenceDataSpec(
    domain: String,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri(REFERENCE_DATA_URL, domain)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val REFERENCE_DATA_URL = "/reference-data/{domain}"

    @JvmStatic
    fun referenceDataDomains() =
      ReferenceDataDomain.entries.flatMap {
        listOf(
          Arguments.of(it.name),
          Arguments.of(it.name.lowercase()),
          Arguments.of(it.name.lowercase().replace("_", "-")),
        )
      }
  }
}
