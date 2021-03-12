package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel

interface ComplexityOfNeed {
  fun removeOffendersWithHighComplexityOfNeed(prisonId: String, offenders: Set<String>): Set<String>
  fun onComplexityChange(offenderNo: String, level: ComplexityOfNeedLevel)
}

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isBlank('\${complexity_of_need_uri}')")
class StubComplexityOfNeed : ComplexityOfNeed {
  override fun removeOffendersWithHighComplexityOfNeed(prisonId: String, offenders: Set<String>): Set<String> =
    offenders

  override fun onComplexityChange(offenderNo: String, level: ComplexityOfNeedLevel) {}
}

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${complexity_of_need_uri}')")
class ComplexityOfNeedService(
  val keyworkerService: KeyworkerService,
  val complexityOfNeedGateway: ComplexityOfNeedGateway,
  @Value("\${prisons.with.offenders.that.have.complex.needs}") val prisonsWithOffenderComplexityNeeds: Set<String>,
  val telemetryClient: TelemetryClient
) : ComplexityOfNeed {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun removeOffendersWithHighComplexityOfNeed(prisonId: String, offenders: Set<String>): Set<String> {
    if (!prisonsWithOffenderComplexityNeeds.contains(prisonId)) return offenders

    val complexOffenders = complexityOfNeedGateway
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

  override fun onComplexityChange(offenderNo: String, level: ComplexityOfNeedLevel) {
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
