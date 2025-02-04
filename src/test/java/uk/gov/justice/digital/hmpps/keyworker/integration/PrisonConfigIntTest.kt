package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported

class PrisonConfigIntTest : IntegrationTest() {
  @ParameterizedTest
  @MethodSource("prisonConfigArgs")
  fun `200 ok - prison config is returned appropriately`(
    prisonCode: String,
    status: PrisonKeyworkerStatus,
  ) {
    givenConfiguredPrisons()

    val res = getPrisonConfig(prisonCode)
    assertThat(res).isEqualTo(status)
  }

  private fun getPrisonConfig(prisonCode: String) =
    webTestClient
      .get()
      .uri { it.path("/prisons/{prisonCode}/keyworker/configuration").build(prisonCode) }
      .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(Roles.KEYWORKER_RO)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(PrisonKeyworkerStatus::class.java)
      .returnResult()
      .responseBody!!

  private fun givenConfiguredPrisons() =
    prisonSupportedRepository.saveAll(
      listOf(
        prisonConfig("ZEZE", false, false),
        prisonConfig("ZEON", false, true),
        prisonConfig("ONZE", true, false),
        prisonConfig("ONON", true, true),
      ),
    )

  private fun prisonConfig(
    code: String,
    migrated: Boolean,
    hasPrisonersWithComplexNeeds: Boolean,
  ) = PrisonSupported(code, migrated, false, null, 6, 9, 1, hasPrisonersWithComplexNeeds)

  companion object {
    @JvmStatic
    fun prisonConfigArgs() =
      listOf(
        Arguments.of("ZEZE", PrisonKeyworkerStatus(false, false)),
        Arguments.of("ZEON", PrisonKeyworkerStatus(false, true)),
        Arguments.of("ONZE", PrisonKeyworkerStatus(true, false)),
        Arguments.of("ONON", PrisonKeyworkerStatus(true, true)),
      )
  }
}
