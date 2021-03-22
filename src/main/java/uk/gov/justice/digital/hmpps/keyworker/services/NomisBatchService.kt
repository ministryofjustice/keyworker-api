package uk.gov.justice.digital.hmpps.keyworker.services

import com.google.common.collect.ImmutableMap
import com.microsoft.applicationinsights.TelemetryClient
import lombok.extern.slf4j.Slf4j
import org.springframework.retry.RetryCallback
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.QueueAdminService.Companion.log
import org.springframework.retry.policy.SimpleRetryPolicy

import org.springframework.retry.support.RetryTemplate
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate

@Service
@Slf4j
class NomisBatchService(private val nomisService: NomisService,
                        private val telemetryClient: TelemetryClient,
                        private val enableNomisRetryTemplate: RetryTemplate) {

  fun enableNomis() {
    try {
      val prisonsWithId  = nomisService.allPrisons
      log.info("There are %d prisons", prisonsWithId.size)
      for (prison in prisonsWithId.stream()) {
        enableNomisForPrison(prison.prisonId)
      }
    } catch (e: Exception ) {
      log.error("Error occurred retrieving prisons", e)
    }
  }

  private fun enableNomisForPrison(prisonId: String) {
    try {
      log.info("Starting: Enabling API access for all active users in prison %s", prisonId)
      val caseload = enableNomisForCaseloadWithRetry(prisonId)
      if (caseload.numUsersEnabled > 0) {
        log.info("Enabled %d new users for API access", caseload.numUsersEnabled)
      }
      val infoMap = ImmutableMap.of(
        "prisonId",
        caseload.caseload,
        "numUsersEnabled",
        caseload.numUsersEnabled.toString()
      )
      telemetryClient.trackEvent("ApiUsersEnabled", infoMap, null)
    } catch (e: Exception ) {
      log.error("Error occurred enabling new nomis", e)
    }
  }

  private fun enableNomisForCaseloadWithRetry(prisonId: String): CaseloadUpdate {
    return enableNomisRetryTemplate.execute(RetryCallback<CaseloadUpdate, RuntimeException> {
      nomisService.enableNewNomisForCaseload(prisonId)
    })
  }

}