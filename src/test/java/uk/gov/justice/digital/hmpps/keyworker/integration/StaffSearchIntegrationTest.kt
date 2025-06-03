package uk.gov.justice.digital.hmpps.keyworker.integration

import com.nimbusds.jose.jwk.JWKParameterNames.KEY_TYPE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthEntries
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthSessions
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaff
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaffMembers
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.math.BigDecimal
import java.time.LocalDate

class StaffSearchIntegrationTest : IntegrationTest() {
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
    searchStaffSpec("DNM", searchRequest(), policy = AllocationPolicy.KEY_WORKER, "ROLE_ANY__OTHER_RW")
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `can filter keyworkers and decorate with config and counts`() {
    val policy = AllocationPolicy.KEY_WORKER
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "SFI"
    givenPrisonConfig(prisonConfig(prisonCode))

    val staffIds = (0..10).map { newId() }
    val request = searchRequest()
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, request, NomisStaffMembers(nomisStaff(staffIds)))
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
          givenKeyworkerAllocation(
            keyworkerAllocation(
              personIdentifier(),
              prisonCode,
              kw.staffId,
              allocationType = if (index == 7 && it == 7) AllocationType.PROVISIONAL else AllocationType.AUTO,
            ),
          )
        }
      }.flatten()

    val sessionUsage =
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
      response = sessionUsage,
    )

    val entryUsage =
      NoteUsageResponse(
        staffIds
          .mapIndexed { index, staffId ->
            UsageByAuthorIdResponse(
              staffId.toString(),
              KEY_TYPE,
              KW_ENTRY_SUBTYPE,
              index / 2,
            )
          }.groupBy { it.authorId },
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthEntries(staffIds.map(Long::toString).toSet()),
      response = entryUsage,
    )

    val response =
      searchStaffSpec(prisonCode, request)
        .expectStatus()
        .isOk
        .expectBody(StaffSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content.map { it.status.code }.toSet()).containsOnly(ACTIVE.name)

    assertThat(response.content[0].staffId).isEqualTo(staffIds[0])
    assertThat(response.content[0].autoAllocationAllowed).isEqualTo(false)
    assertThat(response.content[0].numberAllocated).isEqualTo(0)
    assertThat(response.content[0].numberOfSessions).isEqualTo(0)
    assertThat(response.content[0].numberOfEntries).isEqualTo(0)

    assertThat(response.content.find { it.staffId == staffIds[3] }).isNull()

    assertThat(response.content[4].staffId).isEqualTo(staffIds[5])
    assertThat(response.content[4].autoAllocationAllowed).isEqualTo(false)
    assertThat(response.content[4].numberAllocated).isEqualTo(0)
    assertThat(response.content[4].numberOfSessions).isEqualTo(5)
    assertThat(response.content[4].numberOfEntries).isEqualTo(2)
    assertThat(response.content[4].staffRole).isNotNull

    assertThat(response.content[6].staffId).isEqualTo(staffIds[8])
    assertThat(response.content[6].autoAllocationAllowed).isEqualTo(true)
    assertThat(response.content[6].numberAllocated).isEqualTo(7)
    assertThat(response.content[6].numberOfSessions).isEqualTo(8)
    assertThat(response.content[6].numberOfEntries).isEqualTo(4)
    assertThat(response.content[6].staffRole).isNotNull
  }

  @Test
  fun `can find all keyworkers with config and counts`() {
    val policy = AllocationPolicy.KEY_WORKER
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "STA"
    givenPrisonConfig(prisonConfig(prisonCode))

    val staffIds = (0..10).map { newId() }
    val request = searchRequest(status = StaffSearchRequest.Status.ALL)
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, request, NomisStaffMembers(nomisStaff(staffIds)))
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
          givenKeyworkerAllocation(
            keyworkerAllocation(
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

    val entryUsage =
      NoteUsageResponse(
        staffIds
          .mapIndexed { index, staffId ->
            UsageByAuthorIdResponse(
              staffId.toString(),
              KW_TYPE,
              KW_ENTRY_SUBTYPE,
              index / 2,
            )
          }.groupBy { it.authorId },
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthEntries(staffIds.map(Long::toString).toSet()),
      response = entryUsage,
    )

    val response =
      searchStaffSpec(prisonCode, request)
        .expectStatus()
        .isOk
        .expectBody(StaffSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(11)
  }

  @Test
  fun `can find allocation counts when no keyworker record`() {
    val policy = AllocationPolicy.KEY_WORKER
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "NSR"
    givenPrisonConfig(prisonConfig(prisonCode))

    val staffId = newId()
    val request = searchRequest()
    nomisUserRolesMockServer.stubGetUserStaff(
      prisonCode,
      request,
      NomisStaffMembers(nomisStaff(listOf(staffId))),
    )
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))

    givenKeyworkerAllocation(keyworkerAllocation(personIdentifier(), prisonCode, staffId))
    givenKeyworkerAllocation(keyworkerAllocation(personIdentifier(), prisonCode, staffId))

    val sessionUsage =
      NoteUsageResponse(
        mapOf(
          "$staffId" to listOf(UsageByAuthorIdResponse("$staffId", KW_TYPE, KW_SESSION_SUBTYPE, 7)),
        ),
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthSessions(setOf("$staffId")),
      response = sessionUsage,
    )

    val entryUsage =
      NoteUsageResponse(
        mapOf(
          "$staffId" to listOf(UsageByAuthorIdResponse("$staffId", PO_ENTRY_TYPE, PO_ENTRY_SUBTYPE, 3)),
        ),
      )
    caseNotesMockServer.stubUsageByStaffIds(
      request = lastMonthEntries(setOf("$staffId")),
      response = entryUsage,
    )

    val response =
      searchStaffSpec(prisonCode, request)
        .expectStatus()
        .isOk
        .expectBody(StaffSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    val keyworker = response.content.single()
    assertThat(keyworker.capacity).isEqualTo(6)
    assertThat(keyworker.numberAllocated).isEqualTo(2)
    assertThat(keyworker.numberOfSessions).isEqualTo(7)
    assertThat(keyworker.numberOfEntries).isEqualTo(3)
    assertThat(keyworker.staffRole).isNotNull
  }

  private fun searchRequest(
    query: String? = null,
    status: StaffSearchRequest.Status = StaffSearchRequest.Status.ACTIVE,
    hasPolicyStaffRole: Boolean = true,
  ) = StaffSearchRequest(query, status, hasPolicyStaffRole)

  private fun searchStaffSpec(
    prisonCode: String,
    request: StaffSearchRequest,
    policy: AllocationPolicy = AllocationPolicy.KEY_WORKER,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  private fun nomisStaff(staffIds: List<Long>): List<NomisStaff> =
    staffIds.map {
      NomisStaff(
        it,
        "First$it",
        "Last$it",
        "ACTIVE",
      )
    }

  private fun staffRoles(staffIds: List<Long>): List<StaffLocationRoleDto> {
    val positionTypes = listOf("AA", "AO", "PPO", "PRO", "CHAP")
    val scheduleTypes = listOf("FT", "PT", "SESS", "VOL")
    return staffIds.map {
      StaffLocationRoleDto
        .builder()
        .staffId(it)
        .firstName("First Name $it")
        .lastName("Last Name $it")
        .position(positionTypes.random())
        .scheduleType(scheduleTypes.random())
        .hoursPerWeek(BigDecimal.valueOf(37.5))
        .fromDate(LocalDate.now().minusDays(it * 64))
        .build()
    }
  }

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/staff"
  }
}
