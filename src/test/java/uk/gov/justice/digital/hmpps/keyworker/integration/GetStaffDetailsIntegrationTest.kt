package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventCount
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType.ENTRY
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType.SESSION
import uk.gov.justice.digital.hmpps.keyworker.dto.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.math.BigDecimal
import java.math.RoundingMode.HALF_EVEN
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS

class GetStaffDetailsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_STAFF_DETAILS, "NEP", newId())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getStaffDetailSpec("DNM", newId(), AllocationPolicy.KEY_WORKER, role = "ROLE_ANY__OTHER_RW")
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `200 ok and staff details returned`(policy: AllocationPolicy) {
    val prisonCode = "DEF"

    // adding config with a different policy to confirm correct config is returned
    setContext(
      AllocationContext.get().copy(
        policy =
          when (policy) {
            AllocationPolicy.PERSONAL_OFFICER -> AllocationPolicy.KEY_WORKER
            AllocationPolicy.KEY_WORKER -> AllocationPolicy.PERSONAL_OFFICER
          },
      ),
    )
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 5, allowAutoAllocation = true))

    setContext(AllocationContext.get().copy(policy = policy))
    val staffConfig =
      givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10, allowAutoAllocation = false))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerDetails(
        prisonCode,
        staffConfig.staffId,
        staffDetail(staffConfig.staffId, "FT", "PRO"),
      )
    } else {
      prisonMockServer.stubStaffSummaries(listOf(staffSummary(id = staffConfig.staffId)))
      givenStaffRole(
        staffRole(
          prisonCode,
          staffConfig.staffId,
          withReferenceData(ReferenceDataDomain.STAFF_POSITION, "PRO"),
          withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "FT"),
          BigDecimal(36.5),
          now().minusWeeks(6),
        ),
      )
    }

    val currentMonth = ReportingPeriod.currentMonth()
    val allocations =
      (1..20).map {
        val activeAllocation = it % 9 != 0
        givenAllocation(
          staffAllocation(
            personIdentifier = personIdentifier(),
            prisonCode = prisonCode,
            staffId = staffConfig.staffId,
            allocatedAt = LocalDateTime.now().minusDays(31L),
            active = activeAllocation,
            deallocatedAt = if (activeAllocation) null else LocalDateTime.now().minusDays(10),
            deallocatedBy = if (activeAllocation) null else "T357",
            deallocationReason = if (activeAllocation) null else DeallocationReason.TRANSFER,
          ),
        )
      }

    val personIdentifiers = allocations.filter { it.isActive }.map { it.personIdentifier }.toSet()
    val caseNoteIdentifiers =
      allocations
        .filter {
          (it.deallocatedAt == null || !it.deallocatedAt!!.isBefore(currentMonth.from)) &&
            it.allocatedAt.isBefore(currentMonth.to)
        }.map { it.personIdentifier }
        .toSet()
    val prisoners =
      personIdentifiers.mapIndexed { index, identifier ->
        Prisoner(
          identifier,
          "First$index",
          "Last$index",
          now().minusDays(30),
          now().plusDays(90),
          prisonCode,
          "Description of $prisonCode",
          "$prisonCode-A-1",
          "STANDARD",
          null,
          null,
          listOf(
            PrisonAlert("Type", "XRF", true, false),
            PrisonAlert("Type", "RNO121", false, true),
            PrisonAlert("Type", "OTHER1", true, false),
            PrisonAlert("Type", "OTHER2", true, false),
          ),
        )
      }
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, personIdentifiers, prisoners)

    val currentDateRange = dateRange(currentMonth.from.toLocalDate(), currentMonth.to.toLocalDate())

    val (entryType, entrySubtype) =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> KW_TYPE to KW_ENTRY_SUBTYPE
        AllocationPolicy.PERSONAL_OFFICER -> PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE
      }
    (1..15).map {
      givenAllocationCaseNote(
        caseNote(
          prisonCode,
          entryType,
          entrySubtype,
          currentDateRange.random().atStartOfDay(),
          personIdentifier = caseNoteIdentifiers.random(),
          staffId = staffConfig.staffId,
        ),
      )
    }

    if (policy == AllocationPolicy.KEY_WORKER) {
      (1..38).map {
        givenAllocationCaseNote(
          caseNote(
            prisonCode,
            KW_TYPE,
            KW_SESSION_SUBTYPE,
            currentDateRange.random().atStartOfDay(),
            personIdentifier = caseNoteIdentifiers.random(),
            staffId = staffConfig.staffId,
          ),
        )
      }
    }

    val response =
      getStaffDetailSpec(prisonCode, staffConfig.staffId, policy, true)
        .expectStatus()
        .isOk
        .expectBody(StaffDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.staffRole).isEqualTo(
      StaffRoleInfo(
        CodedDescription("PRO", "Prison Officer"),
        CodedDescription("FT", "Full Time"),
        BigDecimal(36.5),
        now().minusWeeks(6),
        null,
      ),
    )
    assertThat(response.status).isEqualTo(CodedDescription("ACTIVE", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription(prisonCode, "Description of $prisonCode"))
    assertThat(response.capacity).isEqualTo(10)
    assertThat(response.allocated).isEqualTo(18)
    assertThat(response.allocations.size).isEqualTo(18)
    assertThat(
      response.allocations
        .first()
        .prisoner.relevantAlertCodes,
    ).containsExactlyInAnyOrder("XRF")
    assertThat(
      response.allocations
        .first()
        .prisoner.remainingAlertCount,
    ).isEqualTo(2)
    assertThat(
      response.allocations
        .first()
        .stats.projectedComplianceEvents,
    ).isEqualTo(4)
    assertThat(response.allowAutoAllocation).isFalse
    assertThat(response.allocations.all { it.prisoner.cellLocation == "$prisonCode-A-1" }).isTrue
    assertThat(response.stats?.current).isNotNull()
    with(response.stats!!.current) {
      assertThat(projectedComplianceEvents).isEqualTo(allocations.sumOf { (if (it.isActive) 4 else 3) })
      assertThat(recordedComplianceEvents).isEqualTo(if (policy == AllocationPolicy.KEY_WORKER) 38 else 15)
      assertThat(recordedEvents).isEqualTo(
        if (policy == AllocationPolicy.KEY_WORKER) {
          listOf(
            RecordedEventCount(SESSION, 38),
            RecordedEventCount(ENTRY, 15),
          )
        } else {
          listOf(
            RecordedEventCount(ENTRY, 15),
          )
        },
      )
      assertThat(complianceRate).isEqualTo(
        BigDecimal(recordedComplianceEvents / projectedComplianceEvents.toDouble() * 100)
          .setScale(2, HALF_EVEN)
          .toDouble(),
      )
    }

    assertThat(response.stats.current.from).isEqualTo(
      response.stats.previous.to
        .plusDays(1),
    )
    assertThat(
      DAYS.between(response.stats.current.from, response.stats.current.to),
    ).isEqualTo(DAYS.between(response.stats.previous.from, response.stats.previous.to))

    assertThat(response.stats.previous).isNotNull()
    with(response.stats.previous) {
      val projectedSessions = DAYS.between(from, to) * 2 + 2
      assertThat(projectedSessions).isEqualTo(projectedSessions)
      assertThat(recordedComplianceEvents).isEqualTo(0)
      assertThat(recordedEvents).isEqualTo(
        if (policy == AllocationPolicy.KEY_WORKER) {
          listOf(
            RecordedEventCount(SESSION, 0),
            RecordedEventCount(ENTRY, 0),
          )
        } else {
          listOf(
            RecordedEventCount(ENTRY, 0),
          )
        },
      )
      assertThat(complianceRate).isEqualTo(0.0)
    }

    val withoutStats =
      getStaffDetailSpec(prisonCode, staffConfig.staffId, policy, false)
        .expectStatus()
        .isOk
        .expectBody(StaffDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(withoutStats.stats).isNull()
    assertThat(withoutStats.allocations).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `200 ok and staff details returned when no allocations exist and no config`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prison = Prison("NOAL", "No Allocations")
    val (prisonCode, prisonDescription) = prison
    val staff = staffDetail(newId(), "PT", "CHAP", "Noah", "Locations")
    prisonRegisterMockServer.stubGetPrisons(setOf(prison))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerDetails(prisonCode, staff.staffId, staff)
    } else {
      prisonMockServer.stubStaffSummaries(listOf(staffSummary("Noah", "Locations", staff.staffId)))
      givenStaffRole(
        staffRole(
          prisonCode,
          staff.staffId,
          withReferenceData(ReferenceDataDomain.STAFF_POSITION, "CHAP"),
          withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "PT"),
          BigDecimal(36.5),
          now().minusWeeks(6),
        ),
      )
    }

    val response =
      getStaffDetailSpec(prisonCode, staff.staffId, policy)
        .expectStatus()
        .isOk
        .expectBody(StaffDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.staffRole)
      .isEqualTo(
        StaffRoleInfo(
          CodedDescription("CHAP", "Chaplain"),
          CodedDescription("PT", "Part Time"),
          BigDecimal(36.5),
          now().minusWeeks(6),
          null,
        ),
      )
    assertThat(response.status).isEqualTo(CodedDescription("ACTIVE", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription(prisonCode, prisonDescription))
    assertThat(response.capacity).isEqualTo(6)
    assertThat(response.allocated).isEqualTo(0)
    assertThat(response.allocations.size).isEqualTo(0)
    assertThat(response.allowAutoAllocation).isTrue
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff details are returned including a reactivate date`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prison = Prison("UAL", "Unavailable for Annual Leave)")
    prisonRegisterMockServer.stubGetPrisons(setOf(prison))
    val prisonCode = prison.prisonId
    val staff = staffDetail(newId(), "FT", "AO", "On", "Holiday")
    val staffConfig =
      givenStaffConfig(
        staffConfig(
          StaffStatus.UNAVAILABLE_ANNUAL_LEAVE,
          staff.staffId,
          capacity = 10,
          allowAutoAllocation = false,
          reactivateOn = now().plusDays(7),
        ),
      )
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerDetails(prisonCode, staff.staffId, staff)
    } else {
      prisonMockServer.stubStaffSummaries(listOf(staffSummary("On", "Holiday", staffConfig.staffId)))
      givenStaffRole(
        staffRole(
          prisonCode,
          staffConfig.staffId,
          withReferenceData(ReferenceDataDomain.STAFF_POSITION, "AO"),
          withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "FT"),
          BigDecimal(36.5),
          now().minusWeeks(6),
        ),
      )
    }

    val response =
      getStaffDetailSpec(prisonCode, staff.staffId, policy)
        .expectStatus()
        .isOk
        .expectBody(StaffDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.staffRole)
      .isEqualTo(
        StaffRoleInfo(
          CodedDescription("AO", "Admin Officer"),
          CodedDescription("FT", "Full Time"),
          BigDecimal(36.5),
          now().minusWeeks(6),
          null,
        ),
      )
    assertThat(response.status).isEqualTo(
      CodedDescription(
        "UNAVAILABLE_ANNUAL_LEAVE",
        "Unavailable - annual leave",
      ),
    )
    assertThat(response.reactivateOn).isEqualTo(staffConfig.reactivateOn)
  }

  private fun getStaffDetailSpec(
    prisonCode: String,
    staffId: Long,
    policy: AllocationPolicy,
    includeStats: Boolean = false,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_STAFF_DETAILS)
      it.queryParam("includeStats", includeStats)
      it.build(prisonCode, staffId)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .header(CaseloadIdHeader.NAME, prisonCode)
    .exchange()

  private fun caseNoteResponse(
    personIdentifiers: Set<String>,
    policy: AllocationPolicy,
  ): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    when (policy) {
      AllocationPolicy.KEY_WORKER ->
        NoteUsageResponse(
          personIdentifiers
            .mapIndexed { index, pi ->
              buildSet {
                if (index % 4 != 0) {
                  add(
                    UsageByPersonIdentifierResponse(
                      pi,
                      KW_TYPE,
                      KW_SESSION_SUBTYPE,
                      (index % 4) + 1,
                      LatestNote(LocalDateTime.now().minusDays(index * 2L)),
                    ),
                  )
                }
                if (index % 3 == 0) {
                  add(
                    UsageByPersonIdentifierResponse(
                      pi,
                      KW_TYPE,
                      KW_ENTRY_SUBTYPE,
                      (index % 2) + 2,
                      LatestNote(LocalDateTime.now().minusDays(index + 1L)),
                    ),
                  )
                }
              }
            }.flatten()
            .groupBy { it.personIdentifier },
        )

      AllocationPolicy.PERSONAL_OFFICER ->
        NoteUsageResponse(
          personIdentifiers
            .mapIndexed { index, pi ->
              buildSet {
                if (index % 3 == 0) {
                  add(
                    UsageByPersonIdentifierResponse(
                      pi,
                      PO_ENTRY_TYPE,
                      PO_ENTRY_SUBTYPE,
                      (index % 2) + 2,
                      LatestNote(LocalDateTime.now().minusDays(index + 1L)),
                    ),
                  )
                }
              }
            }.flatten()
            .groupBy { it.personIdentifier },
        )
    }

  fun staffSummary(
    firstName: String = "First",
    lastName: String = "Last",
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val GET_STAFF_DETAILS = "/prisons/{prisonCode}/staff/{staffId}"

    fun staffDetail(
      id: Long,
      scheduleType: String,
      position: String,
      firstName: String = "First",
      lastName: String = "Last",
      hoursPerWeek: BigDecimal = BigDecimal(36.5),
    ): StaffLocationRoleDto =
      StaffLocationRoleDto
        .builder()
        .staffId(id)
        .scheduleType(scheduleType)
        .position(position)
        .hoursPerWeek(hoursPerWeek)
        .firstName(firstName)
        .lastName(lastName)
        .fromDate(now().minusWeeks(6))
        .build()

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
