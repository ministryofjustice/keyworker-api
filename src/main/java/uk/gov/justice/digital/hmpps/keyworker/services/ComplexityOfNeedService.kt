package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel

@Service
class ComplexityOfNeedService(
  val keyworkerService: KeyworkerService,
  val complexityOfNeedAPI: ComplexityOfNeedAPI,
  @Value("\${womens.estate}") val enabledPrisons: Set<String>,
  val telemetryClient: TelemetryClient
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getComplexOffenders(prisonId: String, offenders: Set<String>): Set<String> {
    if (!enabledPrisons.contains(prisonId)) return offenders

    val complexOffenders = complexityOfNeedAPI
      .getOffendersWithMeasuredComplexityOfNeed(offenders)
      .filter {
        it.level.equals(ComplexityOfNeedLevel.HIGH)
      }.map {
        it.offenderNo
      }.toSet()

    return offenders.filter {
      !complexOffenders.contains(it)
    }.toSet()
  }

  fun onComplexityChange(offenderNo: String, level: ComplexityOfNeedLevel) {
    telemetryClient.trackEvent(
      "complexity-of-need-change",
      mapOf(
        "offenderNo" to offenderNo,
        "level-changed-to" to level.toString()
      ),
      null
    )

    if (level != ComplexityOfNeedLevel.HIGH) return

    log.info("Deallocating an offender based on their HIGH complexity of need")
    keyworkerService.deallocate(offenderNo)
  }
}
