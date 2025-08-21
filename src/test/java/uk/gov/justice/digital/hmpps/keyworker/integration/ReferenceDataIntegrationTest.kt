package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription

class ReferenceDataIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(REFERENCE_DATA_URL, "any-domain")
      .header(PolicyHeader.NAME, AllocationPolicy.KEY_WORKER.name)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getReferenceDataSpec(
      "any-domain",
      AllocationPolicy.PERSONAL_OFFICER,
      "ROLE_ANY__OTHER_RW",
    ).expectStatus().isForbidden
  }

  @Test
  fun `200 ok - can retrieve staff status`() {
    val rd =
      getReferenceDataSpec("staff-status", policy = AllocationPolicy.PERSONAL_OFFICER)
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
  fun `200 ok - can retrieve reference data domains with correct role`(
    domain: String,
    policy: AllocationPolicy,
  ) {
    val rd =
      getReferenceDataSpec(domain, policy)
        .expectStatus()
        .isOk
        .expectBodyList(CodedDescription::class.java)
        .returnResult()
        .responseBody

    assertThat(rd).isNotEmpty
  }

  private fun getReferenceDataSpec(
    domain: String,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri(REFERENCE_DATA_URL, domain)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  companion object {
    const val REFERENCE_DATA_URL = "/reference-data/{domain}"

    @JvmStatic
    fun referenceDataDomains() =
      ReferenceDataDomain.entries.flatMap { rdd ->
        AllocationPolicy.entries.map { ap ->
          Arguments.of(rdd.name, ap)
          Arguments.of(rdd.name.lowercase(), ap)
          Arguments.of(rdd.name.lowercase().replace("_", "-"), ap)
        }
      }
  }
}
