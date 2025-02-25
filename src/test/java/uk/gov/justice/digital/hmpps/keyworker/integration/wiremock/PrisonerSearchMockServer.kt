package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoners
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper
import java.time.LocalDate

class PrisonerSearchMockServer : WireMockServer(9996) {
  fun stubFindAllPrisoners(
    prisonCode: String,
    prisoners: Prisoners,
  ): StubMapping =
    stubFor(
      get(urlPathEqualTo("/prisoner-search/prison/$prisonCode"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(prisoners))
            .withStatus(200),
        ),
    )

  fun stubFindPrisonDetails(
    prisonNumbers: Set<String>,
    prisoners: List<Prisoner> =
      prisonNumbers.map {
        Prisoner(
          it,
          "First",
          "Last",
          LocalDate.now().minusDays(30),
          LocalDate.now().plusDays(90),
          "DEF",
          "Default Prison",
          "STANDARD",
        )
      },
  ): StubMapping =
    stubFor(
      post(urlPathEqualTo("/prisoner-search/prisoner-numbers"))
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(prisonNumbers), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(prisoners))
            .withStatus(200),
        ),
    )
}
