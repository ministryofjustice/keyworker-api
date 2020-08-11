package uk.gov.justice.digital.hmpps.keyworker.controllers

import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
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
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.UserRolesMigrationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerAutoAllocationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerMigrationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import java.util.*

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
        .thenThrow(WebClientResponseException.create(404, "Not Found", HttpHeaders.EMPTY, null, null, null))

    webTestClient.get()
        .uri("$pathPrefix/offender/A1234AA")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isNotFound

    verify(keyworkerService).getCurrentKeyworkerForPrisoner("A1234AA")
  }
}
