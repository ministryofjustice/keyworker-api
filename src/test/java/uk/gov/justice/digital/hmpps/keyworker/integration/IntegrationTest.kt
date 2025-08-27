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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.keyworker.config.container.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.keyworker.config.container.PostgresContainer
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.AuditRevision
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatisticRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.STAFF_STATUS
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedChange
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.CaseNotesMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ComplexityOfNeedMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.ManageUsersMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.NomisUserRolesMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.OAuthMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonRegisterMockServer
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.PrisonerSearchMockServer
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyPrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthHelper
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.MissingTopicException
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.publish
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTest {
  @Autowired
  protected lateinit var staffRoleRepository: StaffRoleRepository

  @Autowired
  protected lateinit var staffConfigRepository: StaffConfigRepository

  @Autowired
  protected lateinit var allocationRepository: AllocationRepository

  @Autowired
  protected lateinit var offenderKeyworkerRepository: LegacyKeyworkerAllocationRepository

  @Autowired
  protected lateinit var prisonSupportedRepository: LegacyPrisonConfigurationRepository

  @Autowired
  protected lateinit var prisonConfigRepository: PrisonConfigurationRepository

  @Autowired
  protected lateinit var prisonStatisticRepository: PrisonStatisticRepository

  @Autowired
  protected lateinit var nomisService: NomisService

  @Autowired
  lateinit var flyway: Flyway

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  internal lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  internal lateinit var recordedEventRepository: RecordedEventRepository

  @Autowired
  internal lateinit var referenceDataRepository: ReferenceDataRepository

  @Autowired
  internal lateinit var caseNoteRecordedEventRepository: CaseNoteRecordedEventRepository

  @MockitoBean
  internal lateinit var telemetryClient: TelemetryClient

  @Autowired
  internal lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  internal lateinit var entityManager: EntityManager

  @Autowired
  internal lateinit var contextHolder: AllocationContextHolder

  @MockitoSpyBean
  internal lateinit var nomisUserRolesApiClient: NomisUserRolesApiClient

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

  val offenderEventsTopic by lazy {
    hmppsQueueService.findByTopicId("keyworkerevents") ?: throw MissingTopicException("offender events topic not found")
  }

  val offenderEventsQueue by lazy {
    hmppsQueueService.findByQueueId("offenderevents") ?: throw MissingQueueException("offender events queue not found")
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

  internal fun publishOffenderEvent(
    eventType: String,
    event: OffenderEvent,
  ) {
    offenderEventsTopic.publish(eventType, objectMapper.writeValueAsString(event))
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

    @JvmField
    internal val nomisUserRolesMockServer = NomisUserRolesMockServer()

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
      nomisUserRolesMockServer.start()
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
      nomisUserRolesMockServer.stop()
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
    nomisUserRolesMockServer.resetAll()

    flyway.clean()
    flyway.migrate()
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
  }

  internal fun setOmicAdminHeaders(): (HttpHeaders) -> Unit = setHeaders(roles = listOf("ROLE_OMIC_ADMIN"))

  internal fun setHeaders(
    username: String? = "ITAG_USER",
    roles: List<String>? = emptyList(),
  ): (HttpHeaders) -> Unit {
    val token = jwtAuthHelper.createJwt(subject = username, roles = roles)
    return {
      it.setBearerAuth(token)
      it.contentType = MediaType.APPLICATION_JSON
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
    capacity: Int = 9,
    frequencyInWeeks: Int = 1,
    hasPrisonersWithHighComplexityNeeds: Boolean = false,
    policy: AllocationPolicy = AllocationContext.get().policy,
  ) = PrisonConfiguration(
    code,
    enabled,
    allowAutoAllocation,
    6,
    capacity,
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

  protected fun staffConfig(
    status: StaffStatus,
    staffId: Long = newId(),
    capacity: Int = 6,
    allowAutoAllocation: Boolean = true,
    reactivateOn: LocalDate? = null,
  ) = StaffConfiguration(
    withReferenceData(STAFF_STATUS, status.name),
    capacity,
    allowAutoAllocation,
    reactivateOn,
    staffId,
  )

  protected fun givenStaffConfig(staffConfig: StaffConfiguration): StaffConfiguration = staffConfigRepository.save(staffConfig)

  protected fun staffRole(
    prisonCode: String,
    staffId: Long,
    position: ReferenceData = withReferenceData(ReferenceDataDomain.STAFF_POSITION, "PRO"),
    scheduleType: ReferenceData = withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "FT"),
    hoursPerWeek: BigDecimal = BigDecimal(37.5),
    fromDate: LocalDate = LocalDate.now().minusDays(7),
    toDate: LocalDate? = null,
  ) = StaffRole(position, scheduleType, hoursPerWeek, fromDate, toDate, prisonCode, staffId)

  protected fun givenStaffRole(staffRole: StaffRole): StaffRole = staffRoleRepository.save(staffRole)

  protected fun staffAllocation(
    personIdentifier: String,
    prisonCode: String,
    staffId: Long = newId(),
    allocatedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
    active: Boolean = true,
    allocationReason: AllocationReason = AllocationReason.AUTO,
    allocationType: AllocationType = AllocationType.AUTO,
    allocatedBy: String = "T357",
    deallocatedAt: LocalDateTime? = null,
    deallocationReason: DeallocationReason? = null,
    deallocatedBy: String? = null,
  ) = Allocation(
    personIdentifier,
    prisonCode,
    staffId,
    allocatedAt,
    active,
    allocationReason.asReferenceData(),
    allocationType,
    allocatedBy,
    deallocatedAt,
    deallocationReason?.asReferenceData(),
    deallocatedBy,
  )

  protected fun givenAllocation(allocation: Allocation): Allocation = allocationRepository.save(allocation)

  protected fun givenRecordedEvent(re: () -> RecordedEvent): RecordedEvent =
    transactionTemplate.execute {
      recordedEventRepository.save(re())
    }!!

  protected fun withReferenceData(
    domain: ReferenceDataDomain,
    code: String,
  ): ReferenceData =
    referenceDataRepository.findByKey(ReferenceDataKey(domain, code))
      ?: throw IllegalArgumentException("Reference data does not exist: $code")

  protected fun AllocationReason.asReferenceData(): ReferenceData = withReferenceData(ReferenceDataDomain.ALLOCATION_REASON, reasonCode)

  protected fun DeallocationReason.asReferenceData(): ReferenceData = withReferenceData(ReferenceDataDomain.DEALLOCATION_REASON, reasonCode)

  protected fun RecordedEventType.asReferenceData(): ReferenceData = withReferenceData(ReferenceDataDomain.RECORDED_EVENT_TYPE, name)

  protected fun dateRange(
    start: LocalDate,
    end: LocalDate,
  ) = buildList {
    var next = start.plusDays(1)
    while (next.isBefore(end)) {
      add(next)
      next = next.plusDays(1)
    }
  }

  protected fun recordedEvent(
    prisonCode: String,
    type: RecordedEventType,
    occurredAt: LocalDateTime,
    createdAt: LocalDateTime = occurredAt,
    personIdentifier: String = personIdentifier(),
    staffId: Long = newId(),
    username: String = "US3R",
    id: UUID = IdGenerator.newUuid(),
  ): () -> RecordedEvent =
    {
      val type = type.asReferenceData()
      RecordedEvent(
        prisonCode,
        personIdentifier,
        staffId,
        username,
        occurredAt,
        createdAt,
        type,
        type.policyCode,
        id,
      )
    }
}
