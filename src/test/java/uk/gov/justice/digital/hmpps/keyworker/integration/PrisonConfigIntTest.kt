package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration

class PrisonConfigIntTest : IntegrationTest() {
  @AfterEach
  fun setUp() {
    prisonConfigRepository.deleteAll()
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
  }

  @ParameterizedTest
  @MethodSource("prisonConfigArgs")
  fun `200 ok - prison config is returned appropriately`(
    prisonCode: String,
    status: PrisonKeyworkerConfiguration,
  ) {
    givenConfiguredKeyworkerPrisonConfig.forEach { givenPrisonConfig(it) }
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenPersonalOfficerPrisonConfig.forEach { givenPrisonConfig(it) }
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

  private fun givenConfiguredKeyworkerPrisonConfig() =
    listOf(
      prisonConfig("ZEZE", false, false, hasPrisonersWithHighComplexityNeeds = false),
      prisonConfig("ZEON", false, false, hasPrisonersWithHighComplexityNeeds = true),
      prisonConfig("ONZE", true, true, hasPrisonersWithHighComplexityNeeds = false),
      prisonConfig("ONON", true, true, hasPrisonersWithHighComplexityNeeds = true),
    ).map { givenPrisonConfig(it) }

  private fun givenPersonalOfficerPrisonConfig() =
    listOf(
      prisonConfig("ZEZE", true, true, policy = AllocationPolicy.PERSONAL_OFFICER),
      prisonConfig("ZEON", true, true, policy = AllocationPolicy.PERSONAL_OFFICER),
      prisonConfig("ONZE", false, true, policy = AllocationPolicy.PERSONAL_OFFICER),
      prisonConfig("ONON", false, false, policy = AllocationPolicy.PERSONAL_OFFICER),
    ).map { givenPrisonConfig(it) }

  companion object {
    @JvmStatic
    fun prisonConfigArgs() =
      listOf(
        Arguments.of("ZEZE", PrisonKeyworkerConfiguration(false, false, false, 6, 9, 1)),
        Arguments.of("ZEON", PrisonKeyworkerConfiguration(false, true, false, 6, 9, 1)),
        Arguments.of("ONZE", PrisonKeyworkerConfiguration(true, false, true, 6, 9, 1)),
        Arguments.of("ONON", PrisonKeyworkerConfiguration(true, true, true, 6, 9, 1)),
      )

    private val givenConfiguredKeyworkerPrisonConfig =
      listOf(
        prisonConfig("ZEZE", false, false, hasPrisonersWithHighComplexityNeeds = false),
        prisonConfig("ZEON", false, false, hasPrisonersWithHighComplexityNeeds = true),
        prisonConfig("ONZE", true, true, hasPrisonersWithHighComplexityNeeds = false),
        prisonConfig("ONON", true, true, hasPrisonersWithHighComplexityNeeds = true),
      )

    private val givenPersonalOfficerPrisonConfig =
      listOf(
        prisonConfig("ZEZE", true, true, policy = AllocationPolicy.PERSONAL_OFFICER),
        prisonConfig("ZEON", true, true, policy = AllocationPolicy.PERSONAL_OFFICER),
        prisonConfig("ONZE", false, true, policy = AllocationPolicy.PERSONAL_OFFICER),
        prisonConfig("ONON", false, false, policy = AllocationPolicy.PERSONAL_OFFICER),
      )

    protected fun prisonConfig(
      code: String,
      enabled: Boolean = false,
      allowAutoAllocation: Boolean = false,
      capacity: Int = 6,
      maxCapacity: Int = 9,
      frequencyInWeeks: Int = 1,
      hasPrisonersWithHighComplexityNeeds: Boolean = false,
      policy: AllocationPolicy = AllocationPolicy.KEY_WORKER,
    ) = PrisonConfiguration(
      code,
      enabled,
      allowAutoAllocation,
      capacity,
      maxCapacity,
      frequencyInWeeks,
      hasPrisonersWithHighComplexityNeeds,
      policy.name,
    )
  }
}
