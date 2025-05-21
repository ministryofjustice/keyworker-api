package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexOffender
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate

class PersonSearchIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .post()
      .uri(SEARCH_URL, "NEP")
      .bodyValue(searchRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    searchPersonSpec("DNM", searchRequest(), "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `can filter people and decorate with keyworker`() {
    val prisonCode = "FIND"
    givenPrisonConfig(prisonConfig(prisonCode))

    val prisoners = prisoners(prisonCode, 10)
    prisonerSearchMockServer.stubFindFilteredPrisoners(
      prisonCode,
      prisoners,
      mapOf("cellLocationPrefix" to "$prisonCode-A"),
    )

    val staffIds = (0..6).map { newId() }

    val allocations =
      prisoners.content.mapIndexedNotNull { index, p ->
        if (index == 0) {
          null
        } else {
          givenKeyworkerAllocation(
            keyworkerAllocation(
              p.prisonerNumber,
              prisonCode,
              staffIds.random(),
              allocationType = if (index % 5 == 0) AllocationType.PROVISIONAL else AllocationType.AUTO,
              active = index % 3 != 0,
            ),
          )
        }
      }

    val summaries =
      allocations
        .filter { it.active && it.allocationType != AllocationType.PROVISIONAL }
        .map { it.staffId }
        .distinct()
        .map { StaffSummary(it, "Keyworker$it", "Staff$it") }
    prisonMockServer.stubKeyworkerSummaries(summaries)

    val response =
      searchPersonSpec(prisonCode, searchRequest(cellLocationPrefix = "$prisonCode-A"))
        .expectStatus()
        .isOk
        .expectBody(PersonSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(10)
    val none = requireNotNull(response.content.find { it.location == "$prisonCode-A-1" })
    with(none) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isFalse
      assertThat(keyworker).isNull()
    }
    val history = requireNotNull(response.content.find { it.location == "$prisonCode-A-4" })
    with(history) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(keyworker).isNull()
    }
    val active = requireNotNull(response.content.find { it.location == "$prisonCode-A-3" })
    with(active) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(keyworker).isNotNull
    }
  }

  @Test
  fun `can filter complex needs people and decorate with keyworker`() {
    val prisonCode = "COMP"
    givenPrisonConfig(prisonConfig(prisonCode, hasPrisonersWithHighComplexityNeeds = true))

    val prisoners = prisoners(prisonCode, 6)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners, mapOf("term" to "First"))

    complexityOfNeedMockServer.stubComplexOffenders(
      prisoners.personIdentifiers(),
      prisoners.personIdentifiers().mapIndexedNotNull { index, personIdentifier ->
        if (index % 3 == 0) {
          null
        } else {
          ComplexOffender(
            personIdentifier,
            if (index % 2 == 0) ComplexityOfNeedLevel.HIGH else ComplexityOfNeedLevel.MEDIUM,
          )
        }
      },
    )

    val staffIds = (0..6).map { newId() }

    val allocations =
      prisoners.content.mapIndexedNotNull { index, p ->
        if (index == 0) {
          null
        } else {
          givenKeyworkerAllocation(
            keyworkerAllocation(
              p.prisonerNumber,
              prisonCode,
              staffIds.random(),
              allocationType = if (index % 5 == 0) AllocationType.PROVISIONAL else AllocationType.AUTO,
              active = index % 3 != 0,
            ),
          )
        }
      }

    val summaries =
      allocations
        .filter { it.active && it.allocationType != AllocationType.PROVISIONAL }
        .map { it.staffId }
        .distinct()
        .map { StaffSummary(it, "Keyworker$it", "Staff$it") }
    prisonMockServer.stubKeyworkerSummaries(summaries)

    val response =
      searchPersonSpec(prisonCode, searchRequest(query = "First"))
        .expectStatus()
        .isOk
        .expectBody(PersonSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(6)
    val none = requireNotNull(response.content.find { it.firstName == "First1" })
    with(none) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isFalse
      assertThat(keyworker).isNull()
    }
    val history = requireNotNull(response.content.find { it.firstName == "First4" })
    with(history) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(keyworker).isNull()
    }
    val active = requireNotNull(response.content.find { it.firstName == "First3" })
    with(active) {
      assertThat(hasHighComplexityOfNeeds).isTrue
      assertThat(hasAllocationHistory).isTrue
      assertThat(keyworker).isNotNull
    }
  }

  @Test
  fun `can exclude active and decorate with keyworker`() {
    val prisonCode = "EXAC"
    givenPrisonConfig(prisonConfig(prisonCode))

    val prisoners = prisoners(prisonCode, 10)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staffIds = (0..6).map { newId() }
    val summaries = staffIds.map { StaffSummary(it, "Keyworker$it", "Staff$it") }
    prisonMockServer.stubKeyworkerSummaries(summaries)

    prisoners.content.mapIndexed { index, p ->
      if (index == 0) {
        null
      } else {
        givenKeyworkerAllocation(
          keyworkerAllocation(
            p.prisonerNumber,
            prisonCode,
            staffIds.random(),
            allocationType = if (index % 5 == 0) AllocationType.PROVISIONAL else AllocationType.AUTO,
            active = index % 3 != 0,
          ),
        )
      }
    }

    val response =
      searchPersonSpec(prisonCode, searchRequest(excludeActiveAllocations = true))
        .expectStatus()
        .isOk
        .expectBody(PersonSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content.filter { it.keyworker != null }).isEmpty()
    val none = requireNotNull(response.content.find { it.location == "$prisonCode-A-1" })
    with(none) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isFalse
      assertThat(keyworker).isNull()
    }
    val history = requireNotNull(response.content.find { it.location == "$prisonCode-A-4" })
    with(history) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(keyworker).isNull()
    }
  }

  private fun searchRequest(
    query: String? = null,
    cellLocationPrefix: String? = null,
    excludeActiveAllocations: Boolean = false,
  ) = PersonSearchRequest(query, cellLocationPrefix, excludeActiveAllocations)

  private fun searchPersonSpec(
    prisonCode: String,
    request: PersonSearchRequest,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
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
          null,
        )
      },
  )

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/prisoners"
  }
}
