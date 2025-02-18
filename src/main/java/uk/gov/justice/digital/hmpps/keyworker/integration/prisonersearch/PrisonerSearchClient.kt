package uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
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
        it.queryParam("include-restricted-patients", true)
        it.queryParam("size", Int.MAX_VALUE)
        it.build(prisonCode)
      }.retrieve()
      .bodyToMono<Prisoners>()
      .retryRequestOnTransientException()
      .block()!!
}
