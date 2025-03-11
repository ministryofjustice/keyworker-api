package uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoners
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException

@Component
class PrisonerSearchClient(
  @Qualifier("prisonerSearchWebClient") private val webClient: WebClient,
) {
  fun findAllPrisoners(prisonCode: String): Prisoners =
    webClient
      .get()
      .uri {
        it.path("/prisoner-search/prison/{prisonCode}")
        it.queryParam("size", Int.MAX_VALUE)
        it.build(prisonCode)
      }.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .retrieve()
      .bodyToMono<Prisoners>()
      .retryRequestOnTransientException()
      .block()!!

  fun findPrisonerDetails(prisonNumbers: Set<String>): List<Prisoner> =
    webClient
      .post()
      .uri("/prisoner-search/prisoner-numbers")
      .bodyValue(PrisonNumbers(prisonNumbers))
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .retrieve()
      .bodyToMono<List<Prisoner>>()
      .retryRequestOnTransientException()
      .block()!!
}

data class PrisonNumbers(
  val prisonNumbers: Set<String>,
)
