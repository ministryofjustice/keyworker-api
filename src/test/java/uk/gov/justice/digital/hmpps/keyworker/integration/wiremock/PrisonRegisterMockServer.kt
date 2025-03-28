package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonsByIdsRequest
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

class PrisonRegisterMockServer : WireMockServer(9995) {
  fun stubGetPrisons(prisons: Set<Prison>) {
    stubFor(
      WireMock
        .post(WireMock.urlPathEqualTo("/prisons/prisonsByIds"))
        .withRequestBody(
          equalToJson(
            objectMapper.writeValueAsString(PrisonsByIdsRequest(prisons.map { it.prisonId }.toSet())),
            true,
            true,
          ),
        ).willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(prisons)),
        ),
    )
  }
}
