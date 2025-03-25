package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffDeallocation
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonNumber
import java.time.LocalDate

class KeyworkerAllocationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(KEYWORKER_ALLOCATIONS_URL, "NEP")
      .bodyValue(personStaffAllocations())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    allocationAndDeallocate("DNM", personStaffAllocations(), "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - person staff allocations is empty`() {
    val res =
      allocationAndDeallocate("NEN", personStaffAllocations())
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("At least one allocation or deallocation must be provided")
  }

  @Test
  fun `400 bad request - prison not enabled`() {
    val res =
      allocationAndDeallocate("NEN", personStaffAllocations(listOf(personStaffAllocation())))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Prison not enabled for keyworker service")
  }

  @Test
  fun `400 bad request - prisoner not at provided prison`() {
    val prisonCode = "PRM"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))
    val psa = personStaffAllocation()
    prisonerSearchMockServer.stubFindPrisonDetails(setOf(psa.personIdentifier), listOf(prisoner(psa.personIdentifier)))

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("A provided person identifier is not currently at the provided prison")
  }

  @Test
  fun `400 bad request - staff id not a keyworker at provided prison`() {
    val prisonCode = "SNK"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val prisoner = prisoner(prisonCode)
    val staff = staffRole()
    val psa = personStaffAllocation(prisoner.prisonerNumber, staff.staffId)
    prisonerSearchMockServer.stubFindPrisonDetails(setOf(psa.personIdentifier), listOf(prisoner))
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf(staffRole()))

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("A provided staff id is not a keyworker for the provided prison")
  }

  @Test
  fun `400 bad request - staff id not an active keyworker`() {
    val prisonCode = "SIK"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val prisoner = prisoner(prisonCode)
    val staff = staffRole()
    val psa = personStaffAllocation(prisoner.prisonerNumber, staff.staffId)
    prisonerSearchMockServer.stubFindPrisonDetails(setOf(psa.personIdentifier), listOf(prisoner))
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf(staff))

    givenKeyworker(keyworker(KeyworkerStatus.INACTIVE, staff.staffId))

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("A provided staff id is not an active keyworker")
  }

  private fun prisoner(
    prisonCode: String,
    personIdentifier: String = prisonNumber(),
  ): Prisoner =
    Prisoner(
      personIdentifier,
      "First",
      "Last",
      LocalDate.now().minusDays(10),
      null,
      prisonCode,
      "Description of $prisonCode",
      null,
      null,
    )

  private fun staffRole(staffId: Long = newId()) = StaffLocationRoleDto.builder().staffId(staffId).build()

  private fun personStaffAllocation(
    personIdentifier: String = prisonNumber(),
    staffId: Long = newId(),
    allocationReason: String = AllocationReason.MANUAL.name,
  ) = PersonStaffAllocation(personIdentifier, staffId, allocationReason)

  private fun personStaffAllocations(
    allocations: List<PersonStaffAllocation> = emptyList(),
    deallocations: List<PersonStaffDeallocation> = emptyList(),
  ) = PersonStaffAllocations(allocations, deallocations)

  private fun allocationAndDeallocate(
    prisonCode: String,
    request: PersonStaffAllocations,
    role: String? = Roles.KEYWORKER_RW,
  ) = webTestClient
    .put()
    .uri(KEYWORKER_ALLOCATIONS_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val KEYWORKER_ALLOCATIONS_URL = "/prisons/{prisonCode}/prisoners/keyworkers"
  }
}
