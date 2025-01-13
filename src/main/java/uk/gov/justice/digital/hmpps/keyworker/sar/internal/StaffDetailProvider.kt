package uk.gov.justice.digital.hmpps.keyworker.sar.internal

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import java.time.Duration

@Service
class StaffDetailProvider(
  @Qualifier("prisonApiWebClient") private val prisonApi: WebClient,
) {
  fun findStaffSummariesFromIds(ids: Set<Long>): List<StaffSummary> {
    return Flux.fromIterable(ids).flatMap({ id ->
      prisonApi.get().uri(STAFF_BY_ID_URL, id).retrieve()
        .bodyToMono<StaffSummary>()
        .retryOnTransientException()
    }, 10)
      .collectList().block() ?: emptyList()
  }

  companion object {
    const val STAFF_BY_ID_URL = "/staff/{staffId}"
  }
}

fun <T> Mono<T>.retryOnTransientException(): Mono<T> =
  retryWhen(
    Retry.backoff(3, Duration.ofMillis(200))
      .filter {
        it is WebClientRequestException || (it is WebClientResponseException && it.statusCode.is5xxServerError)
      }.onRetryExhaustedThrow { _, signal ->
        signal.failure()
      },
  )
