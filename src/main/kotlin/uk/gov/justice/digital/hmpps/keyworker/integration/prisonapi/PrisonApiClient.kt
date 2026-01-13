package uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
  fun getStaffSummariesFromIds(ids: Set<Long>): List<StaffSummary> = findStaffSummariesFromIds(ids).block()!!

  fun findStaffSummariesFromIds(ids: Set<Long>): Mono<List<StaffSummary>> =
    if (ids.isEmpty()) {
      Mono.just(emptyList())
    } else {
      webClient
        .post()
        .uri(STAFF_BY_IDS_URL)
        .bodyValue(ids)
        .retrieve()
        .bodyToMono<List<StaffSummary>>()
        .retryRequestOnTransientException()
    }

  fun getKeyworkersForPrison(
    prisonCode: String,
    staffId: Long? = null,
  ): List<NomisStaffRole> =
    webClient
      .get()
      .uri {
        it.path(GET_KEYWORKER_INFO)
        staffId?.also { staffId ->
          it.queryParam("staffId", staffId)
          it.queryParam("activeOnly", false)
        }
        it.build(prisonCode)
      }.header("Page-Offset", "0")
      .header("Page-Limit", "3000")
      .header("Sort-Fields", "staffId")
      .header("Sort-Order", "ASC")
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .exchangeToMono {
        when (it.statusCode()) {
          HttpStatus.NOT_FOUND -> Mono.empty()
          HttpStatus.OK -> it.bodyToMono<List<NomisStaffRole>>()
          else -> it.createError()
        }
      }.retryRequestOnTransientException()
      .block()
      ?.filter { staffId != null || !it.isExpired() }
      ?: emptyList()

  fun getKeyworkerForPrison(
    prisonCode: String,
    staffId: Long,
  ): NomisStaffRole? =
    getKeyworkersForPrison(prisonCode, staffId).firstOrNull {
      it.staffId == staffId
    }

  private fun getStaffEmail(staffId: Long): Mono<Pair<Long, Set<String>>> =
    webClient
      .get()
      .uri(STAFF_EMAIL_URL, staffId)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.NOT_FOUND, HttpStatus.NO_CONTENT -> Mono.just(emptySet())
          HttpStatus.OK -> res.bodyToMono<Set<String>>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .map { staffId to it }

  fun getStaffEmails(staffIds: Set<Long>): Mono<List<Pair<Long, Set<String>>>> {
    if (staffIds.isEmpty()) {
      return Mono.just(emptyList())
    }
    return Flux
      .fromIterable(staffIds)
      .flatMap({ getStaffEmail(it) }, 10)
      .collectList()
  }

  companion object {
    const val GET_KEYWORKER_INFO = "/staff/roles/{agencyId}/role/KW"
    const val STAFF_BY_IDS_URL = "/staff"
    const val STAFF_EMAIL_URL = "/staff/{staffId}/emails"
  }
}
