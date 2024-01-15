package uk.gov.justice.digital.hmpps.keyworker.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ComplexityOfNeedMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthHelper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Autowired
  lateinit var flyway: Flyway

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  internal lateinit var jwtAuthHelper: JwtAuthHelper

  init {
    SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "pw")
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  companion object {
    @JvmField
    internal val prisonMockServer = PrisonMockServer()

    @JvmField
    internal val complexityOfNeedMockServer = ComplexityOfNeedMockServer()

    @JvmField
    internal val oAuthMockServer = OAuthMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      oAuthMockServer.start()
      prisonMockServer.start()
      complexityOfNeedMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      oAuthMockServer.stop()
      prisonMockServer.stop()
      complexityOfNeedMockServer.stop()
    }
  }

  @BeforeEach
  fun resetStubs() {
    oAuthMockServer.resetAll()
    prisonMockServer.resetAll()
    complexityOfNeedMockServer.resetAll()
    oAuthMockServer.stubGrantToken()
  }

  @AfterEach
  fun resetDb() {
    flyway.clean()
    flyway.migrate()
  }

  internal fun setOmicAdminHeaders(): (HttpHeaders) -> Unit = setHeaders(roles = listOf("ROLE_OMIC_ADMIN"))

  internal fun setHeaders(
    username: String? = "ITAG_USER",
    roles: List<String>? = emptyList(),
  ): (HttpHeaders) -> Unit {
    val token = jwtAuthHelper.createJwt(subject = username, roles = roles)
    return {
      it.setBearerAuth(token)
      it.setContentType(MediaType.APPLICATION_JSON)
    }
  }

  fun getForEntity(url: String): WebTestClient.ResponseSpec {
    return webTestClient.get()
      .uri(url)
      .exchange()
  }

  fun migratedFoAutoAllocation(prisonId: String) {
    prisonMockServer.stubAllocationHistory(prisonId, getWiremockResponse(prisonId, "auto-allocation"))
    prisonMockServer.stubAccessCodeListForKeyRole(prisonId)
    prisonMockServer.stubAccessCodeListForKeyAdminRole(prisonId)

    webTestClient.post()
      .uri("/key-worker/enable/$prisonId/auto-allocate?migrate=true&capacity=6,9&frequency=2")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  fun migrated(prisonId: String) {
    prisonMockServer.stubAllocationHistory(prisonId, getWiremockResponse(prisonId, "migrated"))
    prisonMockServer.stubAccessCodeListForKeyRole(prisonId)
    prisonMockServer.stubAccessCodeListForKeyAdminRole(prisonId)

    webTestClient.post()
      .uri("/key-worker/enable/$prisonId/auto-allocate?migrate=true")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  fun setKeyworkerCapacity(
    prisonId: String,
    keyworkerId: Long,
    capacity: Int,
  ) {
    webTestClient.post()
      .uri("/key-worker/$keyworkerId/prison/$prisonId")
      .headers(setOmicAdminHeaders())
      .bodyValue(mapOf("capacity" to capacity, "status" to "ACTIVE"))
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  fun subPing(status: Int) {
    addConditionalPingStub(prisonMockServer, status)
    addConditionalPingStub(oAuthMockServer, status)
    addConditionalPingStub(complexityOfNeedMockServer, status, "/ping")
  }

  fun addConditionalPingStub(
    mock: WireMockServer,
    status: Int,
    url: String? = "/health/ping",
  ) {
    mock.stubFor(
      WireMock.get(url).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "{\"status\":\"UP\"}" else "some error")
          .withStatus(status),
      ),
    )
  }

  internal fun getWiremockResponse(
    prisonId: String,
    fileName: String,
  ) = "/wiremock-stub-responses/$prisonId/$fileName.json".readFile()

  internal fun getWiremockResponse(fileName: String) = "/wiremock-stub-responses/$fileName.json".readFile()

  internal fun String.readFile(): String = this@IntegrationTest::class.java.getResource(this).readText()
}
