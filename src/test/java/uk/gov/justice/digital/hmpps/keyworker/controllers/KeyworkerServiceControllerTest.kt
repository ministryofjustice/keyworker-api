package uk.gov.justice.digital.hmpps.keyworker.controllers

import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.nhaarman.mockitokotlin2.any
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationsFilterDto
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.Page
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.UserRolesMigrationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerAutoAllocationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerMigrationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import java.time.LocalDate
import java.util.Optional

@WebFluxTest(KeyworkerServiceController::class)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
@WithMockUser
class KeyworkerServiceControllerTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var keyworkerService: KeyworkerService

    @MockBean
    private lateinit var keyworkerMigrationService: KeyworkerMigrationService

    @MockBean
    private lateinit var roleMigrationService: UserRolesMigrationService

    @MockBean
    private lateinit var keyworkerAutoAllocationService: KeyworkerAutoAllocationService

    @MockBean
    private lateinit var prisonSupportedService: PrisonSupportedService

    @ParameterizedTest
    @ValueSource(strings = arrayOf("/key-worker/LEI", "/key-worker")) // Includes deprecated version of the API
    fun `offender keyworker found should return ok`(pathPrefix: String) {

        whenever(keyworkerService.getCurrentKeyworkerForPrisoner("A1234AA"))
            .thenReturn(Optional.of(BasicKeyworkerDto.builder().build()))

        webTestClient.get()
            .uri("$pathPrefix/offender/A1234AA")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        verify(keyworkerService).getCurrentKeyworkerForPrisoner("A1234AA")
    }

    @ParameterizedTest
    @ValueSource(strings = arrayOf("/key-worker/LEI", "/key-worker")) // Includes deprecated version of the API
    fun `offender keyworker not found should return not found`(pathPrefix: String) {

        whenever(keyworkerService.getCurrentKeyworkerForPrisoner("A1234AA"))
            .thenThrow(WebClientResponseException.create(404, "Not Found", HttpHeaders.EMPTY, byteArrayOf(), null, null))

        webTestClient.get()
            .uri("$pathPrefix/offender/A1234AA")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isNotFound

        verify(keyworkerService).getCurrentKeyworkerForPrisoner("A1234AA")
    }

    @Test
    fun `correct default values are passed to getAllocations`() {
        val prisonId = "123"

        val page = Page<KeyworkerAllocationDetailsDto>(emptyList(), defaultHttpHeaders())
        whenever(keyworkerService.getAllocations(any(), any())).thenReturn(page)

        webTestClient.get()
            .uri("/key-worker/$prisonId/allocations")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk

        val expectedFilterDto = AllocationsFilterDto
            .builder()
            .prisonId(prisonId)
            .fromDate(Optional.empty())
            .toDate(LocalDate.now())
            .allocationType(Optional.empty())
            .build()

        val expectedPagingDto = PagingAndSortingDto
            .builder()
            .pageOffset(0)
            .pageLimit(10)
            .sortOrder(SortOrder.ASC)
            .sortFields("")
            .build()

        verify(keyworkerService).getAllocations(expectedFilterDto, expectedPagingDto)
    }

    private fun defaultHttpHeaders(): HttpHeaders {
        val httpHeaders = HttpHeaders()
        httpHeaders.set(Page.HEADER_TOTAL_RECORDS, "0")
        httpHeaders.set(Page.HEADER_PAGE_OFFSET, "0")
        httpHeaders.set(Page.HEADER_PAGE_LIMIT, "0")
        return httpHeaders
    }
}
