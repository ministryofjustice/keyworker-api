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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.events.DomainEvent
import uk.gov.justice.digital.hmpps.keyworker.events.DomainEventListener
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ComplexityOfNeedMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ManageUsersMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.services.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthHelper
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonNumber
import uk.gov.justice.hmpps.casenotes.config.container.LocalStackContainer
import uk.gov.justice.hmpps.casenotes.config.container.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.hmpps.casenotes.config.container.PostgresContainer
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.MissingTopicException
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.publish
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Autowired
  protected lateinit var offenderKeyworkerRepository: OffenderKeyworkerRepository

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

  @Autowired
  internal lateinit var hmppsQueueService: HmppsQueueService

  val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("domaineventstopic") ?: throw MissingTopicException("domain events topic not found")
  }

  val domainEventsQueue by lazy {
    hmppsQueueService.findByQueueId("domaineventsqueue") ?: throw MissingQueueException("domain events queue not found")
  }

  internal fun publishEventToTopic(event: Any) {
    val eventType =
      when (event) {
        is DomainEvent -> event.eventType
        is MergeInformation -> DomainEventListener.PRISONER_MERGED
        else -> throw IllegalArgumentException("Unknown event $event")
      }
    domainEventsTopic.publish(eventType, objectMapper.writeValueAsString(event))
  }

  internal fun HmppsQueue.countAllMessagesOnQueue() = sqsClient.countAllMessagesOnQueue(queueUrl).get()

  companion object {
    @JvmField
    internal val prisonMockServer = PrisonMockServer()

    @JvmField
    internal val complexityOfNeedMockServer = ComplexityOfNeedMockServer()

    @JvmField
    internal val oAuthMockServer = OAuthMockServer()

    @JvmField
    internal val manageUsersMockServer = ManageUsersMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      oAuthMockServer.start()
      prisonMockServer.start()
      complexityOfNeedMockServer.start()
      manageUsersMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      oAuthMockServer.stop()
      prisonMockServer.stop()
      complexityOfNeedMockServer.stop()
      manageUsersMockServer.stop()
    }

    private val pgContainer = PostgresContainer.instance
    private val localStackContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
      }

      System.setProperty("aws.region", "eu-west-2")

      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }

  @BeforeEach
  fun resetStubs() {
    oAuthMockServer.resetAll()
    prisonMockServer.resetAll()
    complexityOfNeedMockServer.resetAll()
    oAuthMockServer.stubGrantToken()
    manageUsersMockServer.resetAll()
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

  internal fun givenOffenderKeyWorker(
    prisonNumber: String = prisonNumber(),
    staffId: Long = newId(),
    assignedAt: LocalDateTime = LocalDateTime.now(),
    allocationType: AllocationType = AllocationType.AUTO,
    allocationReason: AllocationReason = AllocationReason.AUTO,
    userId: String = newId().toString(),
    expiredAt: LocalDateTime? = null,
    deallocationReason: DeallocationReason? = null,
    active: Boolean = true,
    prisonCode: String = "MDI",
  ) = offenderKeyworkerRepository.save(
    OffenderKeyworker().apply {
      offenderNo = prisonNumber
      this.staffId = staffId
      assignedDateTime = assignedAt
      this.allocationType = allocationType
      this.allocationReason = allocationReason
      this.userId = userId
      expiryDateTime = expiredAt
      this.deallocationReason = deallocationReason
      isActive = active
      prisonId = prisonCode
    },
  )
}
