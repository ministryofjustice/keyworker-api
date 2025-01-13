package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.client.UserDetails
import java.util.UUID

class ManageUsersMockServer : WireMockServer(9998) {
  private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

  fun stubGetUserDetails(
    username: String,
    userId: String,
    name: String,
    activeCaseloadId: String = "MDI",
  ): StubMapping =
    stubFor(
      get("/users/$username")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              mapper.writeValueAsString(
                UserDetails(
                  username = username,
                  active = true,
                  name = name,
                  authSource = "nomis",
                  activeCaseLoadId = activeCaseloadId,
                  userId = userId,
                  uuid = UUID.randomUUID(),
                ),
              ),
            )
            .withStatus(200),
        ),
    )

  fun stubGetUserDetailsNotFound(username: String): StubMapping = stubFor(get("/users/$username").willReturn(aResponse().withStatus(404)))
}
