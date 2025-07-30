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
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.dto.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffRoles
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class AllocatableStaffSearchIntegrationTest : IntegrationTest() {
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
    searchStaffSpec("DNM", searchRequest(), policy = AllocationPolicy.PERSONAL_OFFICER, false, "ROLE_ANY__OTHER_RW")
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can filter staff and decorate with config and counts`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "ASF"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffIds = (0..10).map { newId() }
    val request = searchRequest(query = "First")
    prisonMockServer.stubStaffSummaries(staffIds.map { StaffSummary(it, "First $it", "Last $it") })
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(staffIds))
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
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6, true))
        }
      }

    staffConfigs
      .mapIndexed { index, kw ->
        (0..index)
          .map {
            givenAllocation(
              staffAllocation(
                personIdentifier(),
                prisonCode,
                kw.staffId,
                allocatedAt = LocalDateTime.now().minusMonths(1),
                allocationType = if (index == 7 && it == 7) AllocationType.PROVISIONAL else AllocationType.AUTO,
              ),
            )
          }.apply {
            kw.generateCaseNotes(
              policy,
              prisonCode,
              ReportingPeriod.currentMonth(),
              filter {
                it.allocationType != AllocationType.PROVISIONAL
              }.map { it.personIdentifier }
                .toSet(),
              index,
            )
          }
      }.flatten()

    val response =
      searchStaffSpec(prisonCode, request, policy, true)
        .expectStatus()
        .isOk
        .expectBody(AllocatableSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content.map { it.status.code }.toSet()).containsOnly(ACTIVE.name)

    assertThat(response.content[0].staffId).isEqualTo(staffIds[0])
    assertThat(response.content[0].allowAutoAllocation).isEqualTo(false)
    assertThat(response.content[0].allocated).isEqualTo(0)
    assertThat(
      response.content[0]
        .stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.SESSION }
        ?.count,
    ).isEqualTo(
      if (policy ==
        AllocationPolicy.KEY_WORKER
      ) {
        0
      } else {
        null
      },
    )
    assertThat(
      response.content[0]
        .stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(0)

    assertThat(response.content.find { it.staffId == staffIds[3] }).isNull()

    assertThat(response.content[5].staffId).isEqualTo(staffIds[7])
    assertThat(response.content[5].allowAutoAllocation).isEqualTo(true)
    assertThat(response.content[5].allocated).isEqualTo(6)
    assertThat(
      response.content[5]
        .stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.SESSION }
        ?.count,
    ).isEqualTo(
      if (policy ==
        AllocationPolicy.KEY_WORKER
      ) {
        5
      } else {
        null
      },
    )
    assertThat(
      response.content[5]
        .stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(2)

    assertThat(response.content[6].staffId).isEqualTo(staffIds[8])
    assertThat(response.content[6].allowAutoAllocation).isEqualTo(true)
    assertThat(response.content[6].allocated).isEqualTo(7)
    assertThat(
      response.content[6]
        .stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.SESSION }
        ?.count,
    ).isEqualTo(
      if (policy ==
        AllocationPolicy.KEY_WORKER
      ) {
        6
      } else {
        null
      },
    )
    assertThat(
      response.content[6]
        .stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(3)

    val withoutStats =
      searchStaffSpec(prisonCode, request, policy, false)
        .expectStatus()
        .isOk
        .expectBody(AllocatableSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(withoutStats.content.mapNotNull { it.stats }).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can find all staff with config and counts`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val currentMonth = ReportingPeriod.currentMonth()

    val prisonCode = "ATA"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffIds = (0..10).map { newId() }
    val request = searchRequest(status = StaffStatus.ALL)
    prisonMockServer.stubStaffSummaries(staffIds.map { StaffSummary(it, "First $it", "Last $it") })
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(staffIds))
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
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6, true))
        }
      }

    staffConfigs
      .mapIndexed { index, kw ->
        (0..index)
          .map {
            val personIdentifier = personIdentifier()
            givenAllocation(
              staffAllocation(
                personIdentifier,
                prisonCode,
                kw.staffId,
                allocatedAt = LocalDateTime.now().minusMonths(1),
                allocationType = AllocationType.AUTO,
              ),
            )
            personIdentifier
          }.apply { kw.generateCaseNotes(policy, prisonCode, currentMonth, this.toSet(), index) }
      }.flatten()

    val response =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody(AllocatableSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(11)
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can find allocation counts when no staff config`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "ASR"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffId = newId()
    val staffConfig = givenStaffConfig(staffConfig(ACTIVE, staffId))
    val request = searchRequest()
    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(staffId, "First", "Last")))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(
        prisonCode,
        listOf(
          StaffLocationRoleDto
            .builder()
            .staffId(staffId)
            .firstName("No")
            .lastName("Config")
            .position("PRO")
            .scheduleType("FT")
            .hoursPerWeek(BigDecimal(34.5))
            .fromDate(LocalDate.now().minusDays(7))
            .build(),
        ),
      )
    } else {
      givenStaffRole(
        staffRole(
          prisonCode,
          staffId,
          withReferenceData(ReferenceDataDomain.STAFF_POSITION, "PRO"),
          withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "FT"),
          BigDecimal(34.5),
        ),
      )
    }

    val personIdentifier = personIdentifier()
    givenAllocation(staffAllocation(personIdentifier, prisonCode, staffId))

    val reportingPeriod = ReportingPeriod.currentMonth()
    staffConfig.generateCaseNotes(policy, prisonCode, reportingPeriod, setOf(personIdentifier), 7)

    val response =
      searchStaffSpec(prisonCode, request, policy, true)
        .expectStatus()
        .isOk
        .expectBody(AllocatableSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    val staff = response.content.single()
    assertThat(staff.capacity).isEqualTo(6)
    assertThat(staff.allocated).isEqualTo(1)
    assertThat(
      staff.stats!!
        .recordedEvents
        .find { it.type == RecordedEventType.SESSION }
        ?.count,
    ).isEqualTo(
      if (policy == AllocationPolicy.KEY_WORKER) {
        7
      } else {
        null
      },
    )
    assertThat(
      staff.stats.recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(3)
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
  fun `can filter by name`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "ASN"
    givenPrisonConfig(prisonConfig(prisonCode, policy = policy))

    val staffIds = (0..5).map { newId() }
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(staffIds))
    } else {
      staffIds.forEach {
        givenStaffRole(staffRole(prisonCode, it))
      }
    }
    val si = staffIds.mapIndexed { i, si -> si to i }.toMap()
    val forename = { id: Long -> if (si[id]!! % 2 == 0) "John" else "Jane" }
    val surname = { id: Long -> if (si[id]!! % 4 == 0) "Smith" else "Doe" }
    prisonMockServer.stubStaffSummaries(staffIds.map { StaffSummary(it, forename(it), surname(it)) })

    val staffConfigs: List<StaffConfiguration> =
      staffIds.mapIndexedNotNull { index, staffId ->
        if (index % 5 == 0) {
          null
        } else {
          givenStaffConfig(staffConfig(if (index % 3 == 0) INACTIVE else ACTIVE, staffId, 6, true))
        }
      }

    staffConfigs
      .mapIndexed { index, s ->
        (0..index)
          .map {
            val personIdentifier = personIdentifier()
            givenAllocation(
              staffAllocation(
                personIdentifier,
                prisonCode,
                s.staffId,
                allocatedAt = LocalDateTime.now().minusMonths(1),
                allocationType = AllocationType.MANUAL,
              ),
            )
            personIdentifier
          }.apply { s.generateCaseNotes(policy, prisonCode, ReportingPeriod.currentMonth(), this.toSet(), index) }
      }.flatten()

    val request = searchRequest("John Smith")

    val res =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody(AllocatableSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(res.content).hasSize(2)
  }

  private fun searchRequest(
    query: String? = null,
    status: StaffStatus = StaffStatus.ACTIVE,
  ) = AllocatableSearchRequest(query, status)

  private fun searchStaffSpec(
    prisonCode: String,
    request: AllocatableSearchRequest,
    policy: AllocationPolicy,
    includeStats: Boolean = false,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri {
      it.path(SEARCH_URL)
      it.queryParam("includeStats", includeStats)
      it.build(prisonCode)
    }.bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  private fun StaffConfiguration.generateCaseNotes(
    policy: AllocationPolicy,
    prisonCode: String,
    reportingPeriod: ReportingPeriod,
    personIdentifiers: Set<String>,
    index: Int,
  ) {
    val currentDateRange = dateRange(reportingPeriod.from.toLocalDate(), reportingPeriod.to.toLocalDate())
    val (entryType, entrySubtype) =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> KW_TYPE to KW_ENTRY_SUBTYPE
        AllocationPolicy.PERSONAL_OFFICER -> PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE
      }
    (1..index / 2).map {
      givenAllocationCaseNote(
        caseNote(
          prisonCode,
          entryType,
          entrySubtype,
          currentDateRange.random().atStartOfDay(),
          personIdentifier = personIdentifiers.random(),
          staffId = staffId,
        ),
      )
    }

    if (policy == AllocationPolicy.KEY_WORKER) {
      (1..index).map {
        givenAllocationCaseNote(
          caseNote(
            prisonCode,
            KW_TYPE,
            KW_SESSION_SUBTYPE,
            currentDateRange.random().atStartOfDay(),
            personIdentifier = personIdentifiers.random(),
            staffId = staffId,
          ),
        )
      }
    }
  }

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/staff-allocations"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
