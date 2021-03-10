package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel

@Service
class ComplexityOfNeedService(
  val keyworkerService: KeyworkerService,
  val telemetryClient: TelemetryClient
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun onComplexityChange(offenderNo: String, level: ComplexityOfNeedLevel) {
    telemetryClient.trackEvent(
      "Complexity-of-need-change", mapOf(
        "offenderNo" to offenderNo,
        "level-changed-to" to level.toString()
      ), null
    )

    if (level != ComplexityOfNeedLevel.HIGH) return

    log.info("Deallocating an offender based on their HIGH complexity of need")
    keyworkerService.deallocate(offenderNo)
  }
}
