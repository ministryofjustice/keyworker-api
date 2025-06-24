package uk.gov.justice.digital.hmpps.keyworker.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonalerts.AlertReferenceData
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonalerts.AlertsApiClient
import java.time.LocalDateTime

@Service
class AlertDescriptionService(
  private val alertsApiClient: AlertsApiClient,
) {
  var referenceDataCache: List<AlertReferenceData> = listOf()
  var cachedAt: LocalDateTime = LocalDateTime.now()

  fun getDescription(alertCode: String): String {
    if (cachedAt.isBefore(LocalDateTime.now().minusMinutes(10)) || !referenceDataCache.any { it.code == alertCode }) {
      referenceDataCache = alertsApiClient.getReferenceData()
    }

    referenceDataCache.find { it.code == alertCode }.let {
      if (it == null) {
        log.warn("Cannot find description for alert code '{}', fallback to display code as description", alertCode)
        return alertCode
      }
      return it.description
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
