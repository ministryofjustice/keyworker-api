package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerNumbers
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoners
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

  fun stubFindFilteredPrisoners(
    prisonCode: String,
    prisoners: Prisoners,
    queryParams: Map<String, String> = mapOf(),
  ): StubMapping {
    val request = get(urlPathEqualTo("/prison/$prisonCode/prisoners"))
    queryParams.forEach { queryParam ->
      request.withQueryParam(queryParam.key, equalTo(queryParam.value))
    }
    return stubFor(
      request.willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(prisoners))
          .withStatus(200),
      ),
    )
  }

  fun stubFindPrisonerDetails(
    prisonCode: String,
    prisonNumbers: Set<String>,
    prisoners: List<Prisoner> =
      prisonNumbers.map {
        Prisoner(
          it,
          "First",
          "Last",
          LocalDate.now().minusDays(30),
          LocalDate.now().plusDays(90),
          prisonCode,
          prisonCode,
          "Description of $prisonCode",
          "$prisonCode-A-1",
          "STANDARD",
          null,
          null,
          listOf(),
        )
      },
  ): StubMapping =
    stubFor(
      post(urlPathEqualTo("/prisoner-search/prisoner-numbers"))
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(PrisonerNumbers(prisonNumbers)), true, true))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(prisoners))
            .withStatus(200),
        ),
    )
}
