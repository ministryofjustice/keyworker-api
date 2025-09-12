package uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
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
        it.queryParam("size", PRISONER_SEARCH_LIMIT)
        it.queryParam("responseFields", Prisoner.fields())
        it.build(prisonCode)
      }.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .retrieve()
      .bodyToMono<Prisoners>()
      .retryRequestOnTransientException()
      .block()!!

  fun findPrisonerDetails(prisonNumbers: Set<String>): List<Prisoner> =
    if (prisonNumbers.isEmpty()) {
      emptyList()
    } else {
      Flux
        .fromIterable(prisonNumbers)
        .buffer(1000)
        .flatMap {
          webClient
            .post()
            .uri {
              it.path("/prisoner-search/prisoner-numbers")
              it.queryParam("responseFields", Prisoner.fields())
              it.build()
            }.bodyValue(PrisonerNumbers(prisonNumbers))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .bodyToFlux<Prisoner>()
            .retryRequestOnTransientException()
        }.collectList()
        .block()!!
    }

  fun findFilteredPrisoners(
    prisonCode: String,
    request: PersonSearchRequest,
  ): Prisoners =
    webClient
      .get()
      .uri { ub ->
        ub.path("/prison/{prisonCode}/prisoners")
        with(request) {
          query?.also { ub.queryParam("term", it) }
          cellLocationPrefix?.also { ub.queryParam("cellLocationPrefix", it) }
          ub.queryParam("page", 0)
          ub.queryParam("size", PRISONER_SEARCH_LIMIT)
          ub.queryParam("responseFields", Prisoner.fields())
        }
        ub.build(prisonCode)
      }.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .retrieve()
      .bodyToMono<Prisoners>()
      .retryRequestOnTransientException()
      .block()!!

  companion object {
    const val PRISONER_SEARCH_LIMIT = 10000
  }
}

data class PrisonerNumbers(
  val prisonerNumbers: Set<String>,
)
