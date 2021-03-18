package uk.gov.justice.digital.hmpps.keyworker.integration

import com.amazonaws.services.sqs.AmazonSQS
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ComplexityOfNeedMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.EliteMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthHelper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {

  @Autowired
  lateinit var flyway: Flyway

  @Autowired
  lateinit var webTestClient: WebTestClient

  @SpyBean
  @Qualifier("awsSqsClientForOffenderEvents")
  internal lateinit var awsSqsClientForOffenderEvents: AmazonSQS

  @SpyBean
  @Qualifier("awsSqsClientForComplexityOfNeed")
  internal lateinit var awsSqsClientForComplexityOfNeed: AmazonSQS

  @Autowired
  internal lateinit var jwtAuthHelper: JwtAuthHelper

  init {
    SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "pw")
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  companion object {
    @JvmField
    internal val eliteMockServer = EliteMockServer()
    @JvmField
    internal val complexityOfNeedMockServer = ComplexityOfNeedMockServer()

    @JvmField
    internal val oAuthMockServer = OAuthMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      oAuthMockServer.start()
      eliteMockServer.start()
      complexityOfNeedMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      oAuthMockServer.stop()
      eliteMockServer.stop()
      complexityOfNeedMockServer.stop()
    }
  }

  @BeforeEach
  fun resetStubs() {
    flyway.clean()
    flyway.migrate()

    oAuthMockServer.resetAll()
    eliteMockServer.resetAll()
    complexityOfNeedMockServer.resetAll()
    oAuthMockServer.stubGrantToken()
  }

  internal fun setOmicAdminHeaders(): (HttpHeaders) -> Unit = setHeaders(roles = listOf("ROLE_OMIC_ADMIN"))

  internal fun setHeaders(username: String? = "ITAG_USER", roles: List<String>? = emptyList()): (HttpHeaders) -> Unit {
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
    eliteMockServer.stubAllocationHistory(prisonId, getWiremockResponse(prisonId, "auto-allocation"))
    eliteMockServer.stubAccessCodeListForKeyRole(prisonId)
    eliteMockServer.stubAccessCodeListForKeyAdminRole(prisonId)

    webTestClient.post()
      .uri("/key-worker/enable/$prisonId/auto-allocate?migrate=true&capacity=6&capacity=9&frequency=2")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  fun migrated(prisonId: String) {
    eliteMockServer.stubAllocationHistory(prisonId, getWiremockResponse(prisonId, "migrated"))
    eliteMockServer.stubAccessCodeListForKeyRole(prisonId)
    eliteMockServer.stubAccessCodeListForKeyAdminRole(prisonId)

    webTestClient.post()
      .uri("/key-worker/enable/$prisonId/auto-allocate?migrate=true")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  final fun getWiremockResponse(prisonId: String, fileName: String) =
    getResourceAsText("/wiremock-responses/$prisonId/$fileName.json")

  final fun getWiremockResponse(fileName: String) = getResourceAsText("/wiremock-responses/$fileName.json")

  fun getResourceAsText(path: String): String = this::class.java.getResource(path).readText()
}
