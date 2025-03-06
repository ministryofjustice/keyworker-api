package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.SearchCaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

class CaseNotesMockServer : WireMockServer(9997) {
  fun stubUsageByPersonIdentifier(
    request: UsageByPersonIdentifierRequest,
    response: NoteUsageResponse<UsageByPersonIdentifierResponse>,
  ): StubMapping =
    stubFor(
      post("/case-notes/usage")
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(request), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )

  fun stubUsageByStaffIds(
    request: UsageByAuthorIdRequest,
    response: NoteUsageResponse<UsageByAuthorIdResponse>,
  ): StubMapping =
    stubFor(
      post("/case-notes/staff-usage")
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(request), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )

  fun stubGetCaseNote(caseNote: CaseNote): StubMapping =
    stubFor(
      get("/case-notes/${caseNote.personIdentifier}/${caseNote.id}")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(caseNote))
            .withStatus(200),
        ),
    )

  fun stubGetKeyworkerCaseNotes(personIdentifier: String, response: CaseNotes): StubMapping =
    stubFor(
      post("/case-notes/$personIdentifier")
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(SearchCaseNotes()), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )
}
