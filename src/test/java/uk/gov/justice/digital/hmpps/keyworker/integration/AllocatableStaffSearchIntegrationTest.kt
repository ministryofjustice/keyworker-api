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
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.personalOfficerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
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
    searchStaffSpec("DNM", searchRequest(), policy = AllocationPolicy.PERSONAL_OFFICER, "ROLE_ANY__OTHER_RW")
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
            val personIdentifier = personIdentifier()
            givenAllocation(
              staffAllocation(
                personIdentifier,
                prisonCode,
                kw.staffId,
                allocatedAt = LocalDateTime.now().minusMonths(1),
                allocationType = if (index == 7 && it == 7) AllocationType.PROVISIONAL else AllocationType.AUTO,
              ),
            )
          }.apply {
            kw.mockCaseNotesResponse(
              policy,
              prisonCode,
              ReportingPeriod.currentMonth(),
              this
                .filter {
                  it.allocationType !=
                    AllocationType.PROVISIONAL
                }.map { it.personIdentifier }
                .toSet(),
              index,
            )
          }
      }.flatten()

    val response =
      searchStaffSpec(prisonCode, request, policy)
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
        .stats.recordedEvents
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
        .stats.recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(0)

    assertThat(response.content.find { it.staffId == staffIds[3] }).isNull()

    assertThat(response.content[4].staffId).isEqualTo(staffIds[5])
    assertThat(response.content[4].allowAutoAllocation).isEqualTo(false)
    assertThat(response.content[4].allocated).isEqualTo(0)
    assertThat(
      response.content[4]
        .stats.recordedEvents
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
      response.content[4]
        .stats.recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(0)

    assertThat(response.content[6].staffId).isEqualTo(staffIds[8])
    assertThat(response.content[6].allowAutoAllocation).isEqualTo(true)
    assertThat(response.content[6].allocated).isEqualTo(7)
    assertThat(
      response.content[6]
        .stats.recordedEvents
        .find { it.type == RecordedEventType.SESSION }
        ?.count,
    ).isEqualTo(
      if (policy ==
        AllocationPolicy.KEY_WORKER
      ) {
        8
      } else {
        null
      },
    )
    assertThat(
      response.content[6]
        .stats.recordedEvents
        .find { it.type == RecordedEventType.ENTRY }
        ?.count,
    ).isEqualTo(3)
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
          }.apply { kw.mockCaseNotesResponse(policy, prisonCode, currentMonth, this.toSet(), index) }
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

    caseNotesMockServer.stubUsageByPersonIdentifier(
      if (policy == AllocationPolicy.KEY_WORKER) {
        keyworkerTypes(prisonCode, setOf(personIdentifier), reportingPeriod.from, reportingPeriod.to, setOf(staffId.toString()))
      } else {
        personalOfficerTypes(
          prisonCode,
          setOf(personIdentifier),
          reportingPeriod.from,
          reportingPeriod.to,
          setOf(staffId.toString()),
        )
      },
      NoteUsageResponse(
        (
          if (policy == AllocationPolicy.KEY_WORKER) {
            listOf(
              UsageByPersonIdentifierResponse(
                personIdentifier,
                KW_TYPE,
                KW_SESSION_SUBTYPE,
                7,
              ),
              UsageByPersonIdentifierResponse(
                personIdentifier,
                policy.entryConfig.type,
                policy.entryConfig.subType,
                3,
              ),
            )
          } else {
            listOf(
              UsageByPersonIdentifierResponse(
                personIdentifier,
                policy.entryConfig.type,
                policy.entryConfig.subType,
                3,
              ),
            )
          }
        ).groupBy { it.personIdentifier },
      ),
    )

    val response =
      searchStaffSpec(prisonCode, request, policy)
        .expectStatus()
        .isOk
        .expectBody(AllocatableSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    val staff = response.content.single()
    assertThat(staff.capacity).isEqualTo(6)
    assertThat(staff.allocated).isEqualTo(1)
    assertThat(
      staff.stats.recordedEvents
        .find { it.type == RecordedEventType.SESSION }
        ?.count,
    ).isEqualTo(
      if (policy ==
        AllocationPolicy.KEY_WORKER
      ) {
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
          }.apply { s.mockCaseNotesResponse(policy, prisonCode, ReportingPeriod.currentMonth(), this.toSet(), index) }
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
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  private fun StaffConfiguration.mockCaseNotesResponse(
    policy: AllocationPolicy,
    prisonCode: String,
    reportingPeriod: ReportingPeriod,
    personIdentifiers: Set<String>,
    index: Int,
  ) = caseNotesMockServer.stubUsageByPersonIdentifier(
    if (policy == AllocationPolicy.KEY_WORKER) {
      keyworkerTypes(prisonCode, personIdentifiers, reportingPeriod.from, reportingPeriod.to, setOf(staffId.toString()))
    } else {
      personalOfficerTypes(
        prisonCode,
        personIdentifiers,
        reportingPeriod.from,
        reportingPeriod.to,
        setOf(staffId.toString()),
      )
    },
    NoteUsageResponse(
      (
        if (policy == AllocationPolicy.KEY_WORKER) {
          listOf(
            UsageByPersonIdentifierResponse(
              personIdentifiers.first(),
              KW_TYPE,
              KW_SESSION_SUBTYPE,
              index,
            ),
            UsageByPersonIdentifierResponse(
              personIdentifiers.first(),
              policy.entryConfig.type,
              policy.entryConfig.subType,
              index / 2,
            ),
          )
        } else {
          listOf(
            UsageByPersonIdentifierResponse(
              personIdentifiers.first(),
              policy.entryConfig.type,
              policy.entryConfig.subType,
              index / 2,
            ),
          )
        }
      ).groupBy { it.personIdentifier },
    ),
  )

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/staff-allocations"

    @JvmStatic
    fun policyProvider() =
      listOf(
        //   Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
