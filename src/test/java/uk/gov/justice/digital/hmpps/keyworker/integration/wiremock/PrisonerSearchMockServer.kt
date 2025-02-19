package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoners
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

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
}
