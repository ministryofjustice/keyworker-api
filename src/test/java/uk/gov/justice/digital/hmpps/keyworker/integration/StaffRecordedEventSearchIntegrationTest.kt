package uk.gov.justice.digital.hmpps.keyworker.integration

import io.jsonwebtoken.security.Jwks.OP.policy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.SearchCaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.TypeSubTypeRequest
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.time.LocalDate
import java.time.LocalDateTime

class StaffRecordedEventSearchIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .post()
      .uri(SEARCH_URL, "NEP", newId())
      .bodyValue(searchRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    searchRecordedEventSpec(
      "DNM",
      newId(),
      searchRequest(),
      policy = AllocationPolicy.PERSONAL_OFFICER,
      "ROLE_ANY__OTHER_RW",
    ).expectStatus().isForbidden
  }

  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `can retrieve recorded events`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "REP"
    val staffId = newId()
    val staffUsername = username()
    val cnRequest =
      when (policy) {
        AllocationPolicy.PERSONAL_OFFICER -> setOf(TypeSubTypeRequest(PO_ENTRY_TYPE, setOf(PO_ENTRY_SUBTYPE)))
        AllocationPolicy.KEY_WORKER -> setOf(TypeSubTypeRequest(KW_TYPE, setOf(KW_SESSION_SUBTYPE, KW_ENTRY_SUBTYPE)))
      }
    val caseNotes =
      cnRequest.flatMap { r ->
        r.subTypes.map {
          caseNote(
            it,
            r.type,
            prisonCode = prisonCode,
            staffId = staffId,
            staffUsername = staffUsername,
            amendments = listOf(CaseNoteAmendment(LocalDateTime.now(), "John Smith", "Amended Text")),
          )
        }
      }
    caseNotesMockServer.stubSearchStaffCaseNotes(
      prisonCode,
      staffId,
      SearchCaseNotes(cnRequest),
      CaseNotes(caseNotes),
    )
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, caseNotes.map { it.personIdentifier }.toSet())

    val response =
      searchRecordedEventSpec(prisonCode, staffId, searchRequest(), policy)
        .expectStatus()
        .isOk
        .expectBody(RecordedEventResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.recordedEvents).hasSize(
      when (policy) {
        AllocationPolicy.KEY_WORKER -> 2
        AllocationPolicy.PERSONAL_OFFICER -> 1
      },
    )
    val recordedEvent = response.recordedEvents.first()
    assertThat(recordedEvent.prisoner.firstName).isEqualTo("First")
    assertThat(recordedEvent.prisoner.lastName).isEqualTo("Last")
    assertThat(response.recordedEvents.map { it.type }).containsExactlyInAnyOrderElementsOf(
      when (policy) {
        AllocationPolicy.KEY_WORKER ->
          listOf(
            CodedDescription("SESSION", "Key worker session"),
            CodedDescription("ENTRY", "Key worker entry"),
          )

        AllocationPolicy.PERSONAL_OFFICER -> listOf(CodedDescription("ENTRY", "Personal officer entry"))
      },
    )
  }

  @Test
  fun `can filter recorded event types`() {
    val policy = AllocationPolicy.KEY_WORKER
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "FRE"
    val staffId = newId()
    val staffUsername = username()
    val cnRequest = setOf(TypeSubTypeRequest(KW_TYPE, setOf(KW_SESSION_SUBTYPE)))
    val caseNotes =
      cnRequest.flatMap { r ->
        r.subTypes.map {
          caseNote(
            it,
            r.type,
            prisonCode = prisonCode,
            staffId = staffId,
            staffUsername = staffUsername,
            amendments = listOf(CaseNoteAmendment(LocalDateTime.now(), "John Smith", "Amended Text")),
          )
        }
      }
    caseNotesMockServer.stubSearchStaffCaseNotes(
      prisonCode,
      staffId,
      SearchCaseNotes(cnRequest),
      CaseNotes(caseNotes),
    )
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, caseNotes.map { it.personIdentifier }.toSet())

    val response =
      searchRecordedEventSpec(prisonCode, staffId, searchRequest(), policy)
        .expectStatus()
        .isOk
        .expectBody(RecordedEventResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.recordedEvents).hasSize(1)
    val recordedEvent = response.recordedEvents.first()
    assertThat(recordedEvent.prisoner.firstName).isEqualTo("First")
    assertThat(recordedEvent.prisoner.lastName).isEqualTo("Last")
    assertThat(response.recordedEvents.map { it.type }).containsExactlyInAnyOrderElementsOf(
      listOf(CodedDescription("SESSION", "Key worker session")),
    )
  }

  @Test
  fun `returns empty content when no case notes`() {
    val policy = AllocationPolicy.PERSONAL_OFFICER
    setContext(AllocationContext.get().copy(policy = policy))

    val prisonCode = "NRE"
    val staffId = newId()
    val cnRequest = setOf(TypeSubTypeRequest(PO_ENTRY_TYPE, setOf(PO_ENTRY_SUBTYPE)))
    caseNotesMockServer.stubSearchStaffCaseNotes(
      prisonCode,
      staffId,
      SearchCaseNotes(cnRequest),
      CaseNotes(listOf()),
    )

    val response =
      searchRecordedEventSpec(prisonCode, staffId, searchRequest(), policy)
        .expectStatus()
        .isOk
        .expectBody(RecordedEventResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.recordedEvents).isEmpty()
  }

  private fun searchRequest(
    types: Set<RecordedEventType> = setOf(),
    from: LocalDate? = null,
    to: LocalDate? = null,
  ) = RecordedEventRequest(types, from, to)

  private fun searchRecordedEventSpec(
    prisonCode: String,
    staffId: Long,
    request: RecordedEventRequest,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode, staffId)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/staff/{staffId}/recorded-events"
  }
}
