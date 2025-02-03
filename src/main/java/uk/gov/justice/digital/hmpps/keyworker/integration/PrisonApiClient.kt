package uk.gov.justice.digital.hmpps.keyworker.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
  fun staffRoleCheck(
    staffId: String,
    prisonCode: String,
    role: String,
  ): Boolean? =
    webClient
      .get()
      .uri("/staff/{staffId}/{prisonCode}/roles/{role}", staffId, prisonCode, role)
      .exchangeToMono { res ->
        println("*** -> " + res.request().uri)
        when (res.statusCode()) {
          HttpStatus.NOT_FOUND -> Mono.empty()
          HttpStatus.OK -> res.bodyToMono<Boolean>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()
}
