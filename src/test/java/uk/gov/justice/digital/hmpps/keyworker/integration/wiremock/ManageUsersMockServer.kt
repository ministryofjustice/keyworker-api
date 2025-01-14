package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.justice.digital.hmpps.keyworker.integration.UserDetails
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper
import java.util.UUID

class ManageUsersMockServer : WireMockServer(9998) {
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
              objectMapper.writeValueAsString(
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
