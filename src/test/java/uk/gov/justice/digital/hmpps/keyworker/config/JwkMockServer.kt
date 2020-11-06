package uk.gov.justice.digital.hmpps.keyworker.config

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.junit.WireMockRule

internal class JwkMockServer(port: Int) : WireMockRule(port) {
    fun stubJwkServer() {
        stubFor(
                WireMock.get(WireMock.urlEqualTo("/auth/.well-known/jwks.json"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
                                        .withStatus(200)
                                        .withBody(jwkSet()))
        )
    }

    private fun jwkSet(): String {
        return """{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "use": "sig",
      "kid": "dps-client-key",
      "alg": "RS256",
      "n": "sOPAtsQADdbRu_EH6LP5BM1_mF40VDBn12hJSXPPd5WYK0HLY20VM7AxxR9mnYCF6So1Wt7fGNqUx_WyemBpIJNrs_7Dzwg3uwiQuNh4zKR-EGxWbLwi3yw7lXPUzxUyC5xt88e_7vO-lz1oCnizjh4mxNAms6ZYF7qfnhJE9WvWPwLLkojkZu1JdusLaVowN7GTGNpME8dzeJkam0gp4oxHQGhMN87K6jqX3cEwO6Dvhemg8whs96nzQl8n2LFvAK2up9Prr9Gi2LFgTt7KqXA06kC4Kgw2IR1eFgzcBlTOEwmzjre65HoNaJBr9uNZzV5sILPMczzhQj_fMhz3_Q"
    }
  ]
}"""
    }
}