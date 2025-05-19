package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate
import java.time.LocalDateTime

class RecommendKeyworkerAllocationIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_KEYWORKER_RECOMMENDATIONS, "NAU")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getKeyworkerRecommendations("DNM", "ROLE_ANY__OTHER_RO").expectStatus().isForbidden
  }

  @Test
  fun `identifies cases that have no recommendations when all keyworkers are at max capacity`() {
    val prisonCode = "FUL"
    givenPrisonConfig(prisonConfig(prisonCode, capacityTier1 = 1, capacityTier2 = null))
    val prisoners = prisoners(prisonCode, 10)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staff = (0..2).map { staffDetail() }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staff)

    staff.map {
      givenKeyworkerAllocation(keyworkerAllocation(personIdentifier(), prisonCode, it.staffId))
    }

    val res =
      getKeyworkerRecommendations(prisonCode)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    assertThat(res.noAvailableKeyworkersFor).containsExactlyInAnyOrderElementsOf(prisoners.content.map { it.prisonerNumber })
    assertThat(res.allocations).isEmpty()
  }

  @Test
  fun `will recommend previous keyworker regardless of capacity`() {
    val prisonCode = "EXI"
    givenPrisonConfig(prisonConfig(prisonCode, capacityTier1 = 1, capacityTier2 = null))
    val prisoners = prisoners(prisonCode, 6)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staffWithCapacity = (0..2).map { staffDetail() }
    val staffAtCapacity = (0..2).map { staffDetail() }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffWithCapacity + staffAtCapacity)

    val prisonersReversed = prisoners.content.reversed()
    val previousAllocations =
      staffAtCapacity
        .mapIndexed { i, s ->
          givenKeyworkerAllocation(
            keyworkerAllocation(
              prisonersReversed[i].prisonerNumber,
              prisonCode,
              s.staffId,
              active = false,
              expiryDateTime = LocalDateTime.now().minusDays(1),
              deallocationReason = DeallocationReason.KEYWORKER_STATUS_CHANGE,
            ),
          )
        }.associateBy { it.staffId }
    staffAtCapacity.map { s ->
      givenKeyworkerAllocation(
        keyworkerAllocation(personIdentifier(), prisonCode, s.staffId),
      )
    }

    val res =
      getKeyworkerRecommendations(prisonCode)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    assertThat(res.noAvailableKeyworkersFor).isEmpty()
    assertThat(res.allocations).containsAll(
      staffAtCapacity.map { s ->
        RecommendedAllocation(
          previousAllocations[s.staffId]!!.personIdentifier,
          Keyworker(s.staffId, s.firstName, s.lastName),
        )
      },
    )
  }

  @Test
  fun `will balance recommendations based on capacity and report when keyworkers are at max capacity`() {
    val prisonCode = "BAL"
    givenPrisonConfig(prisonConfig(prisonCode, capacityTier1 = 6, capacityTier2 = 9))
    val prisoners = prisoners(prisonCode, 16)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staff = (0..4).map { staffDetail() }
    staff.map { givenKeyworkerConfig(keyworkerConfig(KeyworkerStatus.ACTIVE, it.staffId, 4)) }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staff)
    staff.mapIndexed { i, s ->
      (1..5 - i).map {
        givenKeyworkerAllocation(
          keyworkerAllocation(personIdentifier(), prisonCode, s.staffId),
        )
      }
    }

    val res =
      getKeyworkerRecommendations(prisonCode)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    val allocMap = res.allocations.groupBy({ it.keyworker.staffId }, { it.personIdentifier })
    assertThat(allocMap.values.map { it.size }.sortedDescending()).containsExactly(5, 4, 3, 2, 1)
    assertThat(res.noAvailableKeyworkersFor).containsExactly(prisoners.content.maxBy { it.lastName }.prisonerNumber)
  }

  private fun getKeyworkerRecommendations(
    prisonCode: String,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_KEYWORKER_RECOMMENDATIONS)
      it.build(prisonCode)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  private fun prisoners(
    prisonCode: String,
    count: Int,
  ) = Prisoners(
    (1..count)
      .map {
        Prisoner(
          personIdentifier(),
          "First$it",
          "Last$it",
          LocalDate.now().minusWeeks(it.toLong()),
          null,
          prisonCode,
          "Description of $prisonCode",
          "$prisonCode-A-$it",
          "STANDARD",
        )
      },
  )

  private fun staffDetail(
    id: Long = newId(),
    firstName: String = "First $id",
    lastName: String = "Last $id",
  ): StaffLocationRoleDto =
    StaffLocationRoleDto
      .builder()
      .staffId(id)
      .firstName(firstName)
      .lastName(lastName)
      .build()

  companion object {
    const val GET_KEYWORKER_RECOMMENDATIONS = "/prisons/{prisonCode}/prisoners/keyworker-recommendations"
  }
}
