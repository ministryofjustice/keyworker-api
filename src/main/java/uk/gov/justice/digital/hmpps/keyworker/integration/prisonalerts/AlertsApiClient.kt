package uk.gov.justice.digital.hmpps.keyworker.integration.prisonalerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException

data class AlertReferenceData(
  val alertTypeCode: String,
  val code: String,
  val description: String,
)

@Component
class AlertsApiClient(
  @Qualifier("alertsWebClient") private val webClient: WebClient,
) {
  fun getReferenceData(): List<AlertReferenceData> =
    webClient
      .get()
      .uri(ALERT_CODE_REFERENCE_DATA_URL)
      .retrieve()
      .bodyToMono<List<AlertReferenceData>>()
      .retryRequestOnTransientException()
      .block()!!

  companion object {
    const val ALERT_CODE_REFERENCE_DATA_URL = "/alert-codes"
  }
}
