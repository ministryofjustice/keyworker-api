package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class AutoAllocationIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val KEYWORKER_ID_1 = 1001L
    const val KEYWORKER_ID_2 = 1002L
  }

  val oFFENDERSATLOCATION: String = getWiremockResponse(PRISON_ID, "offenders-at-location")
  val kEYWORKERLIST = getWiremockResponse(PRISON_ID, "keyworker-list")
  val cOMPLEXOFFENDERUNALLOC1 = getWiremockResponse("UNALLOC1-complexity-high")

  @BeforeEach
  @Test
  fun beforeEach() {
    migratedFoAutoAllocation(PRISON_ID)
    prisonMockServer.stubOffendersAtLocationForAutoAllocation(oFFENDERSATLOCATION)
    prisonMockServer.stubAvailableKeyworkersForAutoAllocation(PRISON_ID, kEYWORKERLIST)

    oAuthMockServer.stubGrantToken()
  }

  @Test
  fun `Allocation service reports ok`() {
    setKeyworkerCapacity(PRISON_ID, KEYWORKER_ID_1, 3)
    setKeyworkerCapacity(PRISON_ID, KEYWORKER_ID_2, 1)

    webTestClient
      .post()
      .uri("/key-worker/$PRISON_ID/allocate/start")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .equals(10)

    webTestClient
      .post()
      .uri("/key-worker/$PRISON_ID/allocate/confirm")
      .headers(setHeaders(roles = listOf("ROLE_OMIC_ADMIN")))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .equals(10)

    webTestClient
      .get()
      .uri("/key-worker/$PRISON_ID/allocations")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("$.length()")
      .isEqualTo(11)
      .jsonPath("$[0].offenderNo")
      .isEqualTo("ALLOCED1") // KW is already 1001
      .jsonPath("$[0].allocationType")
      .isEqualTo("M")
      .jsonPath("$[1].offenderNo")
      .isEqualTo("UNALLOC1")
      .jsonPath("$[1].staffId")
      .isEqualTo(1002) // 1001 not chosen as its no allocated > 0
      .jsonPath("$[1].allocationType")
      .isEqualTo("A")
      .jsonPath("$[1].assigned")
      .value<String> {
        val dateTime = LocalDateTime.parse(it)
        assertThat(dateTime).isCloseTo(dateTime, within(1, ChronoUnit.HOURS))
      }.jsonPath("$[2].offenderNo")
      .isEqualTo("UNALLOC2")
      .jsonPath("$[2].staffId")
      .isEqualTo(1003)
      .jsonPath("$[3].offenderNo")
      .isEqualTo("UNALLOC3")
      .jsonPath("$[3].staffId")
      .isEqualTo(1001) // Now chosen in staffId numerical order
      // 1001 is not bypassed due to an old allocation because this was NOT auto!
      .jsonPath("$[4].offenderNo")
      .isEqualTo("UNALLOC4")
      .jsonPath("$[4].staffId")
      .isEqualTo(1003) // 1002 is now full
      .jsonPath("$[5].offenderNo")
      .isEqualTo("UNALLOC5")
      .jsonPath("$[5].staffId")
      .isEqualTo(1001)
      .jsonPath("$[6].offenderNo")
      .isEqualTo("UNALLOC6")
      .jsonPath("$[6].staffId")
      .isEqualTo(1003)
      .jsonPath("$[7].offenderNo")
      .isEqualTo("UNALLOC7")
      .jsonPath("$[7].staffId")
      .isEqualTo(1001)
      .jsonPath("$[8].offenderNo")
      .isEqualTo("UNALLOC8")
      .jsonPath("$[8].staffId")
      .isEqualTo(1003)
      .jsonPath("$[9].offenderNo")
      .isEqualTo("UNALLOC9")
      .jsonPath("$[9].staffId")
      .isEqualTo(1003) // 1001 is now full
      .jsonPath("$[10].offenderNo")
      .isEqualTo("EXPIRED1") // KW set to previous: 1002, despite being full
      .jsonPath("$[10].staffId")
      .isEqualTo(1002)
      .jsonPath("$[10].allocationType")
      .isEqualTo("A")
      .jsonPath("$[10].assigned")
      .value<String> {
        val dateTime = LocalDateTime.parse(it)
        assertThat(dateTime).isCloseTo(dateTime, within(1, ChronoUnit.HOURS))
      }
  }

  @Test
  fun `should return a list of unallocated offenders for non-complex prisoners`() {
    webTestClient
      .get()
      .uri("/key-worker/$PRISON_ID/offenders/unallocated")
      .headers(setHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .json("keyworker-service-controller-unallocated.json".readFile())
  }

  @Test
  fun `should return a list of unallocated offenders ignoring offenders with high complexity of need`() {
    val prisonSupported = prisonSupportedRepository.findByPrisonCode("AGI").orElseThrow()
    prisonSupported.isEnabled = true
    prisonSupportedRepository.save(prisonSupported)

    complexityOfNeedMockServer.stubComplexOffenders(cOMPLEXOFFENDERUNALLOC1)

    webTestClient
      .get()
      .uri("/key-worker/${prisonSupported.prisonCode}/offenders/unallocated")
      .headers(setHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("\$[?(@.offenderNo == 'UNALLOC1')]")
      .doesNotExist()
  }

  @Test
  fun `should respond with 2xx status`() {
    webTestClient
      .post()
      .uri("/key-worker/enable/$PRISON_ID/auto-allocate?migrate=true&capacity=6,9&frequency=2")
      .headers(setHeaders(roles = listOf("ROLE_KW_MIGRATION")))
      .exchange()
      .expectStatus()
      .is2xxSuccessful
  }
}
