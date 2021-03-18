package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import jodd.net.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.config.JsonConfig

class ComplexityOfNeedMockServer : WireMockServer(10000) {
  fun stubComplexOffenders(json: String) {
    stubFor(
      WireMock.post(WireMock.urlPathEqualTo("/v1/complexity-of-need/multiple/offender-no"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(json)
        )
    )
  }

  fun stubHealthOKResponse() {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/health"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "test/plain")
            .withStatus(HttpStatus.ok().status())
            .withBody("Everything is fine.")
        )
    )
  }
}