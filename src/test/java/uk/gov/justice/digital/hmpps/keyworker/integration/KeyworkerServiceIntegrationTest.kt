package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary

class KeyworkerServiceIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val KEYWORKER_ID_1 = 1001L
    const val MIGRATED_ALLOCATION_OFFENDER_ID = "UNALLOC1"
    const val NON_MIGRATED_ALLOCATION_OFFENDER_ID = "ALLOCED1"
    const val NO_HISTORY_OFFENDER_ID = "UNALLOC2"
  }

  val oFFENDERSHISTORY: String = getWiremockResponse(PRISON_ID, "offenders-history")
  val oFFENDERSATLOCATION: String = getWiremockResponse(PRISON_ID, "offenders-at-location")
  val sTAFFLOCATIONROLELIST = getWiremockResponse(PRISON_ID, "staff-location-role-list")

  @BeforeEach
  @Test
  fun beforeEach() {
    migratedFoAutoAllocation(PRISON_ID)
    prisonMockServer.stubOffendersAtLocationForAutoAllocation(oFFENDERSATLOCATION)
    prisonMockServer.stubKeyworkerRoles(PRISON_ID, KEYWORKER_ID_1, sTAFFLOCATIONROLELIST)
    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(KEYWORKER_ID_1, "John", "Smith")))
  }

  @Test
  fun `Allocation history for offender reports ok`() {
    addKeyworkerAllocation()
    prisonMockServer.stubkeyworkerDetails(KEYWORKER_ID_1, getWiremockResponse("staff-details--5"))
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)
    prisonMockServer.stubPrisonerStatus(NON_MIGRATED_ALLOCATION_OFFENDER_ID, getWiremockResponse("prisoners_information_A1234AA"))

    webTestClient
      .get()
      .uri("/key-worker/allocation-history/$NON_MIGRATED_ALLOCATION_OFFENDER_ID")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .json("keyworker-service-controller-allocation-history.json".readFile())
  }

  @Disabled
  @Test
  fun `Allocation history summary reports ok`() {
    addKeyworkerAllocation()
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)

    webTestClient
      .post()
      .uri("/key-worker/allocation-history/summary")
      .bodyValue(listOf(MIGRATED_ALLOCATION_OFFENDER_ID, NON_MIGRATED_ALLOCATION_OFFENDER_ID, NO_HISTORY_OFFENDER_ID))
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("$.length()")
      .isEqualTo(3)
      .jsonPath("$[0].offenderNo")
      .isEqualTo(MIGRATED_ALLOCATION_OFFENDER_ID)
      .jsonPath("$[0].hasHistory")
      .isEqualTo("true")
      .jsonPath("$[1].offenderNo")
      .isEqualTo(NON_MIGRATED_ALLOCATION_OFFENDER_ID)
      .jsonPath("$[1].hasHistory")
      .isEqualTo("true")
      .jsonPath("$[2].offenderNo")
      .isEqualTo(NO_HISTORY_OFFENDER_ID)
      .jsonPath("$[2].hasHistory")
      .isEqualTo("false")
  }

  @Test
  fun `Allocation history summary validates offender nos provided`() {
    addKeyworkerAllocation()
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)

    webTestClient
      .post()
      .uri("/key-worker/allocation-history/summary")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is5xxServerError
  }

  @Test
  fun `Enable manual allocation accepts multiple capacities`() {
    webTestClient
      .post()
      .uri("/key-worker/enable/MDI/manual?migrate=false&capacity=6,7&frequency=1")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
  }

  @Test
  fun `sar returns 209 if no prn set`() {
    addKeyworkerAllocation()
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)

    webTestClient
      .get()
      .uri("/subject-access-request")
      .headers(setHeaders(roles = listOf("ROLE_SAR_DATA_ACCESS")))
      .exchange()
      .expectStatus()
      .isEqualTo(HttpStatusCode.valueOf(209))
  }

  @Disabled
  @Test
  fun `sar has content`() {
    addKeyworkerAllocation()
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)

    webTestClient
      .get()
      .uri("/subject-access-request?prn=${MIGRATED_ALLOCATION_OFFENDER_ID}")
      .headers(setHeaders(roles = listOf("ROLE_SAR_DATA_ACCESS")))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .consumeWith(System.out::println)
      .jsonPath("$.content")
      .isNotEmpty
  }

  @Test
  fun `sar has no content with date range filter`() {
    addKeyworkerAllocation()
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)

    webTestClient
      .get()
      .uri("/subject-access-request?prn=${MIGRATED_ALLOCATION_OFFENDER_ID}&toDate=1999-01-01")
      .headers(setHeaders(roles = listOf("ROLE_SAR_DATA_ACCESS")))
      .exchange()
      .expectStatus()
      .isNoContent
  }

  @Disabled
  @Test
  fun `sar has content with date range filter`() {
    addKeyworkerAllocation()
    prisonMockServer.stubOffendersAllocationHistory(oFFENDERSHISTORY)

    webTestClient
      .get()
      .uri("/subject-access-request?prn=${MIGRATED_ALLOCATION_OFFENDER_ID}&fromDate=1999-01-01")
      .headers(setHeaders(roles = listOf("ROLE_SAR_DATA_ACCESS")))
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .consumeWith(System.out::println)
      .jsonPath("$.content")
      .isNotEmpty
  }

  @Test
  fun `sar has no content`() {
    webTestClient
      .get()
      .uri("/subject-access-request?prn=A12345")
      .headers(setHeaders(roles = listOf("ROLE_SAR_DATA_ACCESS")))
      .exchange()
      .expectStatus()
      .isNoContent
  }

  fun addKeyworkerAllocation(
    prisonId: String = PRISON_ID,
    offenderId: String = MIGRATED_ALLOCATION_OFFENDER_ID,
  ) {
    setKeyworkerCapacity(prisonId, KEYWORKER_ID_1, 3)

    webTestClient
      .post()
      .uri("/key-worker/allocate")
      .headers(setOmicAdminHeaders())
      .bodyValue(
        mapOf(
          "offenderNo" to offenderId,
          "staffId" to KEYWORKER_ID_1,
          "prisonId" to prisonId,
          "allocationType" to "M",
          "allocationReason" to "MANUAL",
        ),
      ).exchange()
      .expectStatus()
      .is2xxSuccessful
  }
}
