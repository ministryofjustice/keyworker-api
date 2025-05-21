package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffDeallocation
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
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

    givenKeyworkerConfig(keyworkerConfig(KeyworkerStatus.INACTIVE, staff.staffId))

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("A provided staff id is not an active keyworker")
  }

  @Test
  fun `204 no content - new allocations`() {
    val prisonCode = "NAL"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staff = staffRole()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staff.staffId) }
    prisonerSearchMockServer.stubFindPrisonDetails(prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf(staff))

    allocationAndDeallocate(prisonCode, personStaffAllocations(psas)).expectStatus().isNoContent

    val allocations = keyworkerAllocationRepository.findActiveForPrisonStaff(prisonCode, staff.staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }
  }

  @Test
  fun `204 no content - new allocations overriding recommended allocation`() {
    val prisonCode = "NOR"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val prisoner = prisoner(prisonCode)
    val staff = staffRole()
    val recommendedStaff = staffRole()
    val psa =
      personStaffAllocation(
        prisoner.prisonerNumber,
        staff.staffId,
        recommendedAllocationStaffId = recommendedStaff.staffId,
      )
    prisonerSearchMockServer.stubFindPrisonDetails(setOf(prisoner.prisonerNumber), listOf(prisoner))
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf(staff))

    allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa))).expectStatus().isNoContent

    val allocations = keyworkerAllocationRepository.findAllByPersonIdentifier(prisoner.prisonerNumber)
    assertThat(allocations).hasSize(2)
    val active = allocations.single { it.active }
    val inactive = allocations.single { !it.active }
    assertThat(active.staffId).isEqualTo(staff.staffId)
    assertThat(active.allocationType).isEqualTo(AllocationType.MANUAL)
    assertThat(active.allocationReason.code).isEqualTo(AllocationReason.MANUAL.reasonCode)
    assertThat(inactive.staffId).isEqualTo(recommendedStaff.staffId)
    assertThat(inactive.allocationType).isEqualTo(AllocationType.AUTO)
    assertThat(inactive.allocationReason.code).isEqualTo(AllocationReason.AUTO.reasonCode)
    assertThat(inactive.deallocationReason?.code).isEqualTo(DeallocationReason.OVERRIDE.reasonCode)
  }

  @Test
  fun `204 no content - new allocations and existing allocations deallocated`() {
    val prisonCode = "EAL"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staff = staffRole()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staff.staffId) }
    prisonerSearchMockServer.stubFindPrisonDetails(prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf(staff))
    val existingAllocations =
      prisoners.map { givenKeyworkerAllocation(keyworkerAllocation(it.prisonerNumber, prisonCode)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(psas)).expectStatus().isNoContent

    val allocations = keyworkerAllocationRepository.findActiveForPrisonStaff(prisonCode, staff.staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }

    keyworkerAllocationRepository.findAllById(existingAllocations.map { it.id }).forEach { allocation ->
      assertThat(allocation.active).isFalse
      assertThat(allocation.expiryDateTime).isNotNull
      assertThat(allocation.deallocationReason?.code).isEqualTo(DeallocationReason.OVERRIDE.reasonCode)
    }
  }

  @Test
  fun `204 no content - no changes if already allocated to same staff`() {
    val prisonCode = "NNA"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staff = staffRole()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staff.staffId) }
    prisonerSearchMockServer.stubFindPrisonDetails(prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf(staff))
    val existingAllocations =
      prisoners.map { givenKeyworkerAllocation(keyworkerAllocation(it.prisonerNumber, prisonCode, staff.staffId)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(psas)).expectStatus().isNoContent

    val allocations = keyworkerAllocationRepository.findActiveForPrisonStaff(prisonCode, staff.staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }

    keyworkerAllocationRepository.findAllById(existingAllocations.map { it.id }).forEach { allocation ->
      assertThat(allocation.active).isTrue
      assertThat(allocation.expiryDateTime).isNull()
      assertThat(allocation.deallocationReason?.code).isNull()
    }
  }

  @Test
  fun `204 no content - deallocate existing allocations`() {
    val prisonCode = "DAL"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val staffId = newId()
    val psds = listOf(personStaffDeallocation(staffId = staffId), personStaffDeallocation(staffId = staffId))
    val existing = psds.map { givenKeyworkerAllocation(keyworkerAllocation(it.personIdentifier, prisonCode, staffId)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(deallocations = psds)).expectStatus().isNoContent

    val allocations = keyworkerAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations.isEmpty()).isTrue

    keyworkerAllocationRepository.findAllById(existing.map { it.id }).forEach { allocation ->
      assertThat(allocation.active).isFalse
      assertThat(allocation.expiryDateTime).isNotNull
      assertThat(allocation.deallocationReason?.code).isEqualTo(DeallocationReason.KEYWORKER_STATUS_CHANGE.reasonCode)
    }
  }

  @Test
  fun `204 no content - doesn't deallocate unless person and staff match`() {
    val prisonCode = "DDA"
    givenPrisonConfig(prisonConfig(prisonCode, migrated = true))

    val staffId = newId()
    val psds = listOf(personStaffDeallocation(), personStaffDeallocation())
    val existing = psds.map { givenKeyworkerAllocation(keyworkerAllocation(it.personIdentifier, prisonCode, staffId)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(deallocations = psds)).expectStatus().isNoContent

    val allocations = keyworkerAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations.isEmpty()).isFalse

    keyworkerAllocationRepository.findAllById(existing.map { it.id }).forEach { allocation ->
      assertThat(allocation.active).isTrue
      assertThat(allocation.expiryDateTime).isNull()
      assertThat(allocation.deallocationReason?.code).isNull()
    }
  }

  private fun prisoner(
    prisonCode: String,
    personIdentifier: String = personIdentifier(),
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
      ComplexityOfNeedLevel.LOW,
      LocalDate.now().minusDays(10),
    )

  private fun staffRole(staffId: Long = newId()) = StaffLocationRoleDto.builder().staffId(staffId).build()

  private fun personStaffAllocation(
    personIdentifier: String = personIdentifier(),
    staffId: Long = newId(),
    allocationReason: String = AllocationReason.MANUAL.name,
    recommendedAllocationStaffId: Long? = null,
  ) = PersonStaffAllocation(personIdentifier, staffId, allocationReason, recommendedAllocationStaffId)

  private fun personStaffDeallocation(
    personIdentifier: String = personIdentifier(),
    staffId: Long = newId(),
    deallocationReason: String = DeallocationReason.KEYWORKER_STATUS_CHANGE.name,
  ) = PersonStaffDeallocation(personIdentifier, staffId, deallocationReason)

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
