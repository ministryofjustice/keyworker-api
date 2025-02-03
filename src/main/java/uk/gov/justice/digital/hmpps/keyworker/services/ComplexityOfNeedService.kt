package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${complexity_of_need_uri}')")
class ComplexityOfNeedService(
  private val complexityOfNeedGateway: ComplexityOfNeedGateway,
  @Value("\${prisons.with.offenders.that.have.complex.needs}") val prisonsWithOffenderComplexityNeeds: Set<String>,
  val telemetryClient: TelemetryClient,
) : ComplexityOfNeed {
  override fun removeOffendersWithHighComplexityOfNeed(
    prisonId: String,
    offenders: Set<String>,
  ): Set<String> {
    if (!prisonsWithOffenderComplexityNeeds.contains(prisonId)) return offenders

    val complexOffenders =
      complexityOfNeedGateway
        .getOffendersWithMeasuredComplexityOfNeed(offenders)
        .filter {
          it.level.equals(ComplexityOfNeedLevel.HIGH)
        }.map {
          it.offenderNo
        }.toSet()

    return offenders
      .filter {
        !complexOffenders.contains(it)
      }.toSet()
  }

  override fun isComplexPrison(prisonId: String): Boolean = prisonsWithOffenderComplexityNeeds.contains(prisonId)
}
