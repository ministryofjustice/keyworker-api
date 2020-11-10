package uk.gov.justice.digital.hmpps.keyworker.controllers

import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@WebFluxTest(KeyworkerStatsController::class)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@WithMockUser
class KeyworkerStatsControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    lateinit var keyworkerStatsService: KeyworkerStatsService
    @MockBean
    lateinit var prisonSupportedService: PrisonSupportedService

    @Test
    fun `get stats for staff and missing dates`() {
        val staffId = "123"
        val prisonId = "456"

        whenever(
            keyworkerStatsService.getStatsForStaff(123L, "456", null, null)
        ).thenReturn(KeyworkerStatsDto())

        webTestClient.get()
            .uri("/key-worker-stats/$staffId/prison/$prisonId") // ?fromDate=$fromDate&toDate=$toDate
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getStatsForStaff(123L, "456", null, null)
    }

    @Test
    fun `get stats for staff and missing fromDate`() {
        val staffId = "123"
        val prisonId = "456"
        val toDate = LocalDate.now()

        whenever(
            keyworkerStatsService.getStatsForStaff(123L, "456", null, toDate)
        ).thenReturn(KeyworkerStatsDto())

        webTestClient.get()
            .uri("/key-worker-stats/$staffId/prison/$prisonId?toDate=${toDate.format(DateTimeFormatter.ISO_DATE)}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getStatsForStaff(123L, "456", null, toDate)
    }

    @Test
    fun `get stats for staff and missing toDate`() {
        val staffId = "123"
        val prisonId = "456"
        val fromDate = LocalDate.now()

        whenever(
            keyworkerStatsService.getStatsForStaff(123L, "456", fromDate, null)
        ).thenReturn(KeyworkerStatsDto())

        webTestClient.get()
            .uri("/key-worker-stats/$staffId/prison/$prisonId?fromDate=${fromDate.format(DateTimeFormatter.ISO_DATE)}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getStatsForStaff(123L, "456", fromDate, null)
    }

    @Test
    fun `get stats happy path`() {

        val fromDate = LocalDate.now()
        val toDate = LocalDate.now()

        whenever(
            keyworkerStatsService.getPrisonStats(listOf("MDI"), fromDate, toDate)
        ).thenReturn(KeyworkerStatSummary())

        webTestClient.get()
            .uri("/key-worker-stats?prisonId=MDI&fromDate=${fromDate.format(DateTimeFormatter.ISO_DATE)}&toDate=${toDate.format(DateTimeFormatter.ISO_DATE)}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getPrisonStats(listOf("MDI"), fromDate, toDate)
    }

    @Test
    fun `get stats when missing dates and prisonIds`() {

        whenever(
            prisonSupportedService.migratedPrisons
        ).thenReturn(emptyList())

        whenever(
            keyworkerStatsService.getPrisonStats(emptyList(), null, null)
        ).thenReturn(KeyworkerStatSummary())

        webTestClient.get()
            .uri("/key-worker-stats")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getPrisonStats(emptyList(), null, null)
    }

    @Test
    fun `get stats when missing dates and prisonIds and one migrated prison`() {

        whenever(
            prisonSupportedService.migratedPrisons
        ).thenReturn(listOf(Prison.builder().prisonId("MDI").build()))

        whenever(
            keyworkerStatsService.getPrisonStats(listOf("MDI"), null, null)
        ).thenReturn(KeyworkerStatSummary())

        webTestClient.get()
            .uri("/key-worker-stats")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getPrisonStats(listOf("MDI"), null, null)
    }

    @Test
    fun `get stats when missing dates`() {

        whenever(
            keyworkerStatsService.getPrisonStats(listOf("MDI"), null, null)
        ).thenReturn(KeyworkerStatSummary())

        webTestClient.get()
            .uri("/key-worker-stats?prisonId=MDI")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getPrisonStats(listOf("MDI"), null, null)
    }

    @Test
    fun `get stats when missing formDate`() {

        val toDate = LocalDate.now()

        whenever(
            keyworkerStatsService.getPrisonStats(listOf("MDI"), null, toDate)
        ).thenReturn(KeyworkerStatSummary())

        webTestClient.get()
            .uri("/key-worker-stats?prisonId=MDI&toDate=${toDate.format(DateTimeFormatter.ISO_DATE)}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getPrisonStats(listOf("MDI"), null, toDate)
    }

    @Test
    fun `get stats when missing toDate`() {

        val fromDate = LocalDate.now()

        whenever(
            keyworkerStatsService.getPrisonStats(listOf("MDI"), fromDate, null)
        ).thenReturn(KeyworkerStatSummary())

        webTestClient.get()
            .uri("/key-worker-stats?prisonId=MDI&fromDate=${fromDate.format(DateTimeFormatter.ISO_DATE)}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerStatsService).getPrisonStats(listOf("MDI"), fromDate, null)
    }
}
