package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime

class SwitchToPersonalOfficerIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .post()
      .uri(POLICY_SWITCH_URL)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    switchPolicy("ROLE_NE__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 successful switch`() {
    val prisonCode = "HVI"
    AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER).set()
    val active1 = givenAllocation(staffAllocation(personIdentifier(), prisonCode))
    val active2 = givenAllocation(staffAllocation(personIdentifier(), prisonCode))
    val inactive =
      givenAllocation(
        staffAllocation(
          personIdentifier(),
          prisonCode,
          active = false,
          deallocatedAt = LocalDateTime.now(),
          deallocatedBy = "D34110C",
          deallocationReason = DeallocationReason.RELEASED,
        ),
      )

    AllocationContext.get().copy(policy = null).set()
    switchPolicy().expectStatus().isOk

    AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER).set()
    val switchedActive =
      allocationRepository.findAllByPersonIdentifierInAndIsActiveTrue(
        setOf(active1.personIdentifier, active2.personIdentifier, inactive.personIdentifier),
      )
    assertThat(switchedActive).hasSize(2)
    switchedActive.forEach {
      assertThat(it.id).isIn(active1.id, active2.id)
      assertThat(it.policy).isEqualTo(AllocationPolicy.PERSONAL_OFFICER.name)
      val staffRole = staffRoleRepository.findAllByPrisonCodeAndStaffIdIn(prisonCode, setOf(it.staffId))
      assertThat(staffRole).hasSize(1)
      assertThat(staffRole.first().policy).isEqualTo(AllocationPolicy.PERSONAL_OFFICER.name)
      allocationRepository
    }
    val savedInactive = requireNotNull(allocationRepository.findByIdOrNull(inactive.id))
    assertThat(savedInactive.policy).isEqualTo(AllocationPolicy.PERSONAL_OFFICER.name)
    assertThat(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, inactive.staffId)).isNull()
  }

  private fun switchPolicy(role: String? = Roles.ALLOCATIONS_UI) =
    webTestClient
      .post()
      .uri(POLICY_SWITCH_URL)
      .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
      .exchange()

  companion object {
    const val POLICY_SWITCH_URL = "/prisons/HVI/switch-policy"
  }
}
