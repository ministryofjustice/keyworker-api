package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import java.time.Duration

@Service
class StaffDetailProvider(
  @Qualifier("prisonApiWebClient") private val prisonApi: WebClient,
) {
  fun findStaffSummariesFromIds(ids: Set<Long>): List<StaffSummary> =
    if (ids.isEmpty()) {
      emptyList()
    } else {
      prisonApi
        .post()
        .uri(STAFF_BY_IDS_URL)
        .bodyValue(ids)
        .retrieve()
        .bodyToMono<List<StaffSummary>>()
        .retryOnTransientException()
        .block()!!
    }

  companion object {
    const val STAFF_BY_IDS_URL = "/staff"
  }
}

fun <T> Mono<T>.retryOnTransientException(): Mono<T> =
  retryWhen(
    Retry
      .backoff(3, Duration.ofMillis(200))
      .filter {
        it is WebClientRequestException || (it is WebClientResponseException && it.statusCode.is5xxServerError)
      }.onRetryExhaustedThrow { _, signal ->
        signal.failure()
      },
  )
