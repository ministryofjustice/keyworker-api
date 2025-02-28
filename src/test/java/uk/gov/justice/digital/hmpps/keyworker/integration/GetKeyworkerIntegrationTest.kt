package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.Agency
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonNumber
import java.time.LocalDate.now
import java.time.LocalDateTime

class GetKeyworkerIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unathorised without a valid token`() {
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
    val keyworker = givenKeyworker(keyworker(KeyworkerStatus.ACTIVE, capacity = 10))
    prisonMockServer.stubKeyworkerDetails(keyworker.staffId)

    val allocations =
      (0..30).map {
        givenKeyworkerAllocation(
          keyworkerAllocation(
            personIdentifier = prisonNumber(),
            prisonCode = prisonCode,
            staffId = keyworker.staffId,
            assignedAt = LocalDateTime.now().minusDays(it * 2L),
            active = it % 4 != 0,
            expiryDateTime = if (it % 9 == 0) LocalDateTime.now().minusDays(1) else null,
          ),
        )
      }

    val personIdentifiers = allocations.filter { it.active }.map { it.personIdentifier }.toSet()
    prisonerSearchMockServer.stubFindPrisonDetails(personIdentifiers)
    caseNotesMockServer.stubUsageByPersonIdentifier(
      keyworkerTypes(personIdentifiers, now().minusMonths(1), now(), setOf(keyworker.staffId.toString())),
      caseNoteResponse(personIdentifiers),
    )
    caseNotesMockServer.stubUsageByPersonIdentifier(
      keyworkerTypes(
        personIdentifiers,
        now().minusMonths(2),
        now().minusMonths(1),
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
      .isEqualTo(Keyworker(keyworker.staffId, "First", "Last", CodedDescription("FT", "Full Time")))
    assertThat(response.status).isEqualTo(CodedDescription("ACT", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription("DEF", "Default Prison"))
    assertThat(response.capacity).isEqualTo(10)
    assertThat(response.allocated).isEqualTo(23)
    assertThat(response.allocations.size).isEqualTo(23)

    assertThat(response.stats.current!!).isNotNull()
    with(response.stats.current) {
      assertThat(projectedSessions).isEqualTo(67)
      assertThat(recordedSessions).isEqualTo(12)
      assertThat(recordedEntries).isEqualTo(4)
      assertThat(complianceRate).isEqualTo(17.91)
    }

    assertThat(response.stats.previous!!).isNotNull()
    with(response.stats.previous) {
      assertThat(projectedSessions).isEqualTo(10)
      assertThat(recordedSessions).isEqualTo(0)
      assertThat(recordedEntries).isEqualTo(0)
      assertThat(complianceRate).isEqualTo(null)
    }
  }

  @Test
  fun `200 ok and keyworker details returned when no allocations exist and no config`() {
    val agency = Agency("NOAL", "No Allocations")
    val (prisonCode, prisonDescription) = agency
    val staff = StaffSummary(newId(), "Noah", "Cations", "PT")
    prisonMockServer.stubKeyworkerDetails(staff.staffId, staff)
    prisonMockServer.stubGetAgency(prisonCode, agency)

    val response =
      getKeyworkerSpec(prisonCode, staff.staffId)
        .expectStatus()
        .isOk
        .expectBody(KeyworkerDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.keyworker)
      .isEqualTo(Keyworker(staff.staffId, staff.firstName, staff.lastName, CodedDescription("PT", "Part Time")))
    assertThat(response.status).isEqualTo(CodedDescription("ACT", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription(prisonCode, prisonDescription))
    assertThat(response.capacity).isEqualTo(6)
    assertThat(response.allocated).isEqualTo(0)
    assertThat(response.allocations.size).isEqualTo(0)
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
          buildSet<UsageByPersonIdentifierResponse> {
            if (index % 2 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  KW_TYPE,
                  SESSION_SUBTYPE,
                  3 * index / personIdentifiers.size,
                  LatestNote(LocalDateTime.now().minusDays(index * 2L)),
                ),
              )
            }
            if (index % 3 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  KW_TYPE,
                  ENTRY_SUBTYPE,
                  2 * index / personIdentifiers.size,
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
  }
}
