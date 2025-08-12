package uk.gov.justice.digital.hmpps.keyworker.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.migration.Movement
import uk.gov.justice.digital.hmpps.keyworker.migration.PoHistoricAllocation

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
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
      .retrieve()
      .bodyToMono<List<NomisStaffRole>>()
      .retryRequestOnTransientException()
      .block()!!
      .filter { staffId != null || !it.isExpired() }

  fun getKeyworkerForPrison(
    prisonCode: String,
    staffId: Long,
  ): NomisStaffRole? =
    getKeyworkersForPrison(prisonCode, staffId).firstOrNull {
      it.staffId == staffId
    }

  fun getPersonalOfficerHistory(prisonCode: String): List<PoHistoricAllocation> =
    webClient
      .get()
      .uri(PERSONAL_OFFICER_HISTORY_URL, prisonCode)
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .retrieve()
      .bodyToMono<List<PoHistoricAllocation>>()
      .retryRequestOnTransientException()
      .block() ?: emptyList()

  fun getPersonMovements(personIdentifiers: Set<String>): Map<String, List<Movement>> =
    if (personIdentifiers.isEmpty()) {
      emptyMap()
    } else {
      Flux
        .fromIterable(personIdentifiers)
        .buffer(500)
        .flatMap({ ids ->
          webClient
            .post()
            .uri {
              it.path(MOVEMENTS_URL)
              it.queryParam("allBookings", true)
              it.queryParam("latestOnly", false)
              it.build()
            }.bodyValue(ids)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .bodyToFlux<Movement>()
            .retryRequestOnTransientException()
        }, 5)
        .collectList()
        .block()!!
        .groupBy { it.offenderNo }
    }

  companion object {
    const val GET_KEYWORKER_INFO = "/staff/roles/{agencyId}/role/KW"
    const val STAFF_BY_IDS_URL = "/staff"
    const val PERSONAL_OFFICER_HISTORY_URL = "/personal-officer/{agencyId}/allocation-history"
    const val MOVEMENTS_URL = "/movements/offenders"
  }
}
