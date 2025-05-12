package uk.gov.justice.digital.hmpps.keyworker.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary

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
      .uri(VERIFY_STAFF_ROLE_URL, staffId, prisonCode, role)
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.NOT_FOUND -> Mono.empty()
          HttpStatus.OK -> res.bodyToMono<Boolean>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()

  fun findStaffSummariesFromIds(ids: Set<Long>): List<StaffSummary> =
    if (ids.isEmpty()) {
      emptyList()
    } else {
      webClient
        .post()
        .uri(STAFF_BY_IDS_URL)
        .bodyValue(ids)
        .retrieve()
        .bodyToMono<List<StaffSummary>>()
        .retryRequestOnTransientException()
        .block()!!
    }

  companion object {
    const val VERIFY_STAFF_ROLE_URL = "/staff/{staffId}/{prisonCode}/roles/{role}"
    const val STAFF_BY_IDS_URL = "/staff"
  }
}
