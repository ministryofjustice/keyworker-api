package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesOfInterest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.SearchCaseNotes
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.jsonMapper
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime
import java.util.UUID

class CaseNotesMockServer : WireMockServer(9997) {
  fun stubGetCaseNote(caseNote: CaseNote): StubMapping =
    stubFor(
      get("/case-notes/${caseNote.personIdentifier}/${caseNote.id}")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(caseNote))
            .withStatus(200),
        ),
    )

  fun stubSearchCaseNotes(
    personIdentifier: String,
    ofInterest: CaseNotesOfInterest,
    response: CaseNotes,
  ): StubMapping =
    stubFor(
      post("/search/case-notes/$personIdentifier")
        .withRequestBody(equalToJson(jsonMapper.writeValueAsString(SearchCaseNotes(ofInterest.asRequest())), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )

  fun stubSearchStaffCaseNotes(
    prisonCode: String,
    staffId: Long,
    request: SearchCaseNotes,
    response: CaseNotes,
  ): StubMapping =
    stubFor(
      post("/search/case-notes/prisons/$prisonCode/authors/$staffId")
        .withRequestBody(equalToJson(jsonMapper.writeValueAsString(request), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )

  companion object {
    fun caseNote(
      subType: String,
      type: String = KW_TYPE,
      personIdentifier: String = personIdentifier(),
      occurredAt: LocalDateTime = LocalDateTime.now().minusDays(1),
      staffId: Long = newId(),
      staffUsername: String = NomisIdGenerator.username(),
      prisonCode: String = "LEI",
      createdAt: LocalDateTime = LocalDateTime.now(),
      text: String = "Some notes about the Recorded Event",
      amendments: List<CaseNoteAmendment> = listOf(),
      id: UUID = IdGenerator.newUuid(),
    ): CaseNote =
      CaseNote(
        id,
        type,
        subType,
        occurredAt,
        personIdentifier,
        staffId,
        staffUsername,
        prisonCode,
        createdAt,
        text,
        amendments,
      )
  }
}
