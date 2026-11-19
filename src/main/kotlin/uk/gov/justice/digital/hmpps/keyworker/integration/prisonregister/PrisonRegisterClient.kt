package uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.services.Prison

@Service
class PrisonRegisterClient(
  @Qualifier("prisonRegisterApiWebClient") private val webClient: WebClient,
) {
  fun getAllPrisons(): List<Prison> =
    webClient
      .get()
      .uri("/prisons/names")
      .retrieve()
      .bodyToMono<List<Prison>>()
      .retryRequestOnTransientException()
      .block()!!

  fun getPrison(code: String): Prison =
    webClient
      .get()
      .uri("/prisons/id/{code}", code)
      .retrieve()
      .bodyToMono<Prison>()
      .retryRequestOnTransientException()
      .block()!!
}

fun Prison.asCodedDescription() = CodedDescription(prisonId, prisonName)
