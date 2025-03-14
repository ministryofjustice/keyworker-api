package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported

class PrisonConfigIntTest : IntegrationTest() {
  @ParameterizedTest
  @MethodSource("prisonConfigArgs")
  fun `200 ok - prison config is returned appropriately`(
    prisonCode: String,
    status: PrisonKeyworkerConfiguration,
  ) {
    givenConfiguredPrisons()

    val res = getPrisonConfig(prisonCode)
    assertThat(res).isEqualTo(status)
  }

  private fun getPrisonConfig(prisonCode: String) =
    webTestClient
      .get()
      .uri { it.path("/prisons/{prisonCode}/configuration/keyworker").build(prisonCode) }
      .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(Roles.KEYWORKER_RO)))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(PrisonKeyworkerConfiguration::class.java)
      .returnResult()
      .responseBody!!

  private fun givenConfiguredPrisons() =
    prisonSupportedRepository.saveAll(
      listOf(
        prisonConfig("ZEZE", false, false, false),
        prisonConfig("ZEON", false, false, true),
        prisonConfig("ONZE", true, true, false),
        prisonConfig("ONON", true, true, true),
      ),
    )

  private fun prisonConfig(
    code: String,
    migrated: Boolean,
    autoAllocate: Boolean,
    hasPrisonersWithComplexNeeds: Boolean,
  ) = PrisonSupported(code, migrated, autoAllocate, null, 6, 9, 1, hasPrisonersWithComplexNeeds)

  companion object {
    @JvmStatic
    fun prisonConfigArgs() =
      listOf(
        Arguments.of("ZEZE", PrisonKeyworkerConfiguration(false, false, false, 6, 9, 1)),
        Arguments.of("ZEON", PrisonKeyworkerConfiguration(false, true, false, 6, 9, 1)),
        Arguments.of("ONZE", PrisonKeyworkerConfiguration(true, false, true, 6, 9, 1)),
        Arguments.of("ONON", PrisonKeyworkerConfiguration(true, true, true, 6, 9, 1)),
      )
  }
}
