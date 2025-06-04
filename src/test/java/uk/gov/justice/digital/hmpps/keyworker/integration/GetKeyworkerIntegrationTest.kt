package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.Agency
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerWithSchedule
import uk.gov.justice.digital.hmpps.keyworker.dto.ScheduleType
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.math.BigDecimal
import java.math.RoundingMode.HALF_EVEN
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS

class GetKeyworkerIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_KEYWORKER_DETAILS, "NEP", newId())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getKeyworkerSpec("DNM", newId(), role = "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok and keyworker details returned`() {
    val prisonCode = "DEF"
    val keyworker =
      givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10, allowAutoAllocation = false))
    prisonMockServer.stubKeyworkerDetails(
      prisonCode,
      staffDetail(keyworker.staffId, ScheduleType.FULL_TIME),
    )

    val fromDate = now().minusMonths(1)
    val previousFromDate = fromDate.minusMonths(1)
    val allocations =
      (1..20).map {
        givenKeyworkerAllocation(
          keyworkerAllocation(
            personIdentifier = personIdentifier(),
            prisonCode = prisonCode,
            staffId = keyworker.staffId,
            assignedAt = LocalDateTime.now().minusDays(it * 3L),
            active = it % 9 != 0,
            expiryDateTime = if (it % 9 == 0) LocalDateTime.now().minusDays(10) else null,
          ),
        )
      }

    val personIdentifiers = allocations.filter { it.active }.map { it.personIdentifier }.toSet()
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, personIdentifiers)
    caseNotesMockServer.stubUsageByPersonIdentifier(
      keyworkerTypes(
        prisonCode,
        allocations
          .filter {
            (it.expiryDateTime == null || !it.expiryDateTime!!.toLocalDate().isBefore(fromDate)) &&
              it.assignedAt.toLocalDate().isBefore(now())
          }.map { it.personIdentifier }
          .toSet(),
        fromDate,
        now(),
        setOf(keyworker.staffId.toString()),
      ),
      caseNoteResponse(personIdentifiers),
    )
    caseNotesMockServer.stubUsageByPersonIdentifier(
      keyworkerTypes(
        prisonCode,
        allocations
          .filter {
            (it.expiryDateTime == null || !it.expiryDateTime!!.toLocalDate().isBefore(previousFromDate)) &&
              it.assignedAt.toLocalDate().isBefore(fromDate)
          }.map { it.personIdentifier }
          .toSet(),
        previousFromDate,
        fromDate,
        setOf(keyworker.staffId.toString()),
      ),
      NoteUsageResponse(emptyMap()),
    )

    val response =
      getKeyworkerSpec(prisonCode, keyworker.staffId)
        .expectStatus()
        .isOk
        .expectBody(KeyworkerDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.keyworker)
      .isEqualTo(KeyworkerWithSchedule(keyworker.staffId, "First", "Last", CodedDescription("FT", "Full Time")))
    assertThat(response.status).isEqualTo(CodedDescription("ACTIVE", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription(prisonCode, "Description of $prisonCode"))
    assertThat(response.capacity).isEqualTo(10)
    assertThat(response.allocated).isEqualTo(18)
    assertThat(response.allocations.size).isEqualTo(18)
    assertThat(response.allowAutoAllocation).isFalse
    assertThat(response.allocations.all { it.prisoner.cellLocation == "$prisonCode-A-1" }).isTrue

    assertThat(response.stats.current).isNotNull()
    with(response.stats.current) {
      val projectedSessions = DAYS.between(from, to) * 2 + 2
      assertThat(projectedSessions).isEqualTo(projectedSessions)
      assertThat(recordedSessions).isEqualTo(38)
      assertThat(recordedEntries).isEqualTo(15)
      assertThat(complianceRate).isEqualTo(
        BigDecimal(recordedSessions / projectedSessions.toDouble() * 100)
          .setScale(2, HALF_EVEN)
          .toDouble(),
      )
    }

    assertThat(response.stats.previous).isNotNull()
    with(response.stats.previous) {
      val projectedSessions = DAYS.between(from, to) * 2 + 2
      assertThat(projectedSessions).isEqualTo(projectedSessions)
      assertThat(recordedSessions).isEqualTo(0)
      assertThat(recordedEntries).isEqualTo(0)
      assertThat(complianceRate).isEqualTo(0.0)
    }
  }

  @Test
  fun `200 ok and keyworker details returned when no allocations exist and no config`() {
    val agency = Agency("NOAL", "No Allocations")
    val (prisonCode, prisonDescription) = agency
    val staff = staffDetail(newId(), ScheduleType.PART_TIME, "Noah", "Locations")
    prisonMockServer.stubKeyworkerDetails(prisonCode, staff)
    prisonMockServer.stubGetAgency(prisonCode, agency)

    val response =
      getKeyworkerSpec(prisonCode, staff.staffId)
        .expectStatus()
        .isOk
        .expectBody(KeyworkerDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.keyworker)
      .isEqualTo(
        KeyworkerWithSchedule(
          staff.staffId,
          staff.firstName,
          staff.lastName,
          CodedDescription("PT", "Part Time"),
        ),
      )
    assertThat(response.status).isEqualTo(CodedDescription("ACTIVE", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription(prisonCode, prisonDescription))
    assertThat(response.capacity).isEqualTo(6)
    assertThat(response.allocated).isEqualTo(0)
    assertThat(response.allocations.size).isEqualTo(0)
    assertThat(response.allowAutoAllocation).isTrue
  }

  @Test
  fun `keyworker details are returned including a reactivate date`() {
    val agency = Agency("UAL", "Unavailable for Annual Leave)")
    val prisonCode = agency.agencyId
    val staff = staffDetail(newId(), ScheduleType.FULL_TIME, "On", "Holiday")
    val keyworker =
      givenStaffConfig(
        staffConfig(
          StaffStatus.UNAVAILABLE_ANNUAL_LEAVE,
          staff.staffId,
          capacity = 10,
          allowAutoAllocation = false,
          reactivateOn = now().plusDays(7),
        ),
      )
    prisonMockServer.stubKeyworkerDetails(prisonCode, staff)
    prisonMockServer.stubGetAgency(prisonCode, agency)

    val response =
      getKeyworkerSpec(prisonCode, staff.staffId)
        .expectStatus()
        .isOk
        .expectBody(KeyworkerDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.keyworker)
      .isEqualTo(
        KeyworkerWithSchedule(
          staff.staffId,
          staff.firstName,
          staff.lastName,
          CodedDescription("FT", "Full Time"),
        ),
      )
    assertThat(response.status).isEqualTo(CodedDescription("UNAVAILABLE_ANNUAL_LEAVE", "Unavailable - annual leave"))
    assertThat(response.reactivateOn).isEqualTo(keyworker.reactivateOn)
  }

  private fun getKeyworkerSpec(
    prisonCode: String,
    staffId: Long,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_KEYWORKER_DETAILS)
      it.build(prisonCode, staffId)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  private fun caseNoteResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> =
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

  companion object {
    const val GET_KEYWORKER_DETAILS = "/prisons/{prisonCode}/keyworkers/{staffId}"

    fun staffDetail(
      id: Long,
      scheduleType: ScheduleType,
      firstName: String = "First",
      lastName: String = "Last",
    ): StaffLocationRoleDto =
      StaffLocationRoleDto
        .builder()
        .staffId(id)
        .scheduleType(scheduleType.code)
        .firstName(firstName)
        .lastName(lastName)
        .build()
  }
}
