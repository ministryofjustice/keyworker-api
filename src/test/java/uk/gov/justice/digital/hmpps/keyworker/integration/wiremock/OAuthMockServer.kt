package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import jodd.net.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.config.JsonConfig

class OAuthMockServer : WireMockServer(8090) {
  private val gson = JsonConfig().gson()

  fun stubGrantToken() {
    stubFor(
      WireMock.post(WireMock.urlEqualTo("/auth/oauth/token"))
        .willReturn(
          WireMock.aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(gson.toJson(mapOf("access_token" to "ABCDE", "token_type" to "bearer")))
        )
    )
  }

  fun stubHealthOKResponse() {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/health/ping"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody("""{"status":"UP","HttpStatus":200}""")
        )
    )
  }
}
