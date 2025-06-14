package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthSessions
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier

class KeyworkerSearchIntegrationTest : IntegrationTest() {
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
    searchKeyworkerSpec("DNM", searchRequest(), "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `can filter keyworkers and decorate with config and counts`() {
    val prisonCode = "FIND"
    givenPrisonConfig(prisonConfig(prisonCode))

    val staffIds = (0..10).map { newId() }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(staffIds))

    val keyworkerConfigs: List<StaffConfiguration> =
      staffIds.mapIndexedNotNull { index, staffId ->
        if (index % 5 == 0) {
          null
        } else {
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6, true))
        }
      }

    keyworkerConfigs
      .mapIndexed { index, kw ->
        (0..index).map {
          givenAllocation(
            staffAllocation(
              personIdentifier(),
              prisonCode,
              kw.staffId,
              allocationType = if (index == 7 && it == 7) AllocationType.PROVISIONAL else AllocationType.AUTO,
            ),
          )
        }
      }.flatten()

    val noteUsage =
      NoteUsageResponse(
        staffIds
          .mapIndexed { index, staffId ->
            UsageByAuthorIdResponse(
              staffId.toString(),
              KW_TYPE,
              KW_SESSION_SUBTYPE,
              index,
            )
          }.groupBy { it.authorId },
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthSessions(staffIds.map(Long::toString).toSet()),
      response = noteUsage,
    )

    val response =
      searchKeyworkerSpec(prisonCode, searchRequest())
        .expectStatus()
        .isOk
        .expectBody(KeyworkerSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content.map { it.status.code }.toSet()).containsOnly(ACTIVE.statusCode)

    assertThat(response.content[0].staffId).isEqualTo(staffIds[0])
    assertThat(response.content[0].autoAllocationAllowed).isEqualTo(false)
    assertThat(response.content[0].numberAllocated).isEqualTo(0)
    assertThat(response.content[0].numberOfKeyworkerSessions).isEqualTo(0)

    assertThat(response.content.find { it.staffId == staffIds[3] }).isNull()

    assertThat(response.content[4].staffId).isEqualTo(staffIds[5])
    assertThat(response.content[4].autoAllocationAllowed).isEqualTo(false)
    assertThat(response.content[4].numberAllocated).isEqualTo(0)
    assertThat(response.content[4].numberOfKeyworkerSessions).isEqualTo(5)

    assertThat(response.content[6].staffId).isEqualTo(staffIds[8])
    assertThat(response.content[6].autoAllocationAllowed).isEqualTo(true)
    assertThat(response.content[6].numberAllocated).isEqualTo(7)
    assertThat(response.content[6].numberOfKeyworkerSessions).isEqualTo(8)
  }

  @Test
  fun `can find all keyworkers with config and counts`() {
    val prisonCode = "ALL"
    givenPrisonConfig(prisonConfig(prisonCode))

    val staffIds = (0..10).map { newId() }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(staffIds))

    val keyworkerConfigs: List<StaffConfiguration> =
      staffIds.mapIndexedNotNull { index, staffId ->
        if (index % 5 == 0) {
          null
        } else {
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6, true))
        }
      }

    keyworkerConfigs
      .mapIndexed { index, kw ->
        (0..index).map {
          givenAllocation(
            staffAllocation(
              personIdentifier(),
              prisonCode,
              kw.staffId,
              allocationType = if (index == 7 && it == 7) AllocationType.PROVISIONAL else AllocationType.AUTO,
            ),
          )
        }
      }.flatten()

    val noteUsage =
      NoteUsageResponse(
        staffIds
          .mapIndexed { index, staffId ->
            UsageByAuthorIdResponse(
              staffId.toString(),
              KW_TYPE,
              KW_SESSION_SUBTYPE,
              index,
            )
          }.groupBy { it.authorId },
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthSessions(staffIds.map(Long::toString).toSet()),
      response = noteUsage,
    )

    val response =
      searchKeyworkerSpec(prisonCode, searchRequest(status = KeyworkerSearchRequest.Status.ALL))
        .expectStatus()
        .isOk
        .expectBody(KeyworkerSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(11)
  }

  @Test
  fun `can find allocation counts when no keyworker record`() {
    val prisonCode = "NKR"
    givenPrisonConfig(prisonConfig(prisonCode))

    val staffId = newId()
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))

    givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
    givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))

    val noteUsage =
      NoteUsageResponse(
        mapOf(
          "$staffId" to listOf(UsageByAuthorIdResponse("$staffId", KW_TYPE, KW_SESSION_SUBTYPE, 7)),
        ),
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthSessions(setOf("$staffId")),
      response = noteUsage,
    )

    val response =
      searchKeyworkerSpec(prisonCode, searchRequest())
        .expectStatus()
        .isOk
        .expectBody(KeyworkerSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    val keyworker = response.content.single()
    assertThat(keyworker.capacity).isEqualTo(6)
    assertThat(keyworker.numberAllocated).isEqualTo(2)
    assertThat(keyworker.numberOfKeyworkerSessions).isEqualTo(7)
  }

  private fun searchRequest(
    query: String? = null,
    status: KeyworkerSearchRequest.Status = KeyworkerSearchRequest.Status.ACTIVE,
  ) = KeyworkerSearchRequest(query, status)

  private fun searchKeyworkerSpec(
    prisonCode: String,
    request: KeyworkerSearchRequest,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  private fun staffRoles(staffIds: List<Long>) =
    staffIds.map {
      StaffLocationRoleDto
        .builder()
        .staffId(it)
        .firstName("First Name $it")
        .lastName("Last Name $it")
        .build()
    }

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/keyworkers"
  }
}
