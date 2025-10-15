package uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonsByIdsRequest

@Service
class PrisonRegisterClient(
  @Qualifier("prisonRegisterApiWebClient") private val webClient: WebClient,
) {
  fun findPrisons(ids: Set<String>): List<Prison> =
    if (ids.isEmpty()) {
      emptyList()
    } else {
      webClient
        .post()
        .uri("/prisons/prisonsByIds")
        .bodyValue(PrisonsByIdsRequest(ids))
        .retrieve()
        .bodyToMono<List<Prison>>()
        .retryRequestOnTransientException()
        .block()!!
    }

  fun findPrison(code: String): Prison? = findPrisons(setOf(code)).firstOrNull()
}

fun Prison.asCodedDescription() = CodedDescription(prisonId, prisonName)
