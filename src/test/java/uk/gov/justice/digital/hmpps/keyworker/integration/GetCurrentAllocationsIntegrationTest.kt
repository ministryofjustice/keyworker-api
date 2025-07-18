package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.personalOfficerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate
import java.time.LocalDateTime

class GetCurrentAllocationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_CURRENT_ALLOCATION, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getCurrentAllocationSpec(personIdentifier(), "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok when person identifier does not exist`() {
    val prisonCode = "DNE"
    val personIdentifier = personIdentifier()
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, setOf(personIdentifier), emptyList())

    val response =
      getCurrentAllocationSpec(personIdentifier)
        .expectStatus()
        .isOk
        .expectBody(CurrentPersonStaffAllocation::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.prisonNumber).isEqualTo(personIdentifier)
    assertThat(response.allocations).isEmpty()
    assertThat(response.latestRecordedEvents).isEmpty()
  }

  @Test
  fun `200 ok and current keyworker allocation returned`() {
    val prisonCode = "CAL"
    val personIdentifier = personIdentifier()

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Description of $prisonCode")))
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, setOf(personIdentifier))

    val previous = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val current = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val keyworkers = listOf(previous, current)

    keyworkers.mapIndexed { i, a ->
      givenAllocation(
        staffAllocation(
          personIdentifier = personIdentifier,
          prisonCode = prisonCode,
          staffId = a.staffId,
          allocatedAt = LocalDateTime.now().minusWeeks(12L - (3 * i)),
          allocatedBy = "AS$i",
          active = false,
          deallocatedAt = LocalDateTime.now().minusWeeks(i.toLong()),
          deallocationReason = DeallocationReason.entries.random(),
          deallocatedBy = "DE$i",
        ),
      )
    }

    val currentAllocation =
      givenAllocation(
        staffAllocation(
          personIdentifier = personIdentifier,
          prisonCode = prisonCode,
          staffId = newId(),
          allocatedAt = LocalDateTime.now(),
          active = true,
          allocatedBy = "A110C473",
        ),
      )
    prisonMockServer.stubStaffSummaries(listOf(staffSummary("Current", "Keyworker", currentAllocation.staffId)))

    val latestSession = LocalDateTime.now().minusWeeks(2)
    caseNotesMockServer.stubUsageByPersonIdentifier(
      sessionTypes(
        prisonCode,
        setOf(personIdentifier),
        LocalDate.now().minusMonths(38),
        LocalDate.now(),
      ),
      NoteUsageResponse(
        mapOf(
          personIdentifier to
            listOf(
              UsageByPersonIdentifierResponse(
                personIdentifier,
                KW_TYPE,
                KW_SESSION_SUBTYPE,
                38,
                LatestNote(latestSession),
              ),
            ),
        ),
      ),
    )

    val response =
      getCurrentAllocationSpec(personIdentifier)
        .expectStatus()
        .isOk
        .expectBody(CurrentPersonStaffAllocation::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.prisonNumber).isEqualTo(personIdentifier)
    val pris = CodedDescription(prisonCode, "Description of $prisonCode")
    assertThat(response.allocations).containsOnly(
      CurrentAllocation(
        CodedDescription("KEY_WORKER", "Key worker"),
        pris,
        StaffSummary(currentAllocation.staffId, "Current", "Keyworker"),
      ),
    )
    assertThat(response.latestRecordedEvents).containsOnly(
      RecordedEvent(pris, RecordedEventType.SESSION, latestSession),
    )
  }

  @Test
  fun `200 ok and current personal officer allocation returned`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "CAP"
    val personIdentifier = personIdentifier()

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Description of $prisonCode")))
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, setOf(personIdentifier))

    val previous = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val current = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val keyworkers = listOf(previous, current)

    keyworkers.mapIndexed { i, a ->
      givenAllocation(
        staffAllocation(
          personIdentifier = personIdentifier,
          prisonCode = prisonCode,
          staffId = a.staffId,
          allocatedAt = LocalDateTime.now().minusWeeks(12L - (3 * i)),
          allocatedBy = "AS$i",
          active = false,
          deallocatedAt = LocalDateTime.now().minusWeeks(i.toLong()),
          deallocationReason = DeallocationReason.entries.random(),
          deallocatedBy = "DE$i",
        ),
      )
    }

    val currentAllocation =
      givenAllocation(
        staffAllocation(
          personIdentifier = personIdentifier,
          prisonCode = prisonCode,
          staffId = newId(),
          allocatedAt = LocalDateTime.now(),
          active = true,
          allocatedBy = "A110C473",
        ),
      )
    prisonMockServer.stubStaffSummaries(listOf(staffSummary("Personal", "Officer", currentAllocation.staffId)))

    val latestSession = LocalDateTime.now().minusWeeks(1)
    caseNotesMockServer.stubUsageByPersonIdentifier(
      personalOfficerTypes(
        prisonCode,
        setOf(personIdentifier),
        LocalDate.now().atStartOfDay().minusMonths(38),
        LocalDate.now().atStartOfDay(),
      ),
      NoteUsageResponse(
        mapOf(
          personIdentifier to
            listOf(
              UsageByPersonIdentifierResponse(
                personIdentifier,
                PO_ENTRY_TYPE,
                PO_ENTRY_SUBTYPE,
                38,
                LatestNote(latestSession),
              ),
            ),
        ),
      ),
    )

    val response =
      getCurrentAllocationSpec(personIdentifier)
        .expectStatus()
        .isOk
        .expectBody(CurrentPersonStaffAllocation::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.prisonNumber).isEqualTo(personIdentifier)
    val pris = CodedDescription(prisonCode, "Description of $prisonCode")
    assertThat(response.allocations).containsOnly(
      CurrentAllocation(
        CodedDescription("PERSONAL_OFFICER", "Personal officer"),
        pris,
        StaffSummary(currentAllocation.staffId, "Personal", "Officer"),
      ),
    )
    assertThat(response.latestRecordedEvents).containsOnly(
      RecordedEvent(pris, RecordedEventType.ENTRY, latestSession),
    )
  }

  @Test
  fun `200 ok and complex needs correctly returned`() {
    val prisonCode = "COM"
    val prisonNumber = personIdentifier()

    prisonerSearchMockServer.stubFindPrisonDetails(
      prisonCode,
      setOf(prisonNumber),
      listOf(
        Prisoner(
          prisonNumber,
          "First",
          "Last",
          LocalDate.now().minusDays(30),
          LocalDate.now().plusDays(90),
          prisonCode,
          "Description of $prisonCode",
          "$prisonCode-A-1",
          "STANDARD",
          ComplexityOfNeedLevel.HIGH,
          null,
          listOf(),
        ),
      ),
    )

    val response =
      getCurrentAllocationSpec(prisonNumber)
        .expectStatus()
        .isOk
        .expectBody(CurrentPersonStaffAllocation::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.prisonNumber).isEqualTo(prisonNumber)
    assertThat(response.allocations).isEmpty()
    assertThat(response.hasHighComplexityOfNeeds).isTrue
  }

  private fun getCurrentAllocationSpec(
    prisonNumber: String,
    role: String? = Roles.ALLOCATIONS_RO,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .get()
      .uri {
        it.path(GET_CURRENT_ALLOCATION)
        it.build(prisonNumber)
      }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
      .exchange()

  fun staffSummary(
    firstName: String = "First",
    lastName: String = "Last",
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val GET_CURRENT_ALLOCATION = "/prisoners/{prisonNumber}/allocations/current"
  }
}
