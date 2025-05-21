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
  fun `will balance recommendations based on capacity availability and report when keyworkers are at max capacity`() {
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

    val sortedPrisoners = prisoners.content.sortedBy { it.lastName }
    val allocMap = res.allocations.groupBy({ it.keyworker.staffId }, { it.personIdentifier })
    assertThat(allocMap.toList()).containsExactlyInAnyOrder(
      staff[0].staffId to listOf(sortedPrisoners[14].prisonerNumber),
      staff[1].staffId to listOf(sortedPrisoners[9].prisonerNumber, sortedPrisoners[13].prisonerNumber),
      staff[2].staffId to
        listOf(
          sortedPrisoners[5].prisonerNumber,
          sortedPrisoners[8].prisonerNumber,
          sortedPrisoners[12].prisonerNumber,
        ),
      staff[3].staffId to
        listOf(
          sortedPrisoners[2].prisonerNumber,
          sortedPrisoners[4].prisonerNumber,
          sortedPrisoners[7].prisonerNumber,
          sortedPrisoners[11].prisonerNumber,
        ),
      staff[4].staffId to
        listOf(
          sortedPrisoners[0].prisonerNumber,
          sortedPrisoners[1].prisonerNumber,
          sortedPrisoners[3].prisonerNumber,
          sortedPrisoners[6].prisonerNumber,
          sortedPrisoners[10].prisonerNumber,
        ),
    )
    assertThat(res.noAvailableKeyworkersFor).containsExactly(sortedPrisoners.last().prisonerNumber)
  }

  @Test
  fun `will balance recommendations based on capacity availability when capacity is different`() {
    val prisonCode = "BAL"
    givenPrisonConfig(prisonConfig(prisonCode, capacityTier1 = 6, capacityTier2 = 9))
    val prisoners = prisoners(prisonCode, 16)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staff = (1..2).map { staffDetail() }
    givenKeyworkerConfig(keyworkerConfig(KeyworkerStatus.ACTIVE, staff[0].staffId, 4))
    givenKeyworkerConfig(keyworkerConfig(KeyworkerStatus.ACTIVE, staff[1].staffId, 8))
    prisonMockServer.stubKeyworkerSearch(prisonCode, staff)

    val res =
      getKeyworkerRecommendations(prisonCode)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    val sortedPrisoners = prisoners.content.sortedBy { it.lastName }
    val allocMap = res.allocations.groupBy({ it.keyworker.staffId }, { it.personIdentifier })
    assertThat(allocMap.toList()).containsExactlyInAnyOrder(
      staff[0].staffId to
        listOf(
          sortedPrisoners[0].prisonerNumber,
          sortedPrisoners[3].prisonerNumber,
          sortedPrisoners[6].prisonerNumber,
          sortedPrisoners[9].prisonerNumber,
          sortedPrisoners[12].prisonerNumber,
          sortedPrisoners[15].prisonerNumber,
        ),
      staff[1].staffId to
        listOf(
          sortedPrisoners[1].prisonerNumber,
          sortedPrisoners[2].prisonerNumber,
          sortedPrisoners[4].prisonerNumber,
          sortedPrisoners[5].prisonerNumber,
          sortedPrisoners[7].prisonerNumber,
          sortedPrisoners[8].prisonerNumber,
          sortedPrisoners[10].prisonerNumber,
          sortedPrisoners[11].prisonerNumber,
          sortedPrisoners[13].prisonerNumber,
          sortedPrisoners[14].prisonerNumber,
        ),
    )
    assertThat(res.noAvailableKeyworkersFor).isEmpty()
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
          null,
          LocalDate.now().minusWeeks(it.toLong()),
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
