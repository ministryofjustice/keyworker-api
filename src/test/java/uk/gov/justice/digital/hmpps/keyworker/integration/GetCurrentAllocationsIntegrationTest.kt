package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.Author
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentStaffSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.PolicyEnabled
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
    getCurrentAllocationSpec(personIdentifier(), role = "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok when person identifier does not exist`() {
    val prisonCode = "DNE"
    val personIdentifier = personIdentifier()
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(personIdentifier), emptyList())

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
    assertThat(response.policies).isEmpty()
  }

  @Test
  fun `200 ok and current keyworker allocation returned`() {
    val prisonCode = "CAL"
    val personIdentifier = personIdentifier()
    givenPrisonConfig(prisonConfig(prisonCode, true))

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Description of $prisonCode")))
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(personIdentifier))

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
          deallocationReason =
            DeallocationReason.entries
              .filter { it !in listOf(DeallocationReason.PRISON_USES_KEY_WORK, DeallocationReason.MIGRATION) }
              .random(),
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

    val latestRecordedEvent =
      givenRecordedEvent(
        recordedEvent(
          prisonCode,
          RecordedEventType.SESSION,
          LocalDateTime.now().minusWeeks(2).truncatedTo(ChronoUnit.SECONDS),
          personIdentifier = personIdentifier,
        ),
      )
    (2..5).map {
      givenRecordedEvent(
        recordedEvent(
          prisonCode,
          RecordedEventType.SESSION,
          LocalDateTime.now().minusWeeks(it * 2L),
          personIdentifier = personIdentifier,
        ),
      )
    }
    prisonMockServer.stubStaffSummaries(
      listOf(
        staffSummary("Current", "Keyworker", currentAllocation.staffId),
        staffSummary("Session", "Keyworker", latestRecordedEvent.staffId),
      ),
    )
    prisonMockServer.stubStaffEmail(currentAllocation.staffId, "current-staff@justice.gov.uk")

    val response =
      getCurrentAllocationSpec(personIdentifier, true)
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
        CurrentStaffSummary(currentAllocation.staffId, "Current", "Keyworker", setOf("current-staff@justice.gov.uk")),
      ),
    )
    assertThat(response.latestRecordedEvents).containsOnly(
      RecordedEvent(
        pris,
        RecordedEventType.SESSION,
        latestRecordedEvent.occurredAt,
        AllocationPolicy.KEY_WORKER,
        Author(latestRecordedEvent.staffId, "Session", "Keyworker", latestRecordedEvent.username),
      ),
    )
    assertThat(response.policies).containsExactlyInAnyOrder(
      PolicyEnabled(AllocationPolicy.KEY_WORKER, true),
      PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, false),
    )
  }

  @Test
  fun `200 ok and current personal officer allocation returned`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "CAP"
    val personIdentifier = personIdentifier()
    givenPrisonConfig(prisonConfig(prisonCode, true))

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Description of $prisonCode")))
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(personIdentifier))

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

    val latestRecordedEvent =
      givenRecordedEvent(
        recordedEvent(
          "OUT",
          RecordedEventType.ENTRY,
          LocalDateTime.now().minusWeeks(2).truncatedTo(ChronoUnit.SECONDS),
          personIdentifier = personIdentifier,
        ),
      )
    (2..5).map {
      givenRecordedEvent(
        recordedEvent(
          prisonCode,
          RecordedEventType.ENTRY,
          LocalDateTime.now().minusWeeks(it * 2L),
          personIdentifier = personIdentifier,
        ),
      )
    }
    prisonMockServer.stubStaffSummaries(
      listOf(staffSummary("Personal", "Officer", currentAllocation.staffId)),
    )
    prisonMockServer.stubStaffEmail(currentAllocation.staffId, null)

    val response =
      getCurrentAllocationSpec(personIdentifier, true)
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
        CurrentStaffSummary(currentAllocation.staffId, "Personal", "Officer", emptySet()),
      ),
    )
    assertThat(response.latestRecordedEvents).containsOnly(
      RecordedEvent(
        CodedDescription("OUT", "OUT"),
        RecordedEventType.ENTRY,
        latestRecordedEvent.occurredAt,
        AllocationPolicy.PERSONAL_OFFICER,
        Author(latestRecordedEvent.staffId, "", "", latestRecordedEvent.username),
      ),
    )
    assertThat(response.policies).containsExactlyInAnyOrder(
      PolicyEnabled(AllocationPolicy.KEY_WORKER, false),
      PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, true),
    )
  }

  @Test
  fun `200 ok and no allocations returned if policy not active`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "CNA"
    val personIdentifier = personIdentifier()
    givenPrisonConfig(prisonConfig(prisonCode, false))

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Description of $prisonCode")))
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(personIdentifier))

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

    val latestRecordedEvent =
      givenRecordedEvent(
        recordedEvent(
          prisonCode,
          RecordedEventType.ENTRY,
          LocalDateTime.now().minusWeeks(2).truncatedTo(ChronoUnit.SECONDS),
          personIdentifier = personIdentifier,
        ),
      )
    (2..5).map {
      givenRecordedEvent(
        recordedEvent(
          prisonCode,
          RecordedEventType.ENTRY,
          LocalDateTime.now().minusWeeks(it * 2L),
          personIdentifier = personIdentifier,
        ),
      )
    }
    prisonMockServer.stubStaffSummaries(
      listOf(
        staffSummary("Personal", "Officer", currentAllocation.staffId),
        staffSummary("Session", "Officer", latestRecordedEvent.staffId),
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
    assertThat(response.allocations).isEmpty()
    assertThat(response.latestRecordedEvents).isEmpty()
    assertThat(response.policies).containsExactlyInAnyOrder(
      PolicyEnabled(AllocationPolicy.KEY_WORKER, false),
      PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, false),
    )
  }

  @Test
  fun `200 ok and complex needs correctly returned`() {
    val prisonCode = "COM"
    val prisonNumber = personIdentifier()

    prisonerSearchMockServer.stubFindPrisonerDetails(
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
    assertThat(response.policies).containsExactlyInAnyOrder(
      PolicyEnabled(AllocationPolicy.KEY_WORKER, false),
      PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, false),
    )
  }

  private fun getCurrentAllocationSpec(
    prisonNumber: String,
    includeContactDetails: Boolean = false,
    role: String? = Roles.ALLOCATIONS_RO,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .get()
      .uri {
        it.path(GET_CURRENT_ALLOCATION)
        it.queryParam("includeContactDetails", includeContactDetails)
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
