package uk.gov.justice.digital.hmpps.keyworker.services.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class PrisonApiHealth(
  @Qualifier("prisonApiHealthWebClient") webClient: WebClient,
) : WebClientPingHealthIndicator(webClient)

@Component
class ComplexityOfNeedApiHealth(
  @Qualifier("complexityOfNeedHealthWebClient") webClient: WebClient,
) : WebClientPingHealthIndicator(webClient)

open class WebClientPingHealthIndicator(
  private val webClient: WebClient,
) : ReactiveHealthIndicator {
  override fun health(): Mono<Health> =
    webClient
      .get()
      .uri("/health/ping")
      .retrieve()
      .toBodilessEntity()
      .map { response ->
        Health
          .up()
          .withDetail("HttpStatus", response.statusCode.toString().substringAfterLast(" "))
          .build()
      }.onErrorResume { Mono.just(Health.down(it).build()) }
}
