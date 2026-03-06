package uk.gov.justice.digital.hmpps.keyworker.integration

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarApiDataTest
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarFlywaySchemaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarIntegrationTestHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequest.SarJpaEntitiesTest
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

@Import(SubjectAccessRequestIntegrationTest.SarTestConfig::class)
class SubjectAccessRequestIntegrationTest :
  IntegrationTest(),
  SarApiDataTest,
  SarFlywaySchemaTest,
  SarJpaEntitiesTest {

  @Autowired
  lateinit var sarIntegrationTestHelper: SarIntegrationTestHelper

  @Autowired
  lateinit var dataSource: DataSource

  override fun getSarHelper(): SarIntegrationTestHelper = sarIntegrationTestHelper

  override fun getWebTestClientInstance(): WebTestClient = webTestClient

  override fun getDataSourceInstance(): DataSource = dataSource

  override fun getEntityManagerInstance(): EntityManager = entityManager

  override fun getPrn(): String = SAR_PRN

  override fun getFromDate(): LocalDate = LocalDate.parse("2024-01-01")

  override fun getToDate(): LocalDate = LocalDate.parse("2026-01-01")

  override fun setupTestData() {
    val staffId = STAFF_ID

    transactionTemplate.execute {
      allocationRepository.findAllByPersonIdentifier(SAR_PRN).forEach { allocationRepository.delete(it) }
    }

    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(staffId, "John", "Smith")))

    givenAllocation(
      staffAllocation(
        personIdentifier = SAR_PRN,
        prisonCode = PRISON_CODE,
        staffId = staffId,
        allocatedAt = LocalDateTime.parse("2024-06-15T10:30:00"),
        active = true,
        allocationReason = AllocationReason.MANUAL,
        allocatedBy = "ADMIN_USER",
      ),
    )

    givenAllocation(
      staffAllocation(
        personIdentifier = SAR_PRN,
        prisonCode = PRISON_CODE,
        staffId = staffId,
        allocatedAt = LocalDateTime.parse("2024-01-10T09:00:00"),
        active = false,
        allocationReason = AllocationReason.AUTO,
        allocatedBy = "SYS_USER",
        deallocatedAt = LocalDateTime.parse("2024-06-14T16:00:00"),
        deallocationReason = DeallocationReason.OVERRIDE,
        deallocatedBy = "ADMIN_USER",
      ),
    )
  }

  @TestConfiguration
  class SarTestConfig {
    @Bean
    fun sarIntegrationTestHelper(
      jwtAuthHelper: JwtAuthorisationHelper,
      @Value("\${hmpps.sar.tests.expected-api-response.path}") expectedApiResponsePath: String,
      @Value("\${hmpps.sar.tests.expected-render-result.path}") expectedRenderResultPath: String,
      @Value("\${hmpps.sar.tests.attachments-expected:false}") attachmentsExpected: Boolean,
      @Value("\${hmpps.sar.tests.expected-flyway-schema-version}") expectedFlywaySchemaVersion: String,
      @Value("\${hmpps.sar.tests.expected-jpa-entity-schema.path}") expectedJpaEntitySchemaPath: String,
      objectMapper: ObjectMapper,
    ): SarIntegrationTestHelper {
      val templateDataFetcherFacade = Mockito.mock(Class.forName("uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateDataFetcherFacade"))
      val templateHelpers = Mockito.mock(Class.forName("uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateHelpers"))
      val templateRenderService = Mockito.mock(Class.forName("uk.gov.justice.digital.hmpps.subjectaccessrequest.templates.TemplateRenderService"))

      return SarIntegrationTestHelper::class.java.declaredConstructors
        .first { it.parameterCount == 10 }
        .newInstance(
          jwtAuthHelper,
          expectedApiResponsePath,
          expectedRenderResultPath,
          attachmentsExpected,
          expectedFlywaySchemaVersion,
          expectedJpaEntitySchemaPath,
          objectMapper,
          templateDataFetcherFacade,
          templateHelpers,
          templateRenderService,
        ) as SarIntegrationTestHelper
    }
  }

  companion object {
    private const val SAR_PRN = "A1234AA"
    private const val PRISON_CODE = "LEI"
    private val STAFF_ID = newId()
  }
}
