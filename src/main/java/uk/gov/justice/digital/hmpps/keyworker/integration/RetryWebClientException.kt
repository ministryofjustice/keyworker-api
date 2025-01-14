package uk.gov.justice.digital.hmpps.keyworker.integration

import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

fun <T> Mono<T>.retryRequestOnTransientException(): Mono<T> =
  retryWhen(
    Retry.backoff(3, Duration.ofMillis(250))
      .filter {
        it is WebClientRequestException || (it is WebClientResponseException && it.statusCode.is5xxServerError)
      }.onRetryExhaustedThrow { _, signal ->
        signal.failure()
      },
  )
