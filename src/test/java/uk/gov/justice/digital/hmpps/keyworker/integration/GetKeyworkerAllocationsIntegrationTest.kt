package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime

class GetKeyworkerAllocationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_KEYWORKER_ALLOCATIONS, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getKeyworkerAllocationSpec(personIdentifier(), role = "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok and all allocations returned`() {
    val prisonCode = "HAL"
    val prisonNumber = personIdentifier()
    val keyworkers = (0..4).map { givenStaffConfig(staffConfig(StaffStatus.entries.random(), capacity = 10)) }

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison("HAL", "Indicated Prison")))

    val historicAllocations =
      keyworkers.mapIndexed { i, a ->
        val allocation =
          givenKeyworkerAllocation(
            keyworkerAllocation(
              personIdentifier = prisonNumber,
              prisonCode = prisonCode,
              staffId = a.staffId,
              assignedAt = LocalDateTime.now().minusWeeks(i + 1L),
              allocatedBy = "AS$i",
              active = false,
              expiryDateTime = LocalDateTime.now().minusWeeks(i.toLong()),
              deallocationReason = DeallocationReason.entries.random(),
            ).apply {
              if (i != 3) {
                lastModifiedBy = "DS$i"
              }
            },
          )
        manageUsersMockServer.stubGetUserDetails(allocation.allocatedBy, newId().toString(), "Allocating User $i")
        if (i == 2) {
          manageUsersMockServer.stubGetUserDetailsNotFound(allocation.lastModifiedBy!!)
        } else {
          manageUsersMockServer.stubGetUserDetails(
            allocation.lastModifiedBy!!,
            newId().toString(),
            "Deallocating User $i",
          )
        }
      }

    val currentAllocation =
      givenKeyworkerAllocation(
        keyworkerAllocation(
          personIdentifier = prisonNumber,
          prisonCode = prisonCode,
          staffId = newId(),
          assignedAt = LocalDateTime.now(),
          active = true,
          allocatedBy = "A110C473",
        ),
      )

    val historic = keyworkers.mapIndexed { i, s -> staffSummary("Forename ${i + 1}", "Surname ${i + 1}", s.staffId) }
    prisonMockServer.stubStaffSummaries(historic + staffSummary("Current", "Keyworker", currentAllocation.staffId))
    manageUsersMockServer.stubGetUserDetails(currentAllocation.allocatedBy, newId().toString(), "Current Allocator")

    val response =
      getKeyworkerAllocationSpec(prisonNumber)
        .expectStatus()
        .isOk
        .expectBody(PersonStaffAllocationHistory::class.java)
        .returnResult()
        .responseBody!!

    with(response.allocations) {
      assertThat(size).isEqualTo(historicAllocations.size + 1)
      assertThat(first().active).isEqualTo(true)
      with(single { it.active }) {
        assertThat(prison.description).isEqualTo("Indicated Prison")
        assertThat(keyworker.staffId).isEqualTo(currentAllocation.staffId)
        assertThat(keyworker.firstName).isEqualTo("Current")
        assertThat(keyworker.lastName).isEqualTo("Keyworker")
        assertThat(allocated.by).isEqualTo("Current Allocator")
      }
      assertThat(
        filter { !it.active },
      ).allMatch { it.deallocated != null && it.deallocated.by.matches("(Deallocating User [0-4])|(User)".toRegex()) }
      val idOrder = map { it.keyworker.staffId }
      val reOrdered = sortedByDescending { it.allocated.at }.map { it.keyworker.staffId }
      assertThat(idOrder).containsExactlyElementsOf(reOrdered)
    }
  }

  private fun getKeyworkerAllocationSpec(
    prisonNumber: String,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_KEYWORKER_ALLOCATIONS)
      it.build(prisonNumber)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  fun staffSummary(
    firstName: String = "First",
    lastName: String = "Last",
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val GET_KEYWORKER_ALLOCATIONS = "/prisoners/{prisonNumber}/keyworkers"
  }
}
