package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentKeyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexOffender
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate
import java.time.LocalDateTime

class GetCurrentKeyworkerAllocationIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_KEYWORKER_ALLOCATION, "ANY", personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getCurrentKeyworkerAllocationSpec("ANY", personIdentifier(), "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok and current allocation returned`() {
    val prisonCode = "CAL"
    val prisonNumber = personIdentifier()

    givenPrisonConfig(prisonConfig(prisonCode))

    val previous = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val current = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val keyworkers = listOf(previous, current)

    keyworkers.mapIndexed { i, a ->
      givenAllocation(
        staffAllocation(
          personIdentifier = prisonNumber,
          prisonCode = prisonCode,
          staffId = a.staffId,
          assignedAt = LocalDateTime.now().minusWeeks(12L - (3 * i)),
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
          personIdentifier = prisonNumber,
          prisonCode = prisonCode,
          staffId = newId(),
          assignedAt = LocalDateTime.now(),
          active = true,
          allocatedBy = "A110C473",
        ),
      )
    prisonMockServer.stubStaffSummaries(listOf(staffSummary("Current", "Keyworker", currentAllocation.staffId)))

    caseNotesMockServer.stubUsageByPersonIdentifier(
      sessionTypes(
        prisonCode,
        setOf(prisonNumber),
        LocalDate.now().minusMonths(38),
        LocalDate.now(),
        setOf(currentAllocation.staffId.toString()),
      ),
      NoteUsageResponse<UsageByPersonIdentifierResponse>(
        mapOf(
          prisonNumber to
            listOf(
              UsageByPersonIdentifierResponse(
                prisonNumber,
                KW_TYPE,
                KW_SESSION_SUBTYPE,
                38,
                LatestNote(LocalDateTime.now().minusWeeks(2)),
              ),
            ),
        ),
      ),
    )

    val response =
      getCurrentKeyworkerAllocationSpec(prisonCode, prisonNumber)
        .expectStatus()
        .isOk
        .expectBody(CurrentPersonStaffAllocation::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.prisonNumber).isEqualTo(prisonNumber)
    assertThat(response.currentKeyworker)
      .isEqualTo(CurrentAllocation(CurrentKeyworker("Current", "Keyworker"), prisonCode))
    assertThat(response.hasHighComplexityOfNeeds).isFalse
    assertThat(response.latestSession).isEqualTo(LocalDate.now().minusWeeks(2))
  }

  @Test
  fun `200 ok and complex needs correctly returned`() {
    val prisonCode = "COM"
    val prisonNumber = personIdentifier()

    givenPrisonConfig(prisonConfig(prisonCode, hasPrisonersWithHighComplexityNeeds = true))

    complexityOfNeedMockServer.stubComplexOffenders(
      setOf(prisonNumber),
      listOf(ComplexOffender(prisonNumber, ComplexityOfNeedLevel.HIGH)),
    )

    val response =
      getCurrentKeyworkerAllocationSpec(prisonCode, prisonNumber)
        .expectStatus()
        .isOk
        .expectBody(CurrentPersonStaffAllocation::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.prisonNumber).isEqualTo(prisonNumber)
    assertThat(response.currentKeyworker).isNull()
    assertThat(response.hasHighComplexityOfNeeds).isTrue
  }

  private fun getCurrentKeyworkerAllocationSpec(
    prisonCode: String,
    prisonNumber: String,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_KEYWORKER_ALLOCATION)
      it.build(prisonCode, prisonNumber)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  fun staffSummary(
    firstName: String = "First",
    lastName: String = "Last",
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val GET_KEYWORKER_ALLOCATION = "prisons/{prisonCode}/prisoners/{prisonNumber}/keyworkers/current"
  }
}
