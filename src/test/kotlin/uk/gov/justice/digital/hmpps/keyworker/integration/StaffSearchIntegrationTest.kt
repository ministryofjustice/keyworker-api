package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaffMembers
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.fromStaffIds
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.nomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.nomisStaffRoles
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can filter staff and decorate with config and counts`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "SFI"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffIds = (0..10).map { newId() }
    val request = searchRequest(query = "First")
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, NomisStaffMembers(fromStaffIds(staffIds)), request)
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, nomisStaffRoles(staffIds.filter { it % 2 != 0L }))
    } else {
      staffIds.filter { it % 2 != 0L }.forEach {
        givenStaffRole(staffRole(prisonCode, it))
      }
    }

    val staffConfigs: List<StaffConfiguration> =
      staffIds.mapIndexedNotNull { index, staffId ->
        if (index % 5 == 0) {
          null
        } else {
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6))
        }
      }

    val allocations =
      staffConfigs
        .flatMapIndexed { index, st ->
          (0..index).map {
            givenAllocation(
              staffAllocation(
                personIdentifier(),
                prisonCode,
                st.staffId,
              ),
            )
          }
        }.groupBy { it.staffId }

    if (policy == AllocationPolicy.KEY_WORKER) {
      staffConfigs.mapIndexed { idx, sc ->
        (0..idx).map {
          givenRecordedEvent(
            recordedEvent(
              prisonCode,
              RecordedEventType.SESSION,
              LocalDateTime.now().minusWeeks(2).truncatedTo(ChronoUnit.SECONDS),
              staffId = sc.staffId,
              personIdentifier = allocations[sc.staffId]!!.map { it.personIdentifier }.toSet().random(),
            ),
          )
        }
      }
    }

    staffConfigs.mapIndexed { idx, sc ->
      (0..idx / 2).map {
        givenRecordedEvent(
          recordedEvent(
            prisonCode,
            RecordedEventType.ENTRY,
            LocalDateTime.now().minusWeeks(2).truncatedTo(ChronoUnit.SECONDS),
            staffId = sc.staffId,
            personIdentifier = allocations[sc.staffId]!!.map { it.personIdentifier }.toSet().random(),
          ),
        )
      }
    }

    val response =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody<StaffSearchResponse>()
        .returnResult()
        .responseBody!!

    assertThat(response.content.map { it.status.code }.toSet()).containsOnly(ACTIVE.name)

    assertThat(response.content[0].staffId).isEqualTo(staffIds[0])
    assertThat(response.content[0].allocated).isEqualTo(0)
    assertThat(response.content[0].numberOfSessions).isEqualTo(0)
    assertThat(response.content[0].numberOfEntries).isEqualTo(0)

    assertThat(response.content.find { it.staffId == staffIds[3] }).isNull()

    assertThat(response.content[4].staffId).isEqualTo(staffIds[5])
    assertThat(response.content[4].allocated).isEqualTo(0)
    assertThat(response.content[4].numberOfSessions).isEqualTo(0)
    assertThat(response.content[4].numberOfEntries).isEqualTo(0)

    assertThat(response.content[5].staffId).isEqualTo(staffIds[7])
    assertThat(response.content[5].allocated).isEqualTo(6)
    assertThat(response.content[5].numberOfSessions).isEqualTo(if (policy == AllocationPolicy.KEY_WORKER) 6 else 0)
    assertThat(response.content[5].numberOfEntries).isEqualTo(3)

    assertThat(response.content[6].staffId).isEqualTo(staffIds[8])
    assertThat(response.content[6].allocated).isEqualTo(7)
    assertThat(response.content[6].numberOfSessions).isEqualTo(if (policy == AllocationPolicy.KEY_WORKER) 7 else 0)
    assertThat(response.content[6].numberOfEntries).isEqualTo(4)
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can find all staff with config and counts`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "STA"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffIds = (0..10).map { newId() }
    val request = searchRequest(status = StaffStatus.ALL)
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, NomisStaffMembers(fromStaffIds(staffIds)), request)
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, nomisStaffRoles(staffIds))
    } else {
      staffIds.forEach {
        givenStaffRole(staffRole(prisonCode, it))
      }
    }

    val staffConfigs: List<StaffConfiguration> =
      staffIds.mapIndexedNotNull { index, staffId ->
        if (index % 5 == 0) {
          null
        } else {
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6))
        }
      }

    staffConfigs.mapIndexed { index, kw ->
      (0..index).map {
        givenAllocation(
          staffAllocation(
            personIdentifier(),
            prisonCode,
            kw.staffId,
          ),
        )
      }
    }

    val response =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody<StaffSearchResponse>()
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(11)
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can find allocation counts when no staff config`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "NSR"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffId = newId()
    val request = searchRequest()
    nomisUserRolesMockServer.stubGetUserStaff(
      prisonCode,
      NomisStaffMembers(fromStaffIds(listOf(staffId))),
      request,
    )
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(
        prisonCode,
        listOf(
          nomisStaffRole(
            staffId,
            position = "PRO",
            scheduleType = "FT",
            firstName = { "No" },
            lastName = { "Config" },
            hoursPerWeek = BigDecimal.valueOf(34.5),
            fromDate = LocalDate.now().minusDays(7),
          ),
        ),
      )
    } else {
      givenStaffRole(staffRole(prisonCode, staffId, hoursPerWeek = BigDecimal(34.5)))
    }

    val allocations =
      listOf(
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId, LocalDateTime.now().minusWeeks(1))),
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId, LocalDateTime.now().minusWeeks(2))),
      )

    if (policy == AllocationPolicy.KEY_WORKER) {
      allocations.mapIndexed { idx, allocation ->
        (1..(idx + 1) * 2).map {
          givenRecordedEvent(
            recordedEvent(
              prisonCode,
              RecordedEventType.SESSION,
              allocation.allocatedAt.plusDays(idx + 2L),
              personIdentifier = allocation.personIdentifier,
              staffId = allocation.staffId,
            ),
          )
        }
      }
    }

    allocations.mapIndexed { idx, allocation ->
      (1..idx + 1).map {
        givenRecordedEvent(
          recordedEvent(
            prisonCode,
            RecordedEventType.ENTRY,
            allocation.allocatedAt.plusDays(idx + 3L),
            personIdentifier = allocation.personIdentifier,
            staffId = allocation.staffId,
          ),
        )
      }
    }

    val response =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody<StaffSearchResponse>()
        .returnResult()
        .responseBody!!

    val staff = response.content.single()
    assertThat(staff.capacity).isEqualTo(9)
    assertThat(staff.allocated).isEqualTo(2)
    assertThat(staff.numberOfSessions).isEqualTo(if (policy == AllocationPolicy.KEY_WORKER) 6 else 0)
    assertThat(staff.numberOfEntries).isEqualTo(3)
    assertThat(staff.staffRole).isEqualTo(
      StaffRoleInfo(
        CodedDescription("PRO", "Prison Officer"),
        CodedDescription("FT", "Full Time"),
        BigDecimal(34.5),
        LocalDate.now().minusDays(7),
        null,
      ),
    )
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can filter for has staff role`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "HKR"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffIds = (0..5).map { newId() }
    val request = searchRequest(hasPolicyStaffRole = true)
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, NomisStaffMembers(fromStaffIds(staffIds)), request)
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, nomisStaffRoles(staffIds.filter { it % 2 == 0L }))
    } else {
      staffIds.filter { it % 2 == 0L }.forEach {
        givenStaffRole(staffRole(prisonCode, it))
      }
    }

    val staffConfigs: List<StaffConfiguration> =
      staffIds.mapIndexedNotNull { index, staffId ->
        if (index % 5 == 0) {
          null
        } else {
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6))
        }
      }

    staffConfigs.flatMapIndexed { index, kw ->
      (0..index).map {
        givenAllocation(
          staffAllocation(
            personIdentifier(),
            prisonCode,
            kw.staffId,
          ),
        )
      }
    }

    val r1 =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody<StaffSearchResponse>()
        .returnResult()
        .responseBody!!

    assertThat(r1.content.all { staff -> staff.staffRole != null }).isTrue

    val r2 =
      searchStaffSpec(prisonCode, request.copy(hasPolicyStaffRole = false), policy)
        .expectStatus()
        .isOk
        .expectBody<StaffSearchResponse>()
        .returnResult()
        .responseBody!!

    assertThat(r2.content.all { staff -> staff.staffRole == null }).isTrue
  }

  private fun searchRequest(
    query: String? = null,
    status: StaffStatus = ACTIVE,
    hasPolicyStaffRole: Boolean? = null,
  ) = StaffSearchRequest(query, status, hasPolicyStaffRole)

  private fun searchStaffSpec(
    prisonCode: String,
    request: StaffSearchRequest,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/staff"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
