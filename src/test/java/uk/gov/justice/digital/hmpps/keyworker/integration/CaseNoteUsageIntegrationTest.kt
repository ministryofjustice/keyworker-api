package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.TypeSubTypeRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonNumber
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CaseNoteUsageIntegrationTest : IntegrationTest() {
  @Autowired
  internal lateinit var nomisService: NomisService

  @Test
  fun `usage by author`() {
    val noteUsage = listOf(
      UsageByAuthorIdResponse("-4", "KA", "KS", 6, latestNote(6)),
      UsageByAuthorIdResponse("-5", "KA", "KS", 3, latestNote(3)),
    )

    val request = UsageByAuthorIdRequest(
      authorIds = setOf("-4", "-5"),
      typeSubTypes = setOf(TypeSubTypeRequest("KA", setOf("KS")))
    )

    caseNotesMockServer.stubUsageByStaffIds(
      request = request,
      response = NoteUsageResponse(content = noteUsage.groupBy { it.authorId }.toMap())
    )

    val usage = nomisService.getCaseNoteUsage(listOf(-4, -5), "KA", "KS", null, null, 0)

    assertThat(usage.size).isEqualTo(2)
    assertThat(usage).containsExactlyInAnyOrder(
      CaseNoteUsageDto(-4, "KA", "KS", 6, LocalDate.now().minusDays(6)),
      CaseNoteUsageDto(-5, "KA", "KS", 3, LocalDate.now().minusDays(3)),
    )
  }

  @Test
  fun `usage by prison number`() {
    val pn1 = prisonNumber()
    val pn2 = prisonNumber()
    val noteUsage = listOf(
      UsageByPersonIdentifierResponse(pn1, "KA", "KE", 5, latestNote(5)),
      UsageByPersonIdentifierResponse(pn2, "KA", "KE", 2, latestNote(2)),
    )

    val request = UsageByPersonIdentifierRequest(
      personIdentifiers = setOf(pn1, pn2),
      typeSubTypes = setOf(TypeSubTypeRequest("KA", setOf("KE")))
    )

    caseNotesMockServer.stubUsageByPersonIdentifier(
      request = request,
      response = NoteUsageResponse(content = noteUsage.groupBy { it.personIdentifier }.toMap())
    )

    val usage = nomisService.getCaseNoteUsageForPrisoners(listOf(pn1, pn2), null, "KA", "KE", null, null)

    assertThat(usage.size).isEqualTo(2)
    assertThat(usage).containsExactlyInAnyOrder(
      CaseNoteUsagePrisonersDto(pn1, "KA", "KE", 5, LocalDate.now().minusDays(5)),
      CaseNoteUsagePrisonersDto(pn2, "KA", "KE", 2, LocalDate.now().minusDays(2)),
    )
  }

  @Test
  fun `usage by prison number with staff filter`() {
    val pn1 = prisonNumber()
    val pn2 = prisonNumber()
    val staffId = 126758381L
    val noteUsage = listOf(
      UsageByPersonIdentifierResponse(pn1, "KA", "KE", 2, latestNote(2)),
      UsageByPersonIdentifierResponse(pn2, "KA", "KE", 1, latestNote(1)),
    )

    val request = UsageByPersonIdentifierRequest(
      personIdentifiers = setOf(pn1, pn2),
      typeSubTypes = setOf(TypeSubTypeRequest("KA", setOf("KE"))),
      authorIds = setOf("$staffId")
    )

    caseNotesMockServer.stubUsageByPersonIdentifier(
      request = request,
      response = NoteUsageResponse(content = noteUsage.groupBy { it.personIdentifier }.toMap())
    )

    val usage = nomisService.getCaseNoteUsageForPrisoners(listOf(pn1, pn2), staffId, "KA", "KE", null, null)

    assertThat(usage.size).isEqualTo(2)
    assertThat(usage).containsExactlyInAnyOrder(
      CaseNoteUsagePrisonersDto(pn1, "KA", "KE", 2, LocalDate.now().minusDays(2)),
      CaseNoteUsagePrisonersDto(pn2, "KA", "KE", 1, LocalDate.now().minusDays(1)),
    )
  }

  private fun latestNote(daysAgo: Long) = LatestNote(UUID.randomUUID(), LocalDateTime.now().minusDays(daysAgo))
}
