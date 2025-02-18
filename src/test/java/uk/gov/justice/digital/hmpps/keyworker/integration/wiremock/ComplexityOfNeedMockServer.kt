package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexOffender
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

class ComplexityOfNeedMockServer : WireMockServer(10000) {
  fun stubComplexOffenders(json: String) {
    stubFor(
      WireMock
        .post(WireMock.urlPathEqualTo("/v1/complexity-of-need/multiple/offender-no"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(json),
        ),
    )
  }

  fun stubComplexOffenders(
    personIdentifiers: Set<String>,
    response: List<ComplexOffender>,
  ) {
    stubFor(
      WireMock
        .post(WireMock.urlPathEqualTo("/v1/complexity-of-need/multiple/offender-no"))
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(personIdentifiers), true, true))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubHealthOKResponse() {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/ping"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "test/plain")
            .withStatus(HttpStatus.OK.value())
            .withBody("Everything is fine."),
        ),
    )
  }
}
