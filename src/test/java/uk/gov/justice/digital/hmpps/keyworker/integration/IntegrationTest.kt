package uk.gov.justice.digital.hmpps.keyworker.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.microsoft.applicationinsights.TelemetryClient
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.keyworker.domain.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntryRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSessionRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfig
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatisticRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.KEYWORKER_STATUS
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedChange
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.CaseNotesMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ComplexityOfNeedMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ManageUsersMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonerSearchMockServer
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository
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
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Autowired
  private lateinit var keyworkerRepository: KeyworkerRepository

  @Autowired
  private lateinit var keyworkerAllocationRepository: KeyworkerAllocationRepository

  @Autowired
  protected lateinit var offenderKeyworkerRepository: OffenderKeyworkerRepository

  @Autowired
  protected lateinit var prisonSupportedRepository: PrisonSupportedRepository

  @Autowired
  protected lateinit var prisonConfigRepository: PrisonConfigRepository

  @Autowired
  protected lateinit var prisonStatisticRepository: PrisonStatisticRepository

  @Autowired
  lateinit var flyway: Flyway

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  internal lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  internal lateinit var ksRepository: KeyworkerSessionRepository

  @Autowired
  internal lateinit var keRepository: KeyworkerEntryRepository

  @Autowired
  internal lateinit var referenceDataRepository: ReferenceDataRepository

  @MockitoBean
  internal lateinit var telemetryClient: TelemetryClient

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

  internal fun publishEventToTopic(
    event: Any,
    snsAttributes: Map<String, MessageAttributeValue> = emptyMap(),
  ) {
    val eventType =
      when (event) {
        is ComplexityOfNeedChange -> EventType.ComplexityOfNeedChanged.name
        is HmppsDomainEvent<*> -> event.eventType
        else -> throw IllegalArgumentException("Unknown event $event")
      }
    domainEventsTopic.publish(eventType, objectMapper.writeValueAsString(event), attributes = snsAttributes)
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

    @JvmField
    internal val caseNotesMockServer = CaseNotesMockServer()

    @JvmField
    internal val prisonerSearchMockServer = PrisonerSearchMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      oAuthMockServer.start()
      prisonMockServer.start()
      complexityOfNeedMockServer.start()
      manageUsersMockServer.start()
      caseNotesMockServer.start()
      prisonerSearchMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      oAuthMockServer.stop()
      prisonMockServer.stop()
      complexityOfNeedMockServer.stop()
      manageUsersMockServer.stop()
      caseNotesMockServer.stop()
      prisonerSearchMockServer.stop()
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
    caseNotesMockServer.resetAll()
    prisonerSearchMockServer.resetAll()
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

  fun getForEntity(url: String): WebTestClient.ResponseSpec =
    webTestClient
      .get()
      .uri(url)
      .exchange()

  fun migratedFoAutoAllocation(prisonId: String) {
    prisonMockServer.stubAllocationHistory(prisonId, getWiremockResponse(prisonId, "auto-allocation"))
    prisonMockServer.stubAccessCodeListForKeyRole(prisonId)
    prisonMockServer.stubAccessCodeListForKeyAdminRole(prisonId)

    webTestClient
      .post()
      .uri("/key-worker/enable/$prisonId/auto-allocate?migrate=true&capacity=6,9&frequency=2")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
  }

  fun migrated(prisonId: String) {
    prisonMockServer.stubAllocationHistory(prisonId, getWiremockResponse(prisonId, "migrated"))
    prisonMockServer.stubAccessCodeListForKeyRole(prisonId)
    prisonMockServer.stubAccessCodeListForKeyAdminRole(prisonId)

    webTestClient
      .post()
      .uri("/key-worker/enable/$prisonId/auto-allocate?migrate=true")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
  }

  fun setKeyworkerCapacity(
    prisonId: String,
    keyworkerId: Long,
    capacity: Int,
  ) {
    webTestClient
      .post()
      .uri("/key-worker/$keyworkerId/prison/$prisonId")
      .headers(setOmicAdminHeaders())
      .bodyValue(mapOf("capacity" to capacity, "status" to "ACTIVE"))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
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
        WireMock
          .aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "{\"status\":\"UP\"}" else "some error")
          .withStatus(status),
      ),
    )
  }

  protected fun getWiremockResponse(
    prisonId: String,
    fileName: String,
  ) = "/wiremock-stub-responses/$prisonId/$fileName.json".readFile()

  protected fun getWiremockResponse(fileName: String) = "/wiremock-stub-responses/$fileName.json".readFile()

  protected fun String.readFile(): String = this@IntegrationTest::class.java.getResource(this)!!.readText()

  protected fun prisonConfig(
    code: String,
    migrated: Boolean = false,
    migratedDateTime: LocalDateTime? = null,
    autoAllocate: Boolean = false,
    capacityTier1: Int = 6,
    capacityTier2: Int? = 9,
    kwSessionFrequencyInWeeks: Int = 1,
    hasPrisonersWithHighComplexityNeeds: Boolean = false,
  ) = PrisonConfig(
    code,
    migrated,
    migratedDateTime,
    autoAllocate,
    capacityTier1,
    capacityTier2,
    kwSessionFrequencyInWeeks,
    hasPrisonersWithHighComplexityNeeds,
  )

  protected fun givenPrisonConfig(prisonConfig: PrisonConfig): PrisonConfig = prisonConfigRepository.save(prisonConfig)

  protected fun prisonStat(
    prisonCode: String,
    date: LocalDate,
    totalPrisoners: Int,
    eligiblePrisoners: Int,
    assignedKeyworker: Int,
    activeKeyworkers: Int,
    keyworkerSessions: Int,
    keyworkerEntries: Int,
    averageReceptionToAllocationDays: Int?,
    averageReceptionToSessionDays: Int?,
  ) = PrisonStatistic(
    prisonCode,
    date,
    totalPrisoners,
    eligiblePrisoners,
    assignedKeyworker,
    activeKeyworkers,
    keyworkerSessions,
    keyworkerEntries,
    averageReceptionToAllocationDays,
    averageReceptionToSessionDays,
  )

  protected fun givenOffenderKeyWorker(
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
  ): OffenderKeyworker =
    offenderKeyworkerRepository.save(
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

  protected fun keyworker(
    status: KeyworkerStatus,
    staffId: Long = newId(),
    capacity: Int = 6,
    autoAllocation: Boolean = true,
  ) = Keyworker(withReferenceData(KEYWORKER_STATUS, status.name), capacity, autoAllocation, staffId)

  protected fun givenKeyworker(keyworker: Keyworker): Keyworker = keyworkerRepository.save(keyworker)

  protected fun keyworkerAllocation(
    personIdentifier: String,
    prisonCode: String,
    staffId: Long = newId(),
    assignedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
    active: Boolean = true,
    allocationReason: AllocationReason = AllocationReason.AUTO,
    allocationType: AllocationType = AllocationType.AUTO,
    userId: String? = "T357",
    expiryDateTime: LocalDateTime? = null,
    deallocationReason: DeallocationReason? = null,
    id: Long = newId(),
  ) = KeyworkerAllocation(
    personIdentifier,
    prisonCode,
    staffId,
    assignedAt,
    active,
    allocationReason,
    allocationType,
    userId,
    expiryDateTime,
    deallocationReason,
    id,
  )

  protected fun givenKeyworkerAllocation(allocation: KeyworkerAllocation): KeyworkerAllocation =
    keyworkerAllocationRepository.save(allocation)

  protected fun givenKeyworkerInteraction(interaction: KeyworkerInteraction): KeyworkerInteraction =
    when (interaction) {
      is KeyworkerSession -> ksRepository.save(interaction)
      is KeyworkerEntry -> keRepository.save(interaction)
    }

  protected fun withReferenceData(
    domain: ReferenceDataDomain,
    code: String,
  ): ReferenceData =
    referenceDataRepository.findByKey(ReferenceDataKey(domain, code))
      ?: throw IllegalArgumentException("Reference data does not exist: $code")
}
