package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.dto.person.StaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime

class GetStaffAllocationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(ALLOCATION_HISTORY_URL, personIdentifier())
      .header(PolicyHeader.NAME, AllocationPolicy.PERSONAL_OFFICER.name)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getAllocationHistorySpec(
      personIdentifier(),
      AllocationPolicy.PERSONAL_OFFICER,
      "ROLE_ANY__OTHER_RW",
    ).expectStatus().isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `200 ok and all allocations returned`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "AHP"
    val prisonNumber = personIdentifier()
    val staffConfig =
      (0..4).map {
        givenStaffConfig(
          staffConfig(
            StaffStatus.entries.filter { it != StaffStatus.ALL }.random(),
            capacity = 10,
          ),
        )
      }

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Indicated Prison")))

    val historicAllocations =
      staffConfig.mapIndexed { i, a ->
        val allocation =
          givenAllocation(
            staffAllocation(
              personIdentifier = prisonNumber,
              prisonCode = prisonCode,
              staffId = a.staffId,
              allocatedAt = LocalDateTime.now().minusWeeks(i + 1L),
              allocatedBy = "AS$i",
              active = false,
              deallocatedAt = LocalDateTime.now().minusWeeks(i.toLong()),
              deallocationReason =
                DeallocationReason.entries
                  .filter {
                    it !in
                      listOf(
                        DeallocationReason.PRISON_USES_KEY_WORK,
                        DeallocationReason.MIGRATION,
                      )
                  }.random(),
              deallocatedBy = "DS$i",
            ),
          )
        manageUsersMockServer.stubGetUserDetails(allocation.allocatedBy, newId().toString(), "Allocating User $i")
        manageUsersMockServer.stubGetUserDetails(
          allocation.deallocatedBy!!,
          newId().toString(),
          "Deallocating User $i",
        )
      }

    val currentAllocation =
      givenAllocation(
        staffAllocation(
          personIdentifier = prisonNumber,
          prisonCode = prisonCode,
          staffId = newId(),
          allocatedAt = LocalDateTime.now(),
          active = true,
          allocatedBy = "A110C473",
        ),
      )

    val historic = staffConfig.mapIndexed { i, s -> staffSummary("Forename ${i + 1}", "Surname ${i + 1}", s.staffId) }
    prisonMockServer.stubStaffSummaries(historic + staffSummary("Current", "Keyworker", currentAllocation.staffId))
    manageUsersMockServer.stubGetUserDetails(currentAllocation.allocatedBy, newId().toString(), "Current Allocator")

    val response: StaffAllocationHistory =
      getAllocationHistorySpec(prisonNumber, policy)
        .expectStatus()
        .isOk
        .expectBody(StaffAllocationHistory::class.java)
        .returnResult()
        .responseBody!!

    with(response.allocations) {
      assertThat(size).isEqualTo(historicAllocations.size + 1)
      assertThat(first().active).isEqualTo(true)
      with(single { it.active }) {
        assertThat(prison.description).isEqualTo("Indicated Prison")
        assertThat(staffMember.staffId).isEqualTo(currentAllocation.staffId)
        assertThat(staffMember.firstName).isEqualTo("Current")
        assertThat(staffMember.lastName).isEqualTo("Keyworker")
        assertThat(allocated.by).isEqualTo("Current Allocator")
      }
      assertThat(
        filter { !it.active },
      ).allMatch { it.deallocated != null && it.deallocated.by.matches("(Deallocating User [0-4])|(User)".toRegex()) }
      val idOrder = map { it.staffMember.staffId }
      val reOrdered = sortedByDescending { it.allocated.at }.map { it.staffMember.staffId }
      assertThat(idOrder).containsExactlyElementsOf(reOrdered)
    }
  }

  private fun getAllocationHistorySpec(
    prisonNumber: String,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri {
      it.path(ALLOCATION_HISTORY_URL)
      it.build(prisonNumber)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  fun staffSummary(
    firstName: String = "First",
    lastName: String = "Last",
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val ALLOCATION_HISTORY_URL = "/prisoners/{prisonNumber}/allocations"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
