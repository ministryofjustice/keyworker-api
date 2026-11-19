package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.jsonMapper

class PrisonRegisterMockServer : WireMockServer(9995) {
  fun stubGetPrisons(prisons: Set<Prison>) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/prisons/names"))
        .withBearerToken()
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(prisons)),
        ),
    )
  }

  fun stubGetPrison(prison: Prison) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/prisons/ids/${prison.prisonId}"))
        .withBearerToken()
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(prison)),
        ),
    )
  }
}
