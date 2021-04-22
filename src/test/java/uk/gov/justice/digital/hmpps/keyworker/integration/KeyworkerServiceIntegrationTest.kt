package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KeyworkerServiceIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val KEYWORKER_ID_1 = 1001L
    const val MIGRATED_ALLOCATION_OFFENDER_ID = "UNALLOC1"
    const val NON_MIGRATED_ALLOCATION_OFFENDER_ID = "ALLOCED1"
    const val NO_HISTORY_OFFENDER_ID = "UNALLOC2"
  }

  val OFFENDERS_HISTORY: String = getWiremockResponse(PRISON_ID, "offenders-history")
  val OFFENDERS_AT_LOCATION: String = getWiremockResponse(PRISON_ID, "offenders-at-location")
  val STAFF_LOCATION_ROLE_LIST = getWiremockResponse(PRISON_ID, "staff-location-role-list")

  @BeforeEach
  @Test
  fun beforeEach() {
    migratedFoAutoAllocation(PRISON_ID)
    eliteMockServer.stubOffendersAtLocationForAutoAllocation(PRISON_ID, OFFENDERS_AT_LOCATION)
    eliteMockServer.stubKeyworkerRoles(PRISON_ID, KEYWORKER_ID_1, STAFF_LOCATION_ROLE_LIST)
  }

  @Test
  fun `Allocation history summary reports ok`() {
    addKeyworkerAllocation(PRISON_ID, MIGRATED_ALLOCATION_OFFENDER_ID)
    eliteMockServer.stubOffendersAllocationHistory(OFFENDERS_HISTORY)

    webTestClient.post()
      .uri("/key-worker/allocation-history/summary")
      .bodyValue(listOf(MIGRATED_ALLOCATION_OFFENDER_ID, NON_MIGRATED_ALLOCATION_OFFENDER_ID, NO_HISTORY_OFFENDER_ID))
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.length()").isEqualTo(3)
      .jsonPath("$[0].offenderNo").isEqualTo(MIGRATED_ALLOCATION_OFFENDER_ID)
      .jsonPath("$[0].hasHistory").isEqualTo("true")
      .jsonPath("$[1].offenderNo").isEqualTo(NON_MIGRATED_ALLOCATION_OFFENDER_ID)
      .jsonPath("$[1].hasHistory").isEqualTo("true")
      .jsonPath("$[2].offenderNo").isEqualTo(NO_HISTORY_OFFENDER_ID)
      .jsonPath("$[2].hasHistory").isEqualTo("false")
  }

  @Test
  fun `Allocation history summary validates offender nos provided`() {
    addKeyworkerAllocation(PRISON_ID, MIGRATED_ALLOCATION_OFFENDER_ID)
    eliteMockServer.stubOffendersAllocationHistory(OFFENDERS_HISTORY)

    webTestClient.post()
      .uri("/key-worker/allocation-history/summary")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().is4xxClientError
  }

  @Test
  fun `Enable manual allocation accepts multiple capacities`() {
    webTestClient.post()
      .uri("/key-worker/enable/MDI/manual?migrate=false&capacity=6,7&frequency=1")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  fun addKeyworkerAllocation(prisonId: String, offenderId: String) {

    setKeyworkerCapacity(PRISON_ID, KEYWORKER_ID_1, 3)

    webTestClient.post()
      .uri("/key-worker/allocate")
      .headers(setOmicAdminHeaders())
      .bodyValue(
        mapOf(
          "offenderNo" to MIGRATED_ALLOCATION_OFFENDER_ID,
          "staffId" to KEYWORKER_ID_1,
          "prisonId" to PRISON_ID,
          "allocationType" to "M",
          "allocationReason" to "MANUAL"
        )
      )
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  fun setKeyworkerCapacity(prisonId: String, keyworkerId: Long, capacity: Int) {
    webTestClient.post()
      .uri("/key-worker/$keyworkerId/prison/$prisonId")
      .headers(setOmicAdminHeaders())
      .bodyValue(mapOf("capacity" to capacity, "status" to "ACTIVE"))
      .exchange()
      .expectStatus().is2xxSuccessful
  }
}
