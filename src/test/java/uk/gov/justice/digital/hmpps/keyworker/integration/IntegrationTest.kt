package uk.gov.justice.digital.hmpps.keyworker.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.RevisionType
import org.hibernate.envers.query.AuditEntity
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AuditRevision
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerConfig
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntryRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSessionRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
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
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonRegisterMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonerSearchMockServer
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyPrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthHelper
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
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
  protected lateinit var keyworkerConfigRepository: KeyworkerConfigRepository

  @Autowired
  protected lateinit var keyworkerAllocationRepository: KeyworkerAllocationRepository

  @Autowired
  protected lateinit var offenderKeyworkerRepository: OffenderKeyworkerRepository

  @Autowired
  protected lateinit var prisonSupportedRepository: LegacyPrisonConfigurationRepository

  @Autowired
  protected lateinit var prisonConfigRepository: PrisonConfigurationRepository

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

  @Autowired
  internal lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  internal lateinit var entityManager: EntityManager

  @Autowired
  internal lateinit var contextHolder: AllocationContextHolder

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

  internal fun verifyAudit(
    entity: Any,
    entityId: Any,
    revisionType: RevisionType,
    affectedEntities: Set<String>,
    context: AllocationContext,
  ) {
    transactionTemplate.execute {
      val auditReader = AuditReaderFactory.get(entityManager)
      assertTrue(auditReader.isEntityClassAudited(entity::class.java))

      val revisionNumber =
        auditReader
          .getRevisions(entity::class.java, entityId)
          .filterIsInstance<Long>()
          .max()

      val entityRevision: Array<*> =
        auditReader
          .createQuery()
          .forRevisionsOfEntity(entity::class.java, false, true)
          .add(AuditEntity.revisionNumber().eq(revisionNumber))
          .resultList
          .first() as Array<*>
      assertThat(entityRevision[2]).isEqualTo(revisionType)

      val auditRevision = entityRevision[1] as AuditRevision
      with(auditRevision) {
        assertThat(username).isEqualTo(context.username)
        assertThat(caseloadId).isEqualTo(context.activeCaseloadId)
        assertThat(this.affectedEntities).containsExactlyInAnyOrderElementsOf(affectedEntities)
      }
    }
  }

  internal fun setContext(context: AllocationContext) {
    contextHolder.setContext(context)
  }

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

    @JvmField
    internal val prisonRegisterMockServer = PrisonRegisterMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      oAuthMockServer.start()
      prisonMockServer.start()
      complexityOfNeedMockServer.start()
      manageUsersMockServer.start()
      caseNotesMockServer.start()
      prisonerSearchMockServer.start()
      prisonRegisterMockServer.start()
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
      prisonRegisterMockServer.stop()
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
    enabled: Boolean = false,
    allowAutoAllocation: Boolean = false,
    capacity: Int = 6,
    maxCapacity: Int = 9,
    frequencyInWeeks: Int = 1,
    hasPrisonersWithHighComplexityNeeds: Boolean = false,
    policy: AllocationPolicy = AllocationPolicy.KEY_WORKER,
  ) = PrisonConfiguration(
    code,
    enabled,
    allowAutoAllocation,
    capacity,
    maxCapacity,
    frequencyInWeeks,
    hasPrisonersWithHighComplexityNeeds,
    policy.name,
  )

  protected fun givenPrisonConfig(prisonConfig: PrisonConfiguration): PrisonConfiguration = prisonConfigRepository.save(prisonConfig)

  protected fun prisonStat(
    prisonCode: String,
    date: LocalDate,
    totalPrisoners: Int,
    highComplexityOfNeedsPrisoners: Int,
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
    highComplexityOfNeedsPrisoners,
    eligiblePrisoners,
    assignedKeyworker,
    activeKeyworkers,
    keyworkerSessions,
    keyworkerEntries,
    averageReceptionToAllocationDays,
    averageReceptionToSessionDays,
  )

  protected fun givenOffenderKeyWorker(
    prisonNumber: String = personIdentifier(),
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
        this.allocationReason = allocationReason.asReferenceData()
        this.userId = userId
        expiryDateTime = expiredAt
        this.deallocationReason = deallocationReason?.asReferenceData()
        isActive = active
        prisonId = prisonCode
      },
    )

  protected fun keyworkerConfig(
    status: KeyworkerStatus,
    staffId: Long = newId(),
    capacity: Int = 6,
    allowAutoAllocation: Boolean = true,
    reactivateOn: LocalDate? = null,
  ) = KeyworkerConfig(withReferenceData(KEYWORKER_STATUS, status.name), capacity, allowAutoAllocation, reactivateOn, staffId)

  protected fun givenKeyworkerConfig(keyworkerConfig: KeyworkerConfig): KeyworkerConfig = keyworkerConfigRepository.save(keyworkerConfig)

  protected fun keyworkerAllocation(
    personIdentifier: String,
    prisonCode: String,
    staffId: Long = newId(),
    assignedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
    active: Boolean = true,
    allocationReason: AllocationReason = AllocationReason.AUTO,
    allocationType: AllocationType = AllocationType.AUTO,
    allocatedBy: String = "T357",
    expiryDateTime: LocalDateTime? = null,
    deallocationReason: DeallocationReason? = null,
    id: Long? = null,
  ) = KeyworkerAllocation(
    personIdentifier,
    prisonCode,
    staffId,
    assignedAt,
    active,
    allocationReason.asReferenceData(),
    allocationType,
    allocatedBy,
    expiryDateTime,
    deallocationReason?.asReferenceData(),
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

  protected fun AllocationReason.asReferenceData(): ReferenceData = withReferenceData(ReferenceDataDomain.ALLOCATION_REASON, reasonCode)

  protected fun DeallocationReason.asReferenceData(): ReferenceData = withReferenceData(ReferenceDataDomain.DEALLOCATION_REASON, reasonCode)
}
